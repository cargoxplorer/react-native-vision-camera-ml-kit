//
//  StackedTextReader.swift
//  react-native-vision-camera-ml-kit
//

import Foundation
import UIKit
import CoreGraphics
import MLKitVision
import MLKitTextRecognition

final class StackedTextReader {

    struct GlyphReading {
        let text: String
        let box: CGRect
    }

    struct StackedBlock {
        let text: String
        let bounds: CGRect
        let glyphs: [GlyphReading]
    }

    struct Prepared {
        let strip: UIImage
        let bounds: CGRect
        let glyphBoxes: [CGRect]
        let widths: [Int]
    }

    static let frameDetectionLongSide = 1920
    static let staticDetectionLongSide = 1200
    static let maxColumnsToRead = 6
    private static let tileHeight = 96
    private static let tileGap = 24
    private static let minTileWidth = 8
    private static let glyphPadding = 0.20
    private static let colourSamples = 24
    private static let rotationSweep = [0, 90, 270, 180]

    private let workspace = StackedTextDetector.Workspace()

    func read(_ image: UIImage, recognizer: TextRecognizer) -> [StackedBlock] {
        guard let source = image.cgImage else { return [] }
        let sourceWidth = source.width
        let sourceHeight = source.height
        guard sourceWidth >= 2, sourceHeight >= 2 else { return [] }

        let started = Date()
        let scale = min(1.0, Double(Self.staticDetectionLongSide) / Double(max(sourceWidth, sourceHeight)))
        let detectionWidth = max(1, Int((Double(sourceWidth) * scale).rounded()))
        let detectionHeight = max(1, Int((Double(sourceHeight) * scale).rounded()))

        guard let rgba = Self.rgbaBuffer(source, width: detectionWidth, height: detectionHeight) else {
            Logger.warn("Could not read pixels for stacked detection")
            return []
        }

        let columns = StackedTextDetector.detect(rgba: rgba, width: detectionWidth, height: detectionHeight, workspace: workspace)
        Logger.performance("stacked.detect", durationMs: Int64(Date().timeIntervalSince(started) * 1000))
        Logger.debug("stacked.columns=\(columns.count) \(columns.map { $0.glyphs.count }) in \(detectionWidth)x\(detectionHeight)")

        let prepared = prepare(columns, inverse: 1.0 / scale, source: source, rotationDegrees: 0, uprightWidth: sourceWidth, uprightHeight: sourceHeight)
        return recognize(prepared, recognizer: recognizer)
    }

    func readWithRotationFallback(_ image: UIImage, recognizer: TextRecognizer) -> [StackedBlock] {
        for degrees in Self.rotationSweep {
            let rotated = Self.rotatedCopy(image, degrees: degrees)
            let blocks = read(rotated, recognizer: recognizer)
            if blocks.isEmpty { continue }
            if degrees == 0 { return blocks }
            let size = rotated.size
            return blocks.map { Self.toSourceCoordinates($0, degrees: degrees, rotatedSize: size) }
        }
        return []
    }

    func prepare(luma: ImageUtils.LumaPlane, source: CGImage, rotationDegrees: Int) -> [Prepared] {
        let started = Date()
        let columns = StackedTextDetector.detect(luma: luma.bytes, width: luma.width, height: luma.height, workspace: workspace)
        Logger.performance("stacked.detect", durationMs: Int64(Date().timeIntervalSince(started) * 1000))
        Logger.debug("stacked.columns=\(columns.count) \(columns.map { $0.glyphs.count }) in \(luma.width)x\(luma.height), passes=\(workspace.passesRun)")

        let upright = rotationDegrees % 180 == 0
        let uprightWidth = upright ? source.width : source.height
        let uprightHeight = upright ? source.height : source.width
        return prepare(columns, inverse: Double(luma.step), source: source, rotationDegrees: rotationDegrees, uprightWidth: uprightWidth, uprightHeight: uprightHeight)
    }

    func recognize(_ prepared: [Prepared], recognizer: TextRecognizer) -> [StackedBlock] {
        prepared.compactMap { readStrip($0, recognizer: recognizer) }
    }

    private func prepare(
        _ columns: [StackedTextDetector.Column],
        inverse: Double,
        source: CGImage,
        rotationDegrees: Int,
        uprightWidth: Int,
        uprightHeight: Int
    ) -> [Prepared] {
        if columns.isEmpty { return [] }
        let started = Date()
        let ranked = columns
            .map { ($0, StackedTextDetector.refine($0)) }
            .sorted { $0.1.score < $1.1.score }
            .prefix(Self.maxColumnsToRead)

        var prepared = [Prepared]()
        for (column, refined) in ranked {
            let glyphBoxes = refined.glyphs.map { Self.toSourceRect($0, inverse: inverse, width: uprightWidth, height: uprightHeight) }
            let tiles = glyphBoxes.indices.map {
                Self.padded(glyphBoxes, index: $0, boxed: refined.boxed[$0], width: uprightWidth, height: uprightHeight)
            }
            let widths = tiles.map { tile -> Int in
                max(Self.minTileWidth, Int((tile.width * CGFloat(Self.tileHeight) / tile.height).rounded()))
            }
            let region = tiles.dropFirst().reduce(tiles[0]) { $0.union($1) }.integral

            guard let crop = Self.uprightCrop(source, region: region, rotationDegrees: rotationDegrees, uprightWidth: uprightWidth, uprightHeight: uprightHeight) else {
                continue
            }
            let strip = Self.buildStrip(crop: crop, tiles: tiles, offset: region.origin, widths: widths, background: Self.medianColour(crop))
            prepared.append(Prepared(
                strip: strip,
                bounds: Self.toSourceRect(column.bounds, inverse: inverse, width: uprightWidth, height: uprightHeight),
                glyphBoxes: glyphBoxes,
                widths: widths
            ))
        }
        Logger.performance("stacked.strip.build", durationMs: Int64(Date().timeIntervalSince(started) * 1000))
        return prepared
    }

    private func readStrip(_ prepared: Prepared, recognizer: TextRecognizer) -> StackedBlock? {
        let started = Date()
        let visionImage = VisionImage(image: prepared.strip)
        visionImage.orientation = .up

        let text: Text
        do {
            text = try recognizer.results(in: visionImage)
        } catch {
            Logger.error("Stacked strip recognition failed: \(error.localizedDescription)")
            return nil
        }
        Logger.performance("stacked.strip.mlkit", durationMs: Int64(Date().timeIntervalSince(started) * 1000))

        let lines = text.blocks.flatMap { $0.lines }.sorted { $0.frame.minX < $1.frame.minX }
        let reading = lines.map { $0.text }.joined(separator: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        Logger.debug("Stacked strip read: '\(reading)'")
        if reading.isEmpty { return nil }

        return StackedBlock(
            text: reading,
            bounds: prepared.bounds,
            glyphs: Self.assignGlyphText(text, glyphBoxes: prepared.glyphBoxes, widths: prepared.widths)
        )
    }

    private static func uprightCrop(
        _ source: CGImage,
        region: CGRect,
        rotationDegrees: Int,
        uprightWidth: Int,
        uprightHeight: Int
    ) -> CGImage? {
        let rotatedSize = CGSize(width: uprightWidth, height: uprightHeight)
        let inSource = toSourceCoordinates(region, degrees: rotationDegrees, rotatedSize: rotatedSize)
        guard let cropped = source.cropping(to: inSource) else { return nil }
        if rotationDegrees % 360 == 0 { return cropped }
        return rotatedCopy(UIImage(cgImage: cropped), degrees: rotationDegrees).cgImage
    }

    private static func buildStrip(
        crop: CGImage,
        tiles: [CGRect],
        offset: CGPoint,
        widths: [Int],
        background: UIColor
    ) -> UIImage {
        let size = CGSize(
            width: widths.reduce(0, +) + tileGap * (tiles.count + 1),
            height: tileHeight + tileGap * 2
        )
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        format.opaque = true

        return UIGraphicsImageRenderer(size: size, format: format).image { context in
            background.setFill()
            context.fill(CGRect(origin: .zero, size: size))

            var x = CGFloat(tileGap)
            for index in tiles.indices {
                let tile = tiles[index].offsetBy(dx: -offset.x, dy: -offset.y)
                guard let cropped = crop.cropping(to: tile) else {
                    x += CGFloat(widths[index] + tileGap)
                    continue
                }
                let destination = CGRect(
                    x: x,
                    y: CGFloat(tileGap),
                    width: CGFloat(widths[index]),
                    height: CGFloat(tileHeight)
                )
                UIImage(cgImage: cropped).draw(in: destination)
                x += CGFloat(widths[index] + tileGap)
            }
        }
    }

    private static func assignGlyphText(
        _ text: Text,
        glyphBoxes: [CGRect],
        widths: [Int]
    ) -> [GlyphReading] {
        var readings = [String](repeating: "", count: glyphBoxes.count)
        var starts = [CGFloat]()
        var x = CGFloat(tileGap)
        for width in widths {
            starts.append(x)
            x += CGFloat(width + tileGap)
        }

        for block in text.blocks {
            for line in block.lines {
                for element in line.elements {
                    let characters = Array(element.text)
                    if characters.isEmpty { continue }
                    let frame = element.frame
                    let step = frame.width / CGFloat(characters.count)
                    for (offset, character) in characters.enumerated() {
                        let centre = frame.minX + step * (CGFloat(offset) + 0.5)
                        guard let tile = starts.lastIndex(where: { centre >= $0 }) else { continue }
                        if centre > starts[tile] + CGFloat(widths[tile]) { continue }
                        readings[tile].append(character)
                    }
                }
            }
        }

        return glyphBoxes.enumerated().map { GlyphReading(text: readings[$0.offset], box: $0.element) }
    }

    private static func rgbaBuffer(_ source: CGImage, width: Int, height: Int) -> [UInt8]? {
        var buffer = [UInt8](repeating: 0, count: width * height * 4)
        let bitmapInfo = CGImageAlphaInfo.premultipliedLast.rawValue
        let drawn: Bool = buffer.withUnsafeMutableBytes { raw -> Bool in
            guard let base = raw.baseAddress,
                  let context = CGContext(
                    data: base,
                    width: width,
                    height: height,
                    bitsPerComponent: 8,
                    bytesPerRow: width * 4,
                    space: CGColorSpaceCreateDeviceRGB(),
                    bitmapInfo: bitmapInfo
                  ) else { return false }
            context.interpolationQuality = .high
            context.draw(source, in: CGRect(x: 0, y: 0, width: width, height: height))
            return true
        }
        return drawn ? buffer : nil
    }

    private static func medianColour(_ crop: CGImage) -> UIColor {
        let width = crop.width
        let height = crop.height
        guard width > 0, height > 0, let pixels = rgbaBuffer(crop, width: width, height: height) else { return .black }

        let stepX = max(1, width / colourSamples)
        let stepY = max(1, height / colourSamples)
        var reds = [Int](), greens = [Int](), blues = [Int]()
        var y = 0
        while y < height {
            var x = 0
            while x < width {
                let base = (y * width + x) * 4
                reds.append(Int(pixels[base]))
                greens.append(Int(pixels[base + 1]))
                blues.append(Int(pixels[base + 2]))
                x += stepX
            }
            y += stepY
        }
        guard !reds.isEmpty else { return .black }
        let mid = reds.count / 2
        return UIColor(
            red: CGFloat(reds.sorted()[mid]) / 255.0,
            green: CGFloat(greens.sorted()[mid]) / 255.0,
            blue: CGFloat(blues.sorted()[mid]) / 255.0,
            alpha: 1
        )
    }

    private static func toSourceRect(
        _ box: StackedTextDetector.Box,
        inverse: Double,
        width: Int,
        height: Int
    ) -> CGRect {
        let left = min(max(0, (Double(box.left) * inverse).rounded()), Double(width - 1))
        let top = min(max(0, (Double(box.top) * inverse).rounded()), Double(height - 1))
        let right = min(max(left + 1, (Double(box.right) * inverse).rounded()), Double(width))
        let bottom = min(max(top + 1, (Double(box.bottom) * inverse).rounded()), Double(height))
        return CGRect(x: left, y: top, width: right - left, height: bottom - top)
    }

    private static func padded(_ all: [CGRect], index: Int, boxed: Bool, width: Int, height: Int) -> CGRect {
        let box = all[index]
        let pad = boxed ? 0 : (box.height * CGFloat(glyphPadding)).rounded()
        let gapUp = index > 0 ? box.minY - all[index - 1].maxY : pad * 2
        let gapDown = index + 1 < all.count ? all[index + 1].minY - box.maxY : pad * 2
        let padY = min(pad, max(0, gapUp / 2), max(0, gapDown / 2)).rounded(.down)
        let left = min(max(0, box.minX - pad), CGFloat(width - 1))
        let top = min(max(0, box.minY - padY), CGFloat(height - 1))
        let right = min(max(left + 1, box.maxX + pad), CGFloat(width))
        let bottom = min(max(top + 1, box.maxY + padY), CGFloat(height))
        return CGRect(x: left, y: top, width: right - left, height: bottom - top)
    }

    static func rotatedCopy(_ image: UIImage, degrees: Int) -> UIImage {
        let normalized = ((degrees % 360) + 360) % 360
        guard normalized != 0, let source = image.cgImage else { return image }

        let width = CGFloat(source.width)
        let height = CGFloat(source.height)
        let size = normalized == 180
            ? CGSize(width: width, height: height)
            : CGSize(width: height, height: width)

        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        format.opaque = true

        return UIGraphicsImageRenderer(size: size, format: format).image { context in
            let cgContext = context.cgContext
            cgContext.translateBy(x: size.width / 2, y: size.height / 2)
            cgContext.rotate(by: CGFloat(normalized) * .pi / 180)
            cgContext.translateBy(x: -width / 2, y: -height / 2)
            UIImage(cgImage: source).draw(in: CGRect(x: 0, y: 0, width: width, height: height))
        }
    }

    static func toSourceCoordinates(_ block: StackedBlock, degrees: Int, rotatedSize: CGSize) -> StackedBlock {
        if ((degrees % 360) + 360) % 360 == 0 { return block }
        return StackedBlock(
            text: block.text,
            bounds: toSourceCoordinates(block.bounds, degrees: degrees, rotatedSize: rotatedSize),
            glyphs: block.glyphs.map {
                GlyphReading(
                    text: $0.text,
                    box: toSourceCoordinates($0.box, degrees: degrees, rotatedSize: rotatedSize)
                )
            }
        )
    }

    static func toSourceCoordinates(_ rect: CGRect, degrees: Int, rotatedSize: CGSize) -> CGRect {
        switch ((degrees % 360) + 360) % 360 {
        case 90:
            return CGRect(
                x: rect.minY,
                y: rotatedSize.width - rect.maxX,
                width: rect.height,
                height: rect.width
            )
        case 180:
            return CGRect(
                x: rotatedSize.width - rect.maxX,
                y: rotatedSize.height - rect.maxY,
                width: rect.width,
                height: rect.height
            )
        case 270:
            return CGRect(
                x: rotatedSize.height - rect.maxY,
                y: rect.minX,
                width: rect.height,
                height: rect.width
            )
        default:
            return rect
        }
    }
}
