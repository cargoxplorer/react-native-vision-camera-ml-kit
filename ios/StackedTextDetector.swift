//
//  StackedTextDetector.swift
//  react-native-vision-camera-ml-kit
//

import Foundation

// Finds columns of upright characters (container numbers on doors) that ML Kit cannot read.
public enum StackedTextDetector {

    public struct Box: Equatable {
        public let left: Int
        public let top: Int
        public let right: Int
        public let bottom: Int

        public var width: Int { right - left }
        public var height: Int { bottom - top }
        public var centerX: Double { Double(left + right) / 2.0 }
    }

    public struct Column: Equatable {
        public let bounds: Box
        public let glyphs: [Box]
    }

    private static let brightThresholds = [180, 150, 210]
    private static let darkThreshold = 90
    private static let maxChroma = 85
    private static let maxForegroundFraction = 0.40
    private static let minHeightFraction = 0.006
    private static let maxHeightFraction = 0.10
    private static let minWidthFraction = 0.004
    private static let maxWidthFraction = 0.07
    private static let minAspect = 0.8
    private static let maxAspect = 6.0
    private static let minFill = 0.2
    public static let minGlyphsPerColumn = 8
    private static let maxVerticalGapInHeights = 3.0
    private static let duplicateIoU = 0.55

    // Scratch buffers reused across frames.
    public final class Workspace {
        fileprivate var low = [UInt8]()
        fileprivate var high = [UInt8]()
        fileprivate var mask = [Bool]()
        fileprivate var queue = [Int]()

        public init() {}

        fileprivate func ensure(_ size: Int) {
            if low.count < size { low = [UInt8](repeating: 0, count: size) }
            if high.count < size { high = [UInt8](repeating: 0, count: size) }
            if mask.count < size { mask = [Bool](repeating: false, count: size) }
            if queue.count < size { queue = [Int](repeating: 0, count: size) }
        }
    }

    public static func detect(
        rgba: [UInt8],
        width: Int,
        height: Int,
        workspace: Workspace = Workspace()
    ) -> [Column] {
        guard width >= 2, height >= 2 else { return [] }
        let count = width * height
        guard rgba.count >= count * 4 else { return [] }
        workspace.ensure(count)

        for i in 0..<count {
            let base = i * 4
            let r = rgba[base]
            let g = rgba[base + 1]
            let b = rgba[base + 2]
            workspace.low[i] = Swift.min(r, Swift.min(g, b))
            workspace.high[i] = Swift.max(r, Swift.max(g, b))
        }

        let longSide = Swift.max(width, height)
        var found = [Column]()
        for threshold in brightThresholds {
            found += collectColumns(workspace, count, width, height, longSide, bright: true, threshold: threshold)
        }
        found += collectColumns(workspace, count, width, height, longSide, bright: false, threshold: darkThreshold)

        return deduplicate(found)
    }

    private static func collectColumns(
        _ workspace: Workspace,
        _ count: Int,
        _ width: Int,
        _ height: Int,
        _ longSide: Int,
        bright: Bool,
        threshold: Int
    ) -> [Column] {
        var foreground = 0
        for i in 0..<count {
            let low = Int(workspace.low[i])
            let high = Int(workspace.high[i])
            let on = bright ? (low >= threshold && high - low < maxChroma) : (high < threshold)
            workspace.mask[i] = on
            if on { foreground += 1 }
        }
        // A threshold that swallows the background cannot yield glyphs.
        if foreground == 0 || Double(foreground) > Double(count) * maxForegroundFraction { return [] }

        let glyphs = components(workspace, count, width, height, longSide)
        if glyphs.count < minGlyphsPerColumn { return [] }
        return cluster(glyphs)
    }

    // 4-connected labelling with an explicit queue; pixels are cleared on enqueue.
    private static func components(
        _ workspace: Workspace,
        _ count: Int,
        _ width: Int,
        _ height: Int,
        _ longSide: Int
    ) -> [Box] {
        let minHeight = minHeightFraction * Double(longSide)
        let maxHeight = maxHeightFraction * Double(longSide)
        let minWidth = minWidthFraction * Double(longSide)
        let maxWidth = maxWidthFraction * Double(longSide)

        var glyphs = [Box]()
        for start in 0..<count {
            if !workspace.mask[start] { continue }
            workspace.mask[start] = false
            workspace.queue[0] = start
            var head = 0
            var tail = 1
            var left = width
            var right = 0
            var top = height
            var bottom = 0
            while head < tail {
                let index = workspace.queue[head]
                head += 1
                let x = index % width
                let y = index / width
                if x < left { left = x }
                if x > right { right = x }
                if y < top { top = y }
                if y > bottom { bottom = y }
                if x > 0, workspace.mask[index - 1] {
                    workspace.mask[index - 1] = false; workspace.queue[tail] = index - 1; tail += 1
                }
                if x + 1 < width, workspace.mask[index + 1] {
                    workspace.mask[index + 1] = false; workspace.queue[tail] = index + 1; tail += 1
                }
                if y > 0, workspace.mask[index - width] {
                    workspace.mask[index - width] = false; workspace.queue[tail] = index - width; tail += 1
                }
                if y + 1 < height, workspace.mask[index + width] {
                    workspace.mask[index + width] = false; workspace.queue[tail] = index + width; tail += 1
                }
            }

            let boxWidth = Double(right - left + 1)
            let boxHeight = Double(bottom - top + 1)
            if boxWidth < minWidth || boxWidth > maxWidth { continue }
            if boxHeight < minHeight || boxHeight > maxHeight { continue }
            let aspect = boxHeight / boxWidth
            if aspect < minAspect || aspect > maxAspect { continue }
            if Double(tail) / (boxWidth * boxHeight) < minFill { continue }
            glyphs.append(Box(left: left, top: top, right: right + 1, bottom: bottom + 1))
        }
        return glyphs
    }

    private static func cluster(_ glyphs: [Box]) -> [Column] {
        let ordered = glyphs.sorted { ($0.top, $0.left) < ($1.top, $1.left) }
        var open = [[Box]]()

        for glyph in ordered {
            var best: Int?
            var bestDistance = Double.greatestFiniteMagnitude
            for (index, column) in open.enumerated() {
                guard let last = column.last else { continue }
                let distance = abs(glyph.centerX - last.centerX)
                if distance > Double(Swift.max(glyph.width, last.width)) { continue }
                if Double(glyph.top - last.bottom) > maxVerticalGapInHeights * Double(last.height) { continue }
                if distance < bestDistance {
                    bestDistance = distance
                    best = index
                }
            }
            if let best = best { open[best].append(glyph) } else { open.append([glyph]) }
        }

        return open.filter { $0.count >= minGlyphsPerColumn }.map { column in
            Column(
                bounds: Box(
                    left: column.map { $0.left }.min()!,
                    top: column.map { $0.top }.min()!,
                    right: column.map { $0.right }.max()!,
                    bottom: column.map { $0.bottom }.max()!
                ),
                glyphs: column
            )
        }
    }

    private static func deduplicate(_ columns: [Column]) -> [Column] {
        let ranked = columns.sorted { a, b in
            if a.glyphs.count != b.glyphs.count { return a.glyphs.count > b.glyphs.count }
            if a.bounds.top != b.bounds.top { return a.bounds.top < b.bounds.top }
            return a.bounds.left < b.bounds.left
        }
        var kept = [Column]()
        for column in ranked {
            if !kept.contains(where: { intersectionOverUnion($0.bounds, column.bounds) > duplicateIoU }) {
                kept.append(column)
            }
        }
        return kept
    }

    private static func intersectionOverUnion(_ a: Box, _ b: Box) -> Double {
        let width = Swift.max(0, Swift.min(a.right, b.right) - Swift.max(a.left, b.left))
        let height = Swift.max(0, Swift.min(a.bottom, b.bottom) - Swift.max(a.top, b.top))
        let intersection = Double(width * height)
        let union = Double(a.width * a.height) + Double(b.width * b.height) - intersection
        return union <= 0 ? 0 : intersection / union
    }
}
