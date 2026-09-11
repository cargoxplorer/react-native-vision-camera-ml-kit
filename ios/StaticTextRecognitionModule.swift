//
//  StaticTextRecognitionModule.swift
//  react-native-vision-camera-ml-kit
//

import Foundation
import React
import UIKit
import MLKitVision
import MLKitTextRecognition
import MLKitTextRecognitionChinese
import MLKitTextRecognitionDevanagari
import MLKitTextRecognitionJapanese
import MLKitTextRecognitionKorean
import Photos

@objc(StaticTextRecognitionModule)
class StaticTextRecognitionModule: NSObject {

    @objc
    static func requiresMainQueueSetup() -> Bool {
        return false
    }

    @objc
    func recognizeText(_ options: NSDictionary,
                       resolver resolve: @escaping RCTPromiseResolveBlock,
                       rejecter reject: @escaping RCTPromiseRejectBlock) {
        let startTime = Date()

        guard let uri = options["uri"] as? String else {
            reject("INVALID_URI", "URI is required", nil)
            return
        }

        let language = (options["language"] as? String ?? "latin").lowercased()
        let orientation = options["orientation"] as? Int ?? 0
        let textLayout = TextLayout.from(options["textLayout"])

        Logger.debug("Recognizing text from static image: \(uri) (language: \(language), orientation: \(orientation), textLayout: \(textLayout.rawValue))")

        // Create recognizer based on language
        let recognizer: TextRecognizer
        switch language {
        case "chinese":
            recognizer = TextRecognizer.textRecognizer(options: ChineseTextRecognizerOptions())
        case "devanagari":
            recognizer = TextRecognizer.textRecognizer(options: DevanagariTextRecognizerOptions())
        case "japanese":
            recognizer = TextRecognizer.textRecognizer(options: JapaneseTextRecognizerOptions())
        case "korean":
            recognizer = TextRecognizer.textRecognizer(options: KoreanTextRecognizerOptions())
        case "latin", "default":
            recognizer = TextRecognizer.textRecognizer(options: TextRecognizerOptions())
        default:
            Logger.warn("Unknown language '\(language)', defaulting to Latin")
            recognizer = TextRecognizer.textRecognizer(options: TextRecognizerOptions())
        }

        // Load image from URI
        loadImage(from: uri) { [weak self] image, error in
            guard let self = self else { return }

            if let error = error {
                Logger.error("Failed to load image from URI: \(uri)", error: error)
                reject("IMAGE_LOAD_ERROR", "Failed to load image: \(error.localizedDescription)", error)
                return
            }

            guard let image = image else {
                reject("IMAGE_LOAD_ERROR", "Failed to load image: image is nil", nil)
                return
            }

            // The stacked pass needs upright pixels, so EXIF and the orientation option are baked in.
            let stackedSource: UIImage? = textLayout == .horizontal
                ? nil
                : Self.uprightCopy(image, extraDegrees: orientation)

            let visionImage = VisionImage(image: stackedSource ?? image)
            visionImage.orientation = stackedSource == nil ? self.imageOrientation(orientation) : .up

            // Process image
            recognizer.process(visionImage) { text, error in
                let processingTime = Int64(Date().timeIntervalSince(startTime) * 1000)

                if let error = error {
                    Logger.error("Error during static text recognition", error: error)
                    Logger.performance("Static text recognition processing (error)", durationMs: processingTime)
                    reject("RECOGNITION_ERROR", "Text recognition failed: \(error.localizedDescription)", error)
                    return
                }

                let finish: ([StackedTextReader.StackedBlock]) -> Void = { stackedBlocks in
                    Logger.performance("Static text recognition processing", durationMs: processingTime)

                    let recognized = text?.text ?? ""
                    if recognized.isEmpty && stackedBlocks.isEmpty {
                        Logger.debug("No text detected in static image")
                        resolve(NSNull())
                        return
                    }

                    Logger.debug("Text detected in static image: \(recognized.count) characters, \(text?.blocks.count ?? 0) blocks, \(stackedBlocks.count) stacked")

                    var blocks = self.processBlocks(text?.blocks ?? [])
                    blocks.append(contentsOf: stackedBlocks.map { StackedTextBlocks.toDictionary($0) })

                    let result: [String: Any] = [
                        "text": StackedTextBlocks.combineText(recognized, stackedBlocks),
                        "blocks": blocks
                    ]

                    resolve(result)
                }

                guard let source = stackedSource, textLayout.shouldReadStacked(text) else {
                    finish([])
                    return
                }

                // The synchronous recognizer must not run on the main queue this completion arrives on.
                DispatchQueue.global(qos: .userInitiated).async {
                    finish(StackedTextReader().readWithRotationFallback(source, recognizer: recognizer))
                }
            }
        }
    }

    // MARK: - Helper Methods

    private func loadImage(from uri: String, completion: @escaping (UIImage?, Error?) -> Void) {
        if uri.hasPrefix("file://") {
            guard let url = URL(string: uri), let image = UIImage(contentsOfFile: url.path) else {
                completion(nil, NSError(domain: "ImageLoadError", code: 1, userInfo: [NSLocalizedDescriptionKey: "Failed to load image from file path"]))
                return
            }
            completion(image, nil)
        } else if uri.hasPrefix("ph://") {
            let assetId = String(uri.dropFirst(5))
            let fetchResult = PHAsset.fetchAssets(withLocalIdentifiers: [assetId], options: nil)

            guard let asset = fetchResult.firstObject else {
                completion(nil, NSError(domain: "ImageLoadError", code: 3, userInfo: [NSLocalizedDescriptionKey: "Photo asset not found"]))
                return
            }

            let options = PHImageRequestOptions()
            options.isSynchronous = false
            options.deliveryMode = .highQualityFormat

            PHImageManager.default().requestImage(for: asset, targetSize: PHImageManagerMaximumSize, contentMode: .default, options: options) { result, _ in
                if let image = result {
                    completion(image, nil)
                } else {
                    completion(nil, NSError(domain: "ImageLoadError", code: 2, userInfo: [NSLocalizedDescriptionKey: "Failed to load image from Photos"]))
                }
            }
        } else {
            // Try as file path
            if let image = UIImage(contentsOfFile: uri) {
                completion(image, nil)
            } else if let url = URL(string: "file://\(uri)"), let image = UIImage(contentsOfFile: url.path) {
                completion(image, nil)
            } else {
                completion(nil, NSError(domain: "ImageLoadError", code: 5, userInfo: [NSLocalizedDescriptionKey: "Failed to load image from path"]))
            }
        }
    }

    private static func uprightCopy(_ image: UIImage, extraDegrees: Int) -> UIImage {
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        format.opaque = true
        let baked = UIGraphicsImageRenderer(size: image.size, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: image.size))
        }
        return StackedTextReader.rotatedCopy(baked, degrees: extraDegrees)
    }

    private func imageOrientation(_ orientation: Int) -> UIImage.Orientation {
        switch orientation {
        case 90: return .right
        case 180: return .down
        case 270: return .left
        default: return .up
        }
    }

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
