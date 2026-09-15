package com.rnvisioncameramlkit.stacked

import android.graphics.Rect
import com.facebook.react.bridge.WritableNativeArray
import com.facebook.react.bridge.WritableNativeMap

object StackedTextBlocks {

    fun toMap(block: StackedTextReader.StackedBlock): WritableNativeMap {
        val line = WritableNativeMap().apply {
            putString("text", block.text)
            putMap("frame", frameOf(block.bounds))
            putArray("cornerPoints", cornerPointsOf(block.bounds))
            putArray("elements", elementsOf(block.glyphs))
        }

        return WritableNativeMap().apply {
            putString("text", block.text)
            putMap("frame", frameOf(block.bounds))
            putArray("cornerPoints", cornerPointsOf(block.bounds))
            putArray("lines", WritableNativeArray().apply { pushMap(line) })
            putBoolean("stacked", true)
        }
    }

    fun combineText(recognized: String, blocks: List<StackedTextReader.StackedBlock>): String {
        if (blocks.isEmpty()) return recognized
        val stacked = blocks.joinToString("\n") { it.text }
        return if (recognized.isEmpty()) stacked else recognized + "\n" + stacked
    }

    private fun elementsOf(glyphs: List<StackedTextReader.GlyphReading>): WritableNativeArray {
        val elements = WritableNativeArray()
        for (glyph in glyphs) {
            val symbols = WritableNativeArray()
            for (character in glyph.text) {
                symbols.pushMap(WritableNativeMap().apply {
                    putString("text", character.toString())
                    putMap("frame", frameOf(glyph.box))
                    putArray("cornerPoints", cornerPointsOf(glyph.box))
                })
            }
            elements.pushMap(WritableNativeMap().apply {
                putString("text", glyph.text)
                putMap("frame", frameOf(glyph.box))
                putArray("cornerPoints", cornerPointsOf(glyph.box))
                putArray("symbols", symbols)
            })
        }
        return elements
    }

    private fun frameOf(box: Rect): WritableNativeMap = WritableNativeMap().apply {
        putDouble("x", box.exactCenterX().toDouble())
        putDouble("y", box.exactCenterY().toDouble())
        putInt("width", box.width())
        putInt("height", box.height())
    }

    private fun cornerPointsOf(box: Rect): WritableNativeArray {
        val points = WritableNativeArray()
        for ((x, y) in listOf(
            box.left to box.top,
            box.right to box.top,
            box.right to box.bottom,
            box.left to box.bottom
        )) {
            points.pushMap(WritableNativeMap().apply {
                putInt("x", x)
                putInt("y", y)
            })
        }
        return points
    }
}
