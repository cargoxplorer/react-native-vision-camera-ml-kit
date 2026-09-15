//
//  TextRecognitionPlugin.swift
//  react-native-vision-camera-ml-kit
//

import Foundation
import VisionCamera
import MLKitVision
import MLKitCommon
import MLKitTextRecognition
import MLKitTextRecognitionChinese
import MLKitTextRecognitionDevanagari
import MLKitTextRecognitionJapanese
import MLKitTextRecognitionKorean

@objc(TextRecognitionPlugin)
public class TextRecognitionPlugin: FrameProcessorPlugin {

    private var textRecognizer: TextRecognizer!
    private var textLayout: TextLayout = .horizontal
    private var async = false
    private lazy var stackedReader = StackedTextReader()
    private let stackedQueue = DispatchQueue(label: "stacked-text", qos: .userInitiated)
    private let processingQueue = DispatchQueue(label: "text-recognition", qos: .userInitiated)
    private let luma = ImageUtils.LumaPlane()
    private let lock = NSLock()
    private var processing = false
    private var pendingResult: [String: Any]?

    private struct Captured {
        let image: UIImage
        let orientation: UIImage.Orientation
        let degrees: Int
        let stacked: Bool
        let startTime: Date
    }

    public override init(proxy: VisionCameraProxyHolder, options: [AnyHashable: Any]! = [:]) {
        super.init(proxy: proxy, options: options)

        let language = (options["language"] as? String ?? "latin").lowercased()
        Logger.info("Initializing text recognition with language: \(language)")

        switch language {
        case "chinese":
            textRecognizer = TextRecognizer.textRecognizer(options: ChineseTextRecognizerOptions())
        case "devanagari":
            textRecognizer = TextRecognizer.textRecognizer(options: DevanagariTextRecognizerOptions())
        case "japanese":
            textRecognizer = TextRecognizer.textRecognizer(options: JapaneseTextRecognizerOptions())
        case "korean":
            textRecognizer = TextRecognizer.textRecognizer(options: KoreanTextRecognizerOptions())
        case "latin", "default":
            textRecognizer = TextRecognizer.textRecognizer(options: TextRecognizerOptions())
        default:
            Logger.warn("Unknown language '\(language)', defaulting to Latin")
            textRecognizer = TextRecognizer.textRecognizer(options: TextRecognizerOptions())
        }

        textLayout = TextLayout.from(options["textLayout"])
        async = options["async"] as? Bool ?? false
        Logger.info("Text recognition initialized successfully (textLayout: \(textLayout.rawValue), async: \(async))")
    }

    deinit {
        Logger.debug("TextRecognitionPlugin deallocating - ML Kit recognizer resources will be freed")
    }

    public override func callback(_ frame: Frame, withArguments arguments: [AnyHashable: Any]?) -> Any? {
        let startTime = Date()

        if !async {
            guard let captured = capture(frame, startTime: startTime) else { return nil }
            return process(captured)
        }

        lock.lock()
        if processing {
            lock.unlock()
            return nil
        }
        let finished = pendingResult
        pendingResult = nil
        lock.unlock()

        guard let captured = capture(frame, startTime: startTime) else { return finished }
        lock.lock()
        processing = true
        lock.unlock()
        processingQueue.async { [self] in
            let result = process(captured)
            lock.lock()
            pendingResult = result
            processing = false
            lock.unlock()
        }
        return finished
    }

    private func capture(_ frame: Frame, startTime: Date) -> Captured? {
        let orientation = frame.orientation

        guard let clonedImage = ImageUtils.imageFromSampleBuffer(frame.buffer) else {
            Logger.error("Failed to create vision image from sample buffer")
            return nil
        }
        let imageOrientation = getOrientation(orientation: orientation)

        Logger.debug("Processing frame: \(frame.width)x\(frame.height), orientation: \(orientation.rawValue)")

        let degrees = TextRecognitionPlugin.rotationDegrees(for: imageOrientation)
        var stacked = false
        if textLayout != .horizontal, let pixelBuffer = CMSampleBufferGetImageBuffer(frame.buffer) {
            stacked = ImageUtils.readLuma(pixelBuffer, rotationDegrees: degrees, longSide: StackedTextReader.frameDetectionLongSide, into: luma)
        }
        return Captured(image: clonedImage, orientation: imageOrientation, degrees: degrees, stacked: stacked, startTime: startTime)
    }

    private func process(_ captured: Captured) -> [String: Any]? {
        let clonedImage = captured.image
        let degrees = captured.degrees
        let visionImage = VisionImage(image: clonedImage)
        visionImage.orientation = captured.orientation

        let uprightSize = degrees % 180 == 0 ? clonedImage.size : CGSize(width: clonedImage.size.height, height: clonedImage.size.width)
        var prepared = [StackedTextReader.Prepared]()
        let group = DispatchGroup()
        var pending = false
        if textLayout == .stacked, captured.stacked, let source = clonedImage.cgImage {
            pending = true
            group.enter()
            stackedQueue.async { [self] in
                prepared = stackedReader.prepare(luma: luma, source: source, rotationDegrees: degrees)
                group.leave()
            }
        }

        do {
            let text: Text
            do {
                text = try textRecognizer.results(in: visionImage)
            } catch {
                if pending { group.wait() }
                throw error
            }

            let stackedBlocks: [StackedTextReader.StackedBlock]
            if pending {
                group.wait()
                stackedBlocks = stackedReader
                    .recognize(prepared, recognizer: textRecognizer)
                    .map { StackedTextReader.toSourceCoordinates($0, degrees: degrees, rotatedSize: uprightSize) }
            } else if captured.stacked, textLayout.shouldReadStacked(text), let source = clonedImage.cgImage {
                stackedBlocks = stackedReader
                    .recognize(stackedReader.prepare(luma: luma, source: source, rotationDegrees: degrees), recognizer: textRecognizer)
                    .map { StackedTextReader.toSourceCoordinates($0, degrees: degrees, rotatedSize: uprightSize) }
            } else {
                stackedBlocks = []
            }

            let processingTime = Int64(Date().timeIntervalSince(captured.startTime) * 1000)
            Logger.performance("Text recognition processing", durationMs: processingTime)

            if text.text.isEmpty && stackedBlocks.isEmpty {
                Logger.debug("No text detected in frame")
                return nil
            }

            Logger.debug("Text detected: \(text.text.count) characters, \(text.blocks.count) blocks, \(stackedBlocks.count) stacked")

            var blocks = processBlocks(text.blocks)
            blocks.append(contentsOf: stackedBlocks.map { StackedTextBlocks.toDictionary($0) })

            return [
                "text": StackedTextBlocks.combineText(text.text, stackedBlocks),
                "blocks": blocks
            ]
        } catch {
            let processingTime = Int64(Date().timeIntervalSince(captured.startTime) * 1000)
            Logger.error("Error during text recognition: \(error.localizedDescription)")
            Logger.performance("Text recognition processing (error)", durationMs: processingTime)
            return nil
        }
    }

    // MARK: - Orientation Mapping

    private func getOrientation(orientation: UIImage.Orientation) -> UIImage.Orientation {
        switch orientation {
        case .up:
            return .up
        case .left:
            return .right  // Swap left and right
        case .down:
            return .down
        case .right:
            return .left   // Swap left and right
        default:
            return .up
        }
    }

    private static func rotationDegrees(for orientation: UIImage.Orientation) -> Int {
        switch orientation {
        case .right: return 90
        case .down: return 180
        case .left: return 270
        default: return 0
        }
    }

    // MARK: - Helper Methods

    private func processBlocks(_ blocks: [TextBlock]) -> [[String: Any]] {
        return blocks.map { block in
            var blockDict: [String: Any] = [
                "text": block.text,
                "frame": processRect(block.frame),
                "cornerPoints": processCornerPoints(block.cornerPoints),
                "lines": processLines(block.lines)
            ]

            if let lang = block.recognizedLanguages.first?.languageCode {
                blockDict["recognizedLanguage"] = lang
            }

            return blockDict
        }
    }

    private func processLines(_ lines: [TextLine]) -> [[String: Any]] {
        return lines.map { line in
            var lineDict: [String: Any] = [
                "text": line.text,
                "frame": processRect(line.frame),
                "cornerPoints": processCornerPoints(line.cornerPoints),
                "elements": processElements(line.elements)
            ]

            if let lang = line.recognizedLanguages.first?.languageCode {
                lineDict["recognizedLanguage"] = lang
            }

            return lineDict
        }
    }

    private func processElements(_ elements: [TextElement]) -> [[String: Any]] {
        return elements.map { element in
            var elementDict: [String: Any] = [
                "text": element.text,
                "frame": processRect(element.frame),
                "cornerPoints": processCornerPoints(element.cornerPoints)
            ]

            if let lang = element.recognizedLanguages.first?.languageCode {
                elementDict["recognizedLanguage"] = lang
            }

            return elementDict
        }
    }

    private func processRect(_ rect: CGRect) -> [String: CGFloat] {
        return [
            "x": rect.midX,
            "y": rect.midY,
            "width": rect.width,
            "height": rect.height
        ]
    }

    private func processCornerPoints(_ cornerPoints: [NSValue]) -> [[String: Int]] {
        return cornerPoints.compactMap { $0.cgPointValue }.map { point in
            ["x": Int(point.x), "y": Int(point.y)]
        }
    }
}
