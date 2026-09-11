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
    private lazy var stackedReader = StackedTextReader()

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
        Logger.info("Text recognition initialized successfully (textLayout: \(textLayout.rawValue))")
    }

    deinit {
        // Clean up ML Kit resources when plugin is deallocated
        // Swift ARC will handle the deallocation, but we log for debugging
        Logger.debug("TextRecognitionPlugin deallocating - ML Kit recognizer resources will be freed")
        // Note: ML Kit resources are automatically freed by ARC when textRecognizer is deallocated
    }

    public override func callback(_ frame: Frame, withArguments arguments: [AnyHashable: Any]?) -> Any? {
        let startTime = Date()

        do {
            let orientation = frame.orientation

            // Clone the camera buffer to UIImage to release the original buffer immediately
            // This prevents buffer exhaustion issues when ML Kit processing takes longer than camera frame rate
            guard let clonedImage = ImageUtils.imageFromSampleBuffer(frame.buffer) else {
                Logger.error("Failed to create vision image from sample buffer")
                return nil
            }
            let visionImage = VisionImage(image: clonedImage)
            let imageOrientation = getOrientation(orientation: orientation)
            visionImage.orientation = imageOrientation

            Logger.debug("Processing frame: \(frame.width)x\(frame.height), orientation: \(orientation.rawValue)")

            // Process synchronously
            let text = try textRecognizer.results(in: visionImage)

            // Runs on an upright copy; frames are mapped back into the buffer's coordinates.
            let stackedBlocks: [StackedTextReader.StackedBlock]
            if textLayout.shouldReadStacked(text) {
                let degrees = TextRecognitionPlugin.rotationDegrees(for: imageOrientation)
                let upright = StackedTextReader.rotatedCopy(clonedImage, degrees: degrees)
                stackedBlocks = stackedReader
                    .read(upright, recognizer: textRecognizer)
                    .map { StackedTextReader.toSourceCoordinates($0, degrees: degrees, rotatedSize: upright.size) }
            } else {
                stackedBlocks = []
            }

            let processingTime = Int64(Date().timeIntervalSince(startTime) * 1000)
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
            let processingTime = Int64(Date().timeIntervalSince(startTime) * 1000)
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
