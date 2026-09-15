//
//  StackedTextBlocks.swift
//  react-native-vision-camera-ml-kit
//

import Foundation
import CoreGraphics
import MLKitTextRecognition

enum TextLayout: String {
    case horizontal
    case stacked
    case auto

    static func from(_ value: Any?) -> TextLayout {
        guard let raw = value as? String else { return .horizontal }
        guard let layout = TextLayout(rawValue: raw.lowercased()) else {
            Logger.warn("Unknown textLayout '\(raw)', defaulting to horizontal")
            return .horizontal
        }
        return layout
    }

    func shouldReadStacked(_ horizontalPass: Text?) -> Bool {
        switch self {
        case .horizontal: return false
        case .stacked: return true
        case .auto:
            guard let text = horizontalPass else { return true }
            return text.blocks.allSatisfy { $0.lines.isEmpty }
        }
    }
}

enum StackedTextBlocks {

    static func toDictionary(_ block: StackedTextReader.StackedBlock) -> [String: Any] {
        let line: [String: Any] = [
            "text": block.text,
            "frame": frame(of: block.bounds),
            "cornerPoints": cornerPoints(of: block.bounds),
            "elements": elements(of: block.glyphs)
        ]

        return [
            "text": block.text,
            "frame": frame(of: block.bounds),
            "cornerPoints": cornerPoints(of: block.bounds),
            "lines": [line],
            "stacked": true
        ]
    }

    static func combineText(_ recognized: String, _ blocks: [StackedTextReader.StackedBlock]) -> String {
        if blocks.isEmpty { return recognized }
        let stacked = blocks.map { $0.text }.joined(separator: "\n")
        return recognized.isEmpty ? stacked : recognized + "\n" + stacked
    }

    private static func elements(of glyphs: [StackedTextReader.GlyphReading]) -> [[String: Any]] {
        return glyphs.map { glyph in
            [
                "text": glyph.text,
                "frame": frame(of: glyph.box),
                "cornerPoints": cornerPoints(of: glyph.box)
            ]
        }
    }

    private static func frame(of rect: CGRect) -> [String: CGFloat] {
        return [
            "x": rect.midX,
            "y": rect.midY,
            "width": rect.width,
            "height": rect.height
        ]
    }

    private static func cornerPoints(of rect: CGRect) -> [[String: Int]] {
        return [
            ["x": Int(rect.minX), "y": Int(rect.minY)],
            ["x": Int(rect.maxX), "y": Int(rect.minY)],
            ["x": Int(rect.maxX), "y": Int(rect.maxY)],
            ["x": Int(rect.minX), "y": Int(rect.maxY)]
        ]
    }
}
