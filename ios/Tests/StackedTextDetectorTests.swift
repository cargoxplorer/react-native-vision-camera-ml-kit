//
//  StackedTextDetectorTests.swift
//  react-native-vision-camera-ml-kit
//

import XCTest
@testable import react_native_vision_camera_mlkit_plugin

final class StackedTextDetectorTests: XCTestCase {

    private let width = 240
    private let height = 640
    private let glyphWidth = 18
    private let glyphHeight = 30
    private let glyphPitch = 40
    private let firstGlyphTop = 40
    private let containerNumberLength = 11
    private let darkPaint = 20
    private let darkBackground = 40
    private let lightBackground = 235
    private let whitePaint = 255

    private struct Image {
        let width: Int
        let height: Int
        var rgba: [UInt8]

        init(width: Int, height: Int, background: Int) {
            self.width = width
            self.height = height
            var buffer = [UInt8](repeating: 255, count: width * height * 4)
            for i in 0..<(width * height) {
                buffer[i * 4] = UInt8(background)
                buffer[i * 4 + 1] = UInt8(background)
                buffer[i * 4 + 2] = UInt8(background)
            }
            self.rgba = buffer
        }

        mutating func fill(_ left: Int, _ top: Int, _ boxWidth: Int, _ boxHeight: Int, _ value: Int) {
            for y in top..<(top + boxHeight) {
                for x in left..<(left + boxWidth) {
                    let base = (y * width + x) * 4
                    rgba[base] = UInt8(value)
                    rgba[base + 1] = UInt8(value)
                    rgba[base + 2] = UInt8(value)
                }
            }
        }

        func detect() -> [StackedTextDetector.Column] {
            StackedTextDetector.detect(rgba: rgba, width: width, height: height)
        }
    }

    private func paintColumn(_ image: inout Image, centerX: Int, value: Int, count: Int? = nil) {
        for i in 0..<(count ?? containerNumberLength) {
            image.fill(centerX - glyphWidth / 2, firstGlyphTop + i * glyphPitch, glyphWidth, glyphHeight, value)
        }
    }

    func testReadsAColumnOfBrightPaintTopToBottom() throws {
        var image = Image(width: width, height: height, background: darkBackground)
        paintColumn(&image, centerX: 120, value: whitePaint)

        let columns = image.detect()

        XCTAssertEqual(columns.count, 1)
        let column = try XCTUnwrap(columns.first)
        XCTAssertEqual(column.glyphs.count, containerNumberLength)
        let tops = column.glyphs.map { $0.top }
        XCTAssertEqual(tops, tops.sorted())
        XCTAssertEqual(column.bounds.top, firstGlyphTop)
        XCTAssertEqual(column.bounds.left, 120 - glyphWidth / 2)
    }

    func testReadsDarkPaintOnALightDoor() {
        var image = Image(width: width, height: height, background: lightBackground)
        paintColumn(&image, centerX: 120, value: darkPaint)

        XCTAssertEqual(image.detect().first?.glyphs.count, containerNumberLength)
    }

    func testIgnoresSpecksEdgesAndOversizedBlobs() {
        var image = Image(width: width, height: height, background: darkBackground)
        paintColumn(&image, centerX: 120, value: whitePaint)
        image.fill(10, 10, 2, 2, whitePaint)
        image.fill(20, 100, 1, 200, whitePaint)
        image.fill(170, 500, 60, 60, whitePaint)

        let columns = image.detect()

        XCTAssertEqual(columns.count, 1)
        XCTAssertEqual(columns.first?.glyphs.count, containerNumberLength)
    }

    func testRejectsARunShorterThanEightGlyphs() {
        var image = Image(width: width, height: height, background: darkBackground)
        paintColumn(&image, centerX: 120, value: whitePaint, count: 7)

        XCTAssertTrue(image.detect().isEmpty)
    }

    func testSeparatesTwoColumns() {
        var image = Image(width: width, height: height, background: darkBackground)
        paintColumn(&image, centerX: 70, value: whitePaint)
        paintColumn(&image, centerX: 180, value: whitePaint)

        let columns = image.detect()

        XCTAssertEqual(columns.count, 2)
        XCTAssertEqual(columns.map { $0.bounds.centerX }.sorted(), [70.0, 180.0])
    }

    func testFindsNothingInABlankFrameOrAHorizontalRow() {
        XCTAssertTrue(Image(width: width, height: height, background: darkBackground).detect().isEmpty)

        var row = Image(width: width, height: height, background: darkBackground)
        for i in 0..<10 {
            row.fill(5 + i * 22, 300, glyphWidth, glyphHeight, whitePaint)
        }
        XCTAssertTrue(row.detect().isEmpty)
    }
}
