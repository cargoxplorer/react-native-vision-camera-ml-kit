package com.rnvisioncameramlkit.stacked

import kotlin.math.max

object ReferenceLabeller {

    fun label(mask: BooleanArray, width: Int, height: Int): List<StackedTextDetector.Box> {
        val longSide = max(width, height)
        val minHeight = 0.006f * longSide
        val maxHeight = 0.10f * longSide
        val minWidth = 0.004f * longSide
        val maxWidth = 0.07f * longSide
        val count = width * height
        val queue = IntArray(count)
        val glyphs = mutableListOf<StackedTextDetector.Box>()
        for (start in 0 until count) {
            if (!mask[start]) continue
            mask[start] = false
            queue[0] = start
            var head = 0
            var tail = 1
            var left = width
            var right = 0
            var top = height
            var bottom = 0
            while (head < tail) {
                val index = queue[head++]
                val x = index % width
                val y = index / width
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
                if (x > 0 && mask[index - 1]) { mask[index - 1] = false; queue[tail++] = index - 1 }
                if (x + 1 < width && mask[index + 1]) { mask[index + 1] = false; queue[tail++] = index + 1 }
                if (y > 0 && mask[index - width]) { mask[index - width] = false; queue[tail++] = index - width }
                if (y + 1 < height && mask[index + width]) { mask[index + width] = false; queue[tail++] = index + width }
            }
            val boxWidth = right - left + 1
            val boxHeight = bottom - top + 1
            if (boxWidth < minWidth || boxWidth > maxWidth) continue
            if (boxHeight < minHeight || boxHeight > maxHeight) continue
            val aspect = boxHeight.toFloat() / boxWidth
            if (aspect < 0.8f || aspect > 6f) continue
            if (tail.toFloat() / (boxWidth * boxHeight) < 0.2f) continue
            glyphs += StackedTextDetector.Box(left, top, right + 1, bottom + 1)
        }
        return glyphs
    }

    fun mask(luma: ByteArray, count: Int, bright: Boolean, threshold: Int): BooleanArray =
        BooleanArray(count) {
            val v = luma[it].toInt() and 0xFF
            if (bright) v >= threshold else v < threshold
        }
}
