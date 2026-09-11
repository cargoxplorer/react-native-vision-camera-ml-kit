//
//  StackedTextReader.swift
//  react-native-vision-camera-ml-kit
//

import Foundation
import UIKit
import CoreGraphics
import MLKitVision
import MLKitTextRecognition

// Re-lays a detected column as a horizontal strip, reads it with ML Kit and maps frames back.
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

    static let detectionLongSide = 1200
    private static let tileHeight = 96
    private static let tileGap = 24
    private static let minTileWidth = 8
    private static let glyphPadding = 0.20
    private static let mergedHeightRatio = 1.7
    private static let boxedWidthRatio = 1.35
    private static let boxedHeightRatio = 1.12
    private static let rotationSweep = [0, 90, 270, 180]

    private let workspace = StackedTextDetector.Workspace()

    func read(_ image: UIImage, recognizer: TextRecognizer) -> [StackedBlock] {
        guard let source = image.cgImage else { return [] }
        let sourceWidth = source.width
        let sourceHeight = source.height
        guard sourceWidth >= 2, sourceHeight >= 2 else { return [] }

        let scale = min(1.0, Double(Self.detectionLongSide) / Double(max(sourceWidth, sourceHeight)))
        let detectionWidth = max(1, Int((Double(sourceWidth) * scale).rounded()))
        let detectionHeight = max(1, Int((Double(sourceHeight) * scale).rounded()))

        guard let rgba = Self.rgbaBuffer(source, width: detectionWidth, height: detectionHeight) else {
            Logger.warn("Could not read pixels for stacked detection")
            return []
        }

        let columns = StackedTextDetector.detect(
            rgba: rgba,
            width: detectionWidth,
            height: detectionHeight,
            workspace: workspace
        )
        if columns.isEmpty { return [] }

        let inverse = 1.0 / scale
        return columns.compactMap { column in
            readColumn(
                column,
                source: source,
                inverse: inverse,
                detectionPixels: rgba,
                detectionWidth: detectionWidth,
                detectionHeight: detectionHeight,
                recognizer: recognizer
            )
        }
    }

    // Orientation metadata does not affect the Latin recognizer, so pixels are rotated.
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

    private func readColumn(
        _ column: StackedTextDetector.Column,
        source: CGImage,
        inverse: Double,
        detectionPixels: [UInt8],
        detectionWidth: Int,
        detectionHeight: Int,
        recognizer: TextRecognizer
    ) -> StackedBlock? {
        let (glyphs, boxed) = Self.refineGlyphs(column.glyphs)
        let glyphBoxes = glyphs.map { Self.toSourceRect($0, inverse: inverse, source: source) }
        let tiles = glyphBoxes.indices.map { Self.padded(glyphBoxes, index: $0, boxed: boxed.contains($0), source: source) }
        let widths = tiles.map { tile -> Int in
            max(Self.minTileWidth, Int((tile.width * CGFloat(Self.tileHeight) / tile.height).rounded()))
        }

        let background = Self.medianColour(
            column.bounds,
            pixels: detectionPixels,
            width: detectionWidth,
            height: detectionHeight
        )
        let strip = Self.buildStrip(source: source, tiles: tiles, widths: widths, background: background)

        let visionImage = VisionImage(image: strip)
        visionImage.orientation = .up

        let text: Text
        do {
            text = try recognizer.results(in: visionImage)
        } catch {
            Logger.error("Stacked strip recognition failed: \(error.localizedDescription)")
            return nil
        }

        let lines = text.blocks.flatMap { $0.lines }.sorted { $0.frame.minX < $1.frame.minX }
        let reading = lines.map { $0.text }.joined(separator: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        if reading.isEmpty { return nil }

        return StackedBlock(
            text: reading,
            bounds: Self.toSourceRect(column.bounds, inverse: inverse, source: source),
            glyphs: Self.assignGlyphText(text, glyphBoxes: glyphBoxes, widths: widths)
        )
    }

    private static func buildStrip(
        source: CGImage,
        tiles: [CGRect],
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
                guard let cropped = source.cropping(to: tiles[index]) else {
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

    // iOS ML Kit stops at elements, so an element's characters are spread evenly over its width.
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

    // Two characters stuck together become one tall glyph; the check-digit frame merges with its digit.
    private static func refineGlyphs(_ detected: [StackedTextDetector.Box]) -> ([StackedTextDetector.Box], Set<Int>) {
        let medianWidth = detected.map { $0.width }.sorted()[detected.count / 2]
        let medianHeight = detected.map { $0.height }.sorted()[detected.count / 2]
        let glyphs = detected.flatMap { g -> [StackedTextDetector.Box] in
            let parts = Int((Double(g.height) / Double(medianHeight)).rounded())
            guard parts >= 2, Double(g.height) > Double(medianHeight) * mergedHeightRatio else { return [g] }
            let step = Double(g.height) / Double(parts)
            return (0..<parts).map { k in
                StackedTextDetector.Box(
                    left: g.left,
                    top: g.top + Int(Double(k) * step),
                    right: g.right,
                    bottom: g.top + Int(Double(k + 1) * step)
                )
            }
        }
        var boxed = Set<Int>()
        let refined = glyphs.enumerated().map { index, g -> StackedTextDetector.Box in
            guard Double(g.width) > Double(medianWidth) * boxedWidthRatio,
                  Double(g.height) > Double(medianHeight) * boxedHeightRatio else { return g }
            boxed.insert(index)
            let dx = (g.width - medianWidth) / 2
            let dy = (g.height - medianHeight) / 2
            return StackedTextDetector.Box(left: g.left + dx, top: g.top + dy, right: g.right - dx, bottom: g.bottom - dy)
        }
        return (refined, boxed)
    }

    // Median rather than mean: the door dominates the area, the paint must not lighten the background.
    private static func medianColour(
        _ bounds: StackedTextDetector.Box,
        pixels: [UInt8],
        width: Int,
        height: Int
    ) -> UIColor {
        let left = max(0, bounds.left)
        let top = max(0, bounds.top)
        let right = min(width, bounds.right)
        let bottom = min(height, bounds.bottom)
        guard right > left, bottom > top else { return .black }

        let stepX = max(1, (right - left) / 24)
        let stepY = max(1, (bottom - top) / 24)
        var reds = [Int](), greens = [Int](), blues = [Int]()
        var y = top
        while y < bottom {
            var x = left
            while x < right {
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
        source: CGImage
    ) -> CGRect {
        let left = min(max(0, (Double(box.left) * inverse).rounded()), Double(source.width - 1))
        let top = min(max(0, (Double(box.top) * inverse).rounded()), Double(source.height - 1))
        let right = min(max(left + 1, (Double(box.right) * inverse).rounded()), Double(source.width))
        let bottom = min(max(top + 1, (Double(box.bottom) * inverse).rounded()), Double(source.height))
        return CGRect(x: left, y: top, width: right - left, height: bottom - top)
    }

    // Vertical padding stops halfway to the neighbouring glyph; a boxed digit gets none, or its frame returns.
    private static func padded(_ all: [CGRect], index: Int, boxed: Bool, source: CGImage) -> CGRect {
        let box = all[index]
        let pad = boxed ? 0 : (box.height * CGFloat(glyphPadding)).rounded()
        let gapUp = index > 0 ? box.minY - all[index - 1].maxY : pad * 2
        let gapDown = index + 1 < all.count ? all[index + 1].minY - box.maxY : pad * 2
        let padY = min(pad, max(0, gapUp / 2), max(0, gapDown / 2)).rounded(.down)
        let left = min(max(0, box.minX - pad), CGFloat(source.width - 1))
        let top = min(max(0, box.minY - padY), CGFloat(source.height - 1))
        let right = min(max(left + 1, box.maxX + pad), CGFloat(source.width))
        let bottom = min(max(top + 1, box.maxY + padY), CGFloat(source.height))
        return CGRect(x: left, y: top, width: right - left, height: bottom - top)
    }

    // Returns the image itself for a whole turn.
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
