//
//  StackedTextDetector.swift
//  react-native-vision-camera-ml-kit
//

import Foundation

public enum StackedTextDetector {

    public struct Box: Equatable {
        public let left: Int
        public let top: Int
        public let right: Int
        public let bottom: Int

        public var width: Int { right - left }
        public var height: Int { bottom - top }
        public var centerX: Double { Double(left + right) / 2.0 }
        public var centerY: Double { Double(top + bottom) / 2.0 }
    }

    public struct Column: Equatable {
        public let bounds: Box
        public let glyphs: [Box]
    }

    public struct Refined {
        public let glyphs: [Box]
        public let boxed: [Bool]
        public let score: Double
    }

    private static let brightThresholds = [180, 210, 150]
    private static let darkThreshold = 90
    private static let darkPass = 3
    private static let passCount = 4
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

    public static let containerNumberLength = 11
    private static let mergedHeightRatio = 1.7
    private static let boxedWidthRatio = 1.35
    private static let boxedHeightRatio = 1.12
    private static let outlierMinRatio = 0.6
    private static let outlierMaxRatio = 1.6
    private static let offCentreInWidths = 0.5
    private static let scorePerMissingGlyph = 0.5
    private static let scoreOffCentreWeight = 2.0

    public final class Workspace {
        fileprivate var low = [UInt8]()
        fileprivate var high = [UInt8]()
        fileprivate var lowHistogram = [Int](repeating: 0, count: 256)
        fileprivate var highHistogram = [Int](repeating: 0, count: 256)
        fileprivate var rowStart = [Int]()
        fileprivate var runStart = [Int](repeating: 0, count: 4096)
        fileprivate var runEnd = [Int](repeating: 0, count: 4096)
        fileprivate var runRow = [Int](repeating: 0, count: 4096)
        fileprivate var parent = [Int](repeating: 0, count: 4096)
        fileprivate var boxLeft = [Int](repeating: 0, count: 4096)
        fileprivate var boxTop = [Int](repeating: 0, count: 4096)
        fileprivate var boxRight = [Int](repeating: 0, count: 4096)
        fileprivate var boxBottom = [Int](repeating: 0, count: 4096)
        fileprivate var area = [Int](repeating: 0, count: 4096)
        public private(set) var passesRun = 0

        public init() {}

        fileprivate func ensurePixels(_ count: Int) {
            if low.count < count { low = [UInt8](repeating: 0, count: count) }
            if high.count < count { high = [UInt8](repeating: 0, count: count) }
        }

        fileprivate func ensureRows(_ height: Int) {
            if rowStart.count < height + 1 { rowStart = [Int](repeating: 0, count: height + 1) }
        }

        fileprivate func growRuns() {
            let size = runStart.count * 2
            runStart += [Int](repeating: 0, count: size - runStart.count)
            runEnd += [Int](repeating: 0, count: size - runEnd.count)
            runRow += [Int](repeating: 0, count: size - runRow.count)
            parent += [Int](repeating: 0, count: size - parent.count)
            boxLeft = [Int](repeating: 0, count: size)
            boxTop = [Int](repeating: 0, count: size)
            boxRight = [Int](repeating: 0, count: size)
            boxBottom = [Int](repeating: 0, count: size)
            area = [Int](repeating: 0, count: size)
        }

        fileprivate func resetPasses() { passesRun = 0 }

        fileprivate func countPass() { passesRun += 1 }
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
        workspace.ensurePixels(count)

        for i in 0..<256 {
            workspace.lowHistogram[i] = 0
            workspace.highHistogram[i] = 0
        }
        for i in 0..<count {
            let base = i * 4
            let r = rgba[base]
            let g = rgba[base + 1]
            let b = rgba[base + 2]
            let lo = Swift.min(r, Swift.min(g, b))
            let hi = Swift.max(r, Swift.max(g, b))
            workspace.low[i] = lo
            workspace.high[i] = hi
            if Int(hi) - Int(lo) < maxChroma { workspace.lowHistogram[Int(lo)] += 1 }
            workspace.highHistogram[Int(hi)] += 1
        }
        return workspace.low.withUnsafeBufferPointer { low in
            workspace.high.withUnsafeBufferPointer { high in
                detect(workspace, low, high, width, height)
            }
        }
    }

    public static func detect(
        luma: [UInt8],
        width: Int,
        height: Int,
        workspace: Workspace = Workspace()
    ) -> [Column] {
        guard width >= 2, height >= 2 else { return [] }
        let count = width * height
        guard luma.count >= count else { return [] }

        for i in 0..<256 { workspace.highHistogram[i] = 0 }
        for i in 0..<count { workspace.highHistogram[Int(luma[i])] += 1 }
        workspace.lowHistogram = workspace.highHistogram
        return luma.withUnsafeBufferPointer { pixels in
            detect(workspace, pixels, pixels, width, height)
        }
    }

    public static func refine(_ column: Column) -> Refined {
        refine(column.glyphs)
    }

    private static func detect(
        _ workspace: Workspace,
        _ low: UnsafeBufferPointer<UInt8>,
        _ high: UnsafeBufferPointer<UInt8>,
        _ width: Int,
        _ height: Int
    ) -> [Column] {
        workspace.ensureRows(height)
        workspace.resetPasses()
        var found = [Column]()
        for pass in 0..<passCount {
            found += collectColumns(workspace, low, high, width, height, pass: pass)
        }
        return deduplicate(found)
    }

    private static func collectColumns(
        _ workspace: Workspace,
        _ low: UnsafeBufferPointer<UInt8>,
        _ high: UnsafeBufferPointer<UInt8>,
        _ width: Int,
        _ height: Int,
        pass: Int
    ) -> [Column] {
        let bright = pass != darkPass
        let threshold = bright ? brightThresholds[pass] : darkThreshold
        let count = width * height

        var foreground = 0
        if bright {
            for v in threshold...255 { foreground += workspace.lowHistogram[v] }
        } else {
            for v in 0..<threshold { foreground += workspace.highHistogram[v] }
        }
        if foreground == 0 || Double(foreground) > Double(count) * maxForegroundFraction { return [] }

        workspace.countPass()
        let glyphs = labelGlyphs(workspace, low, high, width, height, bright: bright, threshold: threshold)
        if glyphs.count < minGlyphsPerColumn { return [] }
        return cluster(glyphs)
    }

    static func labelGlyphs(
        _ workspace: Workspace,
        _ low: UnsafeBufferPointer<UInt8>,
        _ high: UnsafeBufferPointer<UInt8>,
        _ width: Int,
        _ height: Int,
        bright: Bool,
        threshold: Int
    ) -> [Box] {
        workspace.ensureRows(height)
        var runs = 0

        @inline(__always) func isOn(_ index: Int) -> Bool {
            let lo = Int(low[index])
            let hi = Int(high[index])
            return bright ? (lo >= threshold && hi - lo < maxChroma) : (hi < threshold)
        }

        for y in 0..<height {
            workspace.rowStart[y] = runs
            let previousFrom = y > 0 ? workspace.rowStart[y - 1] : 0
            let previousTo = runs
            let rowOffset = y * width
            var j = previousFrom
            var x = 0
            while x < width {
                while x < width && !isOn(rowOffset + x) { x += 1 }
                if x >= width { break }
                let start = x
                while x < width && isOn(rowOffset + x) { x += 1 }

                if runs == workspace.runStart.count { workspace.growRuns() }
                let run = runs
                runs += 1
                workspace.runStart[run] = start
                workspace.runEnd[run] = x
                workspace.runRow[run] = y
                workspace.parent[run] = run

                while j < previousTo && workspace.runEnd[j] <= start { j += 1 }
                var k = j
                while k < previousTo && workspace.runStart[k] < x {
                    union(&workspace.parent, run, k)
                    k += 1
                }
            }
        }
        workspace.rowStart[height] = runs

        for i in 0..<runs where workspace.parent[i] == i {
            workspace.boxLeft[i] = workspace.runStart[i]
            workspace.boxRight[i] = workspace.runEnd[i]
            workspace.boxTop[i] = workspace.runRow[i]
            workspace.boxBottom[i] = workspace.runRow[i] + 1
            workspace.area[i] = workspace.runEnd[i] - workspace.runStart[i]
        }
        for i in 0..<runs where workspace.parent[i] != i {
            let root = find(&workspace.parent, i)
            if workspace.runStart[i] < workspace.boxLeft[root] { workspace.boxLeft[root] = workspace.runStart[i] }
            if workspace.runEnd[i] > workspace.boxRight[root] { workspace.boxRight[root] = workspace.runEnd[i] }
            if workspace.runRow[i] + 1 > workspace.boxBottom[root] { workspace.boxBottom[root] = workspace.runRow[i] + 1 }
            workspace.area[root] += workspace.runEnd[i] - workspace.runStart[i]
        }

        let longSide = Double(Swift.max(width, height))
        let minHeight = minHeightFraction * longSide
        let maxHeight = maxHeightFraction * longSide
        let minWidth = minWidthFraction * longSide
        let maxWidth = maxWidthFraction * longSide
        var glyphs = [Box]()
        for i in 0..<runs where workspace.parent[i] == i {
            let boxWidth = Double(workspace.boxRight[i] - workspace.boxLeft[i])
            let boxHeight = Double(workspace.boxBottom[i] - workspace.boxTop[i])
            if boxWidth < minWidth || boxWidth > maxWidth { continue }
            if boxHeight < minHeight || boxHeight > maxHeight { continue }
            let aspect = boxHeight / boxWidth
            if aspect < minAspect || aspect > maxAspect { continue }
            if Double(workspace.area[i]) / (boxWidth * boxHeight) < minFill { continue }
            glyphs.append(Box(
                left: workspace.boxLeft[i],
                top: workspace.boxTop[i],
                right: workspace.boxRight[i],
                bottom: workspace.boxBottom[i]
            ))
        }
        return glyphs
    }

    private static func find(_ parent: inout [Int], _ index: Int) -> Int {
        var i = index
        while parent[i] != i {
            parent[i] = parent[parent[i]]
            i = parent[i]
        }
        return i
    }

    private static func union(_ parent: inout [Int], _ a: Int, _ b: Int) {
        let rootA = find(&parent, a)
        let rootB = find(&parent, b)
        if rootA == rootB { return }
        if rootA < rootB { parent[rootB] = rootA } else { parent[rootA] = rootB }
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

        return open.filter { $0.count >= minGlyphsPerColumn }.map { Column(bounds: bounds(of: $0), glyphs: $0) }
    }

    private static func bounds(of glyphs: [Box]) -> Box {
        Box(
            left: glyphs.map { $0.left }.min()!,
            top: glyphs.map { $0.top }.min()!,
            right: glyphs.map { $0.right }.max()!,
            bottom: glyphs.map { $0.bottom }.max()!
        )
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

    private static func refine(_ detected: [Box]) -> Refined {
        let medianWidth = median(detected.map { $0.width })
        let medianHeight = median(detected.map { $0.height })

        var split = [Box]()
        for g in detected {
            let parts = Int((Double(g.height) / Double(medianHeight)).rounded())
            if parts < 2 || Double(g.height) <= Double(medianHeight) * mergedHeightRatio {
                split.append(g)
                continue
            }
            let step = Double(g.height) / Double(parts)
            for k in 0..<parts {
                split.append(Box(
                    left: g.left,
                    top: g.top + Int(Double(k) * step),
                    right: g.right,
                    bottom: g.top + Int(Double(k + 1) * step)
                ))
            }
        }

        var boxed = [Bool](repeating: false, count: split.count)
        var glyphs = [Box]()
        glyphs.reserveCapacity(split.count)
        for (i, g) in split.enumerated() {
            if Double(g.width) > Double(medianWidth) * boxedWidthRatio,
               Double(g.height) > Double(medianHeight) * boxedHeightRatio {
                boxed[i] = true
                let dx = (g.width - medianWidth) / 2
                let dy = (g.height - medianHeight) / 2
                glyphs.append(Box(left: g.left + dx, top: g.top + dy, right: g.right - dx, bottom: g.bottom - dy))
            } else {
                glyphs.append(g)
            }
        }

        var from = 0
        var to = glyphs.count
        while to - from > containerNumberLength && isOutlier(glyphs[from], medianWidth, medianHeight) { from += 1 }
        while to - from > containerNumberLength && isOutlier(glyphs[to - 1], medianWidth, medianHeight) { to -= 1 }
        let trimmed = Array(glyphs[from..<to])
        return Refined(glyphs: trimmed, boxed: Array(boxed[from..<to]), score: score(trimmed))
    }

    private static func isOutlier(_ glyph: Box, _ medianWidth: Int, _ medianHeight: Int) -> Bool {
        let width = Double(glyph.width)
        let height = Double(glyph.height)
        return width < Double(medianWidth) * outlierMinRatio || width > Double(medianWidth) * outlierMaxRatio ||
            height < Double(medianHeight) * outlierMinRatio || height > Double(medianHeight) * outlierMaxRatio
    }

    static func score(_ glyphs: [Box]) -> Double {
        if glyphs.count < 2 { return Double.greatestFiniteMagnitude }
        let medianWidth = Double(median(glyphs.map { $0.width }))
        let medianCentre = median(glyphs.map { $0.centerX })
        let offCentre = glyphs.filter { abs($0.centerX - medianCentre) > offCentreInWidths * medianWidth }.count
        return Double(abs(glyphs.count - containerNumberLength)) * scorePerMissingGlyph +
            pitchVariation(glyphs) +
            variation(glyphs.map { Double($0.height) }) +
            variation(glyphs.map { Double($0.width) }) +
            scoreOffCentreWeight * Double(offCentre) / Double(glyphs.count)
    }

    private static func pitchVariation(_ glyphs: [Box]) -> Double {
        if glyphs.count < 3 { return 0 }
        var pitches = [Double]()
        for i in 1..<glyphs.count { pitches.append(glyphs[i].centerY - glyphs[i - 1].centerY) }
        return variation(pitches)
    }

    private static func variation(_ values: [Double]) -> Double {
        if values.isEmpty { return 0 }
        let mean = values.reduce(0, +) / Double(values.count)
        if mean == 0 { return Double.greatestFiniteMagnitude }
        let squares = values.reduce(0) { $0 + ($1 - mean) * ($1 - mean) }
        return (squares / Double(values.count)).squareRoot() / abs(mean)
    }

    private static func median<T: Comparable>(_ values: [T]) -> T {
        let sorted = values.sorted()
        return sorted[sorted.count / 2]
    }
}
