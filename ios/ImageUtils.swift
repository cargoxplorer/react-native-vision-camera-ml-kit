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

            // Validate rotation parameter
            guard [0, 90, 180, 270].contains(rotation) else {
                Logger.error("Invalid rotation value: \(rotation). Must be 0, 90, 180, or 270")
                return nil
            }

            var image = CIImage(cvPixelBuffer: pixelBuffer)

            // Apply rotation if needed
            if rotation != 0 {
                let radians = CGFloat(rotation) * CGFloat.pi / 180.0
                let rotationTransform = CGAffineTransform(rotationAngle: radians)
                image = image.transformed(by: rotationTransform)

                // Adjust extent after rotation to handle potentially negative origins
                let extent = image.extent
                if extent.origin.x != 0 || extent.origin.y != 0 {
                    let offsetTransform = CGAffineTransform(translationX: -extent.origin.x, y: -extent.origin.y)
                    image = image.transformed(by: offsetTransform)
                }
            }

            // Convert CIImage to CGImage using the shared context
            guard let cgImage = ciContext.createCGImage(image, from: image.extent) else {
                Logger.error("Failed to create CGImage from CIImage")
                return nil
            }

            // Create UIImage from CGImage
            return UIImage(cgImage: cgImage, scale: 1.0, orientation: .up)
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

        // Create CIImage from pixel buffer
        // Note: CIImage(cvPixelBuffer:) does not throw; it returns immediately
        return CIImage(cvPixelBuffer: pixelBuffer)
    }

    /// Invert the colors of a CIImage (for white-on-black barcode detection).
    ///
    /// - Parameter ciImage: The input CIImage
    /// - Returns: An inverted CIImage, or nil on failure
    @objc public static func invertImage(_ ciImage: CIImage) -> CIImage? {
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
    }

    /// Convert a CIImage to UIImage.
    ///
    /// - Parameter ciImage: The CIImage to convert
    /// - Returns: A UIImage, or nil on failure
    @objc public static func uiImageFromCIImage(_ ciImage: CIImage) -> UIImage? {
        guard let cgImage = ciContext.createCGImage(ciImage, from: ciImage.extent) else {
            Logger.error("Failed to create CGImage from CIImage for UIImage conversion")
            return nil
        }

        return UIImage(cgImage: cgImage, scale: 1.0, orientation: .up)
    }

    public final class LumaPlane {
        public fileprivate(set) var bytes = [UInt8]()
        public private(set) var width = 0
        public private(set) var height = 0
        public private(set) var step = 1

        public init() {}

        fileprivate func ensure(_ count: Int) {
            if bytes.count < count { bytes = [UInt8](repeating: 0, count: count) }
        }

        fileprivate func finish(width: Int, height: Int, step: Int) {
            self.width = width
            self.height = height
            self.step = step
        }

        func set(_ bytes: [UInt8], width: Int, height: Int, step: Int) {
            self.bytes = bytes
            finish(width: width, height: height, step: step)
        }
    }

    public static func readLuma(_ pixelBuffer: CVPixelBuffer, rotationDegrees: Int, longSide: Int, into: LumaPlane) -> Bool {
        CVPixelBufferLockBaseAddress(pixelBuffer, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(pixelBuffer, .readOnly) }

        let planar = CVPixelBufferIsPlanar(pixelBuffer)
        let format = CVPixelBufferGetPixelFormatType(pixelBuffer)
        guard planar || format == kCVPixelFormatType_32BGRA else {
            Logger.warn("Unsupported pixel format for stacked detection: \(format)")
            return false
        }
        let sourceWidth = planar ? CVPixelBufferGetWidthOfPlane(pixelBuffer, 0) : CVPixelBufferGetWidth(pixelBuffer)
        let sourceHeight = planar ? CVPixelBufferGetHeightOfPlane(pixelBuffer, 0) : CVPixelBufferGetHeight(pixelBuffer)
        let rowStride = planar ? CVPixelBufferGetBytesPerRowOfPlane(pixelBuffer, 0) : CVPixelBufferGetBytesPerRow(pixelBuffer)
        guard let baseAddress = planar ? CVPixelBufferGetBaseAddressOfPlane(pixelBuffer, 0) : CVPixelBufferGetBaseAddress(pixelBuffer) else {
            return false
        }
        let source = baseAddress.assumingMemoryBound(to: UInt8.self)
        let pixelStride = planar ? 1 : 4

        let step = max(1, max(sourceWidth, sourceHeight) / longSide)
        let width = sourceWidth / step
        let height = sourceHeight / step
        into.ensure(width * height)
        let rotation = ((rotationDegrees % 360) + 360) % 360
        let samples = step * step
        let half = samples / 2

        @inline(__always) func luma(_ offset: Int) -> Int {
            if planar { return Int(source[offset]) }
            return (Int(source[offset + 2]) * 299 + Int(source[offset + 1]) * 587 + Int(source[offset]) * 114) / 1000
        }

        into.bytes.withUnsafeMutableBufferPointer { out in
            for dy in 0..<height {
                var index: Int
                let strideX: Int
                switch rotation {
                case 90: index = height - 1 - dy; strideX = height
                case 180: index = (height - 1 - dy) * width + (width - 1); strideX = -1
                case 270: index = (width - 1) * height + dy; strideX = -height
                default: index = dy * width; strideX = 1
                }
                let rowOffset = dy * step * rowStride
                if step == 1 && planar {
                    for dx in 0..<width {
                        out[index] = source[rowOffset + dx]
                        index += strideX
                    }
                    continue
                }
                for dx in 0..<width {
                    var sum = 0
                    let offset = rowOffset + dx * step * pixelStride
                    for s in 0..<step {
                        let lineOffset = offset + s * rowStride
                        for t in 0..<step { sum += luma(lineOffset + t * pixelStride) }
                    }
                    out[index] = UInt8((sum + half) / samples)
                    index += strideX
                }
            }
        }

        into.finish(width: rotation % 180 == 0 ? width : height, height: rotation % 180 == 0 ? height : width, step: step)
        return true
    }
}
