//
//  ImageUtils.swift
//  react-native-vision-camera-ml-kit
//
//  Utility class for efficient image cloning and conversion.
//
//  Purpose: Clone camera CMSampleBuffer data to UIImage to release the original buffer immediately.
//  This prevents buffer exhaustion issues when ML Kit processing takes longer than the camera frame rate.
//
//  The camera frame buffer pool is limited (typically 3-6 buffers on iOS).
//  If we hold references to CMSampleBuffer objects during ML Kit processing, the buffer pool fills up,
//  causing frame drops, stuttering, or memory issues.
//  By copying to a UIImage first, we can release the original CMSampleBuffer immediately.
//

import Foundation
import AVFoundation
import CoreImage
import MLKitVision

@objc(ImageUtils)
public class ImageUtils: NSObject {

    /// Lazy-initialized CIContext for efficient image processing
    /// Reused across all frame processors to minimize per-frame allocations
    private static let ciContext: CIContext = {
        return CIContext(options: [.useSoftwareRenderer: false])
    }()

    /// Lock for thread-safe CIContext access
    private static let contextLock = NSLock()

    /// Clone a CMSampleBuffer to a UIImage for independent processing.
    ///
    /// This method extracts pixel data from the camera buffer and creates a new UIImage.
    /// The original CMSampleBuffer can be released immediately after this call returns.
    ///
    /// - Parameters:
    ///   - sampleBuffer: The camera sample buffer (video frame)
    ///   - rotation: Rotation to apply in degrees (0, 90, 180, 270)
    /// - Returns: A new UIImage containing the frame data, or nil on failure
    @objc public static func imageFromSampleBuffer(_ sampleBuffer: CMSampleBuffer, rotation: Int = 0) -> UIImage? {
        return autoreleasepool {
            guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
                Logger.error("Failed to get pixel buffer from sample buffer")
                return nil
            }

            do {
                var image = CIImage(cvPixelBuffer: pixelBuffer)

                // Apply rotation if needed
                if rotation != 0 {
                    let radians = CGFloat(rotation) * CGFloat.pi / 180.0
                    let rotationTransform = CGAffineTransform(rotationAngle: radians)
                    image = image.transformed(by: rotationTransform)
                }

                // Convert CIImage to CGImage using the shared context
                contextLock.lock()
                guard let cgImage = ciContext.createCGImage(image, from: image.extent) else {
                    contextLock.unlock()
                    Logger.error("Failed to create CGImage from CIImage")
                    return nil
                }
                contextLock.unlock()

                // Create UIImage from CGImage
                return UIImage(cgImage: cgImage, scale: 1.0, orientation: .up)
            } catch {
                Logger.error("Failed to convert sample buffer to image", error: error)
                return nil
            }
        }
    }

    /// Create a VisionImage from a CMSampleBuffer by cloning the data first.
    ///
    /// This is the recommended method for frame processors to ensure proper buffer lifecycle management.
    ///
    /// - Parameters:
    ///   - sampleBuffer: The camera sample buffer
    ///   - rotation: Rotation to apply in degrees
    /// - Returns: A VisionImage ready for ML Kit processing, or nil on failure
    @objc public static func visionImageFromSampleBuffer(_ sampleBuffer: CMSampleBuffer, rotation: Int = 0) -> VisionImage? {
        guard let uiImage = imageFromSampleBuffer(sampleBuffer, rotation: rotation) else {
            return nil
        }

        let visionImage = VisionImage(image: uiImage)
        return visionImage
    }

    /// Convert a CMSampleBuffer to a CIImage for use with CoreImage filters (e.g., inverted detection).
    ///
    /// This method creates a CIImage directly from the pixel buffer without intermediate UIImage conversion.
    /// Use this for efficient filter processing.
    ///
    /// - Parameter sampleBuffer: The camera sample buffer
    /// - Returns: A CIImage, or nil on failure
    @objc public static func ciImageFromSampleBuffer(_ sampleBuffer: CMSampleBuffer) -> CIImage? {
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
            Logger.error("Failed to get pixel buffer from sample buffer for CIImage")
            return nil
        }

        do {
            return CIImage(cvPixelBuffer: pixelBuffer)
        } catch {
            Logger.error("Failed to create CIImage from pixel buffer", error: error)
            return nil
        }
    }

    /// Invert the colors of a CIImage (for white-on-black barcode detection).
    ///
    /// - Parameter ciImage: The input CIImage
    /// - Returns: An inverted CIImage, or nil on failure
    @objc public static func invertImage(_ ciImage: CIImage) -> CIImage? {
        do {
            guard let invertFilter = CIFilter(name: "CIColorInvert") else {
                Logger.error("Failed to create color invert filter")
                return nil
            }

            invertFilter.setValue(ciImage, forKey: kCIInputImageKey)

            guard let outputImage = invertFilter.outputImage else {
                Logger.error("Color invert filter produced no output")
                return nil
            }

            return outputImage
        } catch {
            Logger.error("Failed to invert image", error: error)
            return nil
        }
    }

    /// Convert a CIImage to UIImage.
    ///
    /// - Parameter ciImage: The CIImage to convert
    /// - Returns: A UIImage, or nil on failure
    @objc public static func uiImageFromCIImage(_ ciImage: CIImage) -> UIImage? {
        contextLock.lock()
        guard let cgImage = ciContext.createCGImage(ciImage, from: ciImage.extent) else {
            contextLock.unlock()
            Logger.error("Failed to create CGImage from CIImage for UIImage conversion")
            return nil
        }
        contextLock.unlock()

        return UIImage(cgImage: cgImage, scale: 1.0, orientation: .up)
    }
}
