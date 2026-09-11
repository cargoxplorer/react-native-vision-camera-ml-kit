package com.rnvisioncameramlkit.stacked

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// Finds columns of upright characters (container numbers on doors) that ML Kit cannot read.
object StackedTextDetector {

    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
        val centerX: Float get() = (left + right) / 2f
    }

    data class Column(val bounds: Box, val glyphs: List<Box>)

    private val BRIGHT_THRESHOLDS = intArrayOf(180, 150, 210)
    private const val DARK_THRESHOLD = 90
    private const val MAX_CHROMA = 85
    private const val MAX_FOREGROUND_FRACTION = 0.40f
    private const val MIN_HEIGHT_FRACTION = 0.006f
    private const val MAX_HEIGHT_FRACTION = 0.10f
    private const val MIN_WIDTH_FRACTION = 0.004f
    private const val MAX_WIDTH_FRACTION = 0.07f
    private const val MIN_ASPECT = 0.8f
    private const val MAX_ASPECT = 6f
    private const val MIN_FILL = 0.2f
    const val MIN_GLYPHS_PER_COLUMN = 8
    private const val MAX_VERTICAL_GAP_IN_HEIGHTS = 3f
    private const val DUPLICATE_IOU = 0.55f

    // Scratch buffers reused across frames.
    class Workspace {
        internal var low = ByteArray(0)
        internal var high = ByteArray(0)
        internal var mask = BooleanArray(0)
        internal var queue = IntArray(0)

        internal fun ensure(size: Int) {
            if (low.size < size) low = ByteArray(size)
            if (high.size < size) high = ByteArray(size)
            if (mask.size < size) mask = BooleanArray(size)
            if (queue.size < size) queue = IntArray(size)
        }
    }

    @JvmOverloads
    fun detect(
        pixels: IntArray,
        width: Int,
        height: Int,
        workspace: Workspace = Workspace()
    ): List<Column> {
        if (width < 2 || height < 2) return emptyList()
        val count = width * height
        require(pixels.size >= count)
        workspace.ensure(count)

        val low = workspace.low
        val high = workspace.high
        for (i in 0 until count) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            low[i] = min(r, min(g, b)).toByte()
            high[i] = max(r, max(g, b)).toByte()
        }

        val longSide = max(width, height)
        val found = mutableListOf<Column>()
        for (threshold in BRIGHT_THRESHOLDS) {
            collectColumns(workspace, count, width, height, longSide, true, threshold, found)
        }
        collectColumns(workspace, count, width, height, longSide, false, DARK_THRESHOLD, found)

        return deduplicate(found)
    }

    private fun collectColumns(
        workspace: Workspace,
        count: Int,
        width: Int,
        height: Int,
        longSide: Int,
        bright: Boolean,
        threshold: Int,
        into: MutableList<Column>
    ) {
        val low = workspace.low
        val high = workspace.high
        val mask = workspace.mask

        var foreground = 0
        for (i in 0 until count) {
            val lo = low[i].toInt() and 0xFF
            val hi = high[i].toInt() and 0xFF
            val on = if (bright) lo >= threshold && hi - lo < MAX_CHROMA else hi < threshold
            mask[i] = on
            if (on) foreground++
        }
        // A threshold that swallows the background cannot yield glyphs.
        if (foreground == 0 || foreground > count * MAX_FOREGROUND_FRACTION) return

        val glyphs = components(workspace, count, width, height, longSide)
        if (glyphs.size < MIN_GLYPHS_PER_COLUMN) return
        into += cluster(glyphs)
    }

    // 4-connected labelling with an explicit queue; pixels are cleared on enqueue.
    private fun components(
        workspace: Workspace,
        count: Int,
        width: Int,
        height: Int,
        longSide: Int
    ): List<Box> {
        val mask = workspace.mask
        val queue = workspace.queue
        val minHeight = MIN_HEIGHT_FRACTION * longSide
        val maxHeight = MAX_HEIGHT_FRACTION * longSide
        val minWidth = MIN_WIDTH_FRACTION * longSide
        val maxWidth = MAX_WIDTH_FRACTION * longSide

        val glyphs = mutableListOf<Box>()
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
            if (aspect < MIN_ASPECT || aspect > MAX_ASPECT) continue
            if (tail.toFloat() / (boxWidth * boxHeight) < MIN_FILL) continue
            glyphs += Box(left, top, right + 1, bottom + 1)
        }
        return glyphs
    }

    private fun cluster(glyphs: List<Box>): List<Column> {
        val ordered = glyphs.sortedWith(compareBy({ it.top }, { it.left }))
        val open = mutableListOf<MutableList<Box>>()

        for (glyph in ordered) {
            var best: MutableList<Box>? = null
            var bestDistance = Float.MAX_VALUE
            for (column in open) {
                val last = column.last()
                val distance = abs(glyph.centerX - last.centerX)
                if (distance > max(glyph.width, last.width)) continue
                if (glyph.top - last.bottom > MAX_VERTICAL_GAP_IN_HEIGHTS * last.height) continue
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = column
                }
            }
            if (best != null) best += glyph else open += mutableListOf(glyph)
        }

        return open.filter { it.size >= MIN_GLYPHS_PER_COLUMN }.map { column ->
            var left = Int.MAX_VALUE
            var top = Int.MAX_VALUE
            var right = Int.MIN_VALUE
            var bottom = Int.MIN_VALUE
            for (glyph in column) {
                if (glyph.left < left) left = glyph.left
                if (glyph.top < top) top = glyph.top
                if (glyph.right > right) right = glyph.right
                if (glyph.bottom > bottom) bottom = glyph.bottom
            }
            Column(Box(left, top, right, bottom), column.toList())
        }
    }

    private fun deduplicate(columns: List<Column>): List<Column> {
        val kept = mutableListOf<Column>()
        val ranked = columns.sortedWith(
            compareByDescending<Column> { it.glyphs.size }
                .thenBy { it.bounds.top }
                .thenBy { it.bounds.left }
        )
        for (column in ranked) {
            if (kept.none { intersectionOverUnion(it.bounds, column.bounds) > DUPLICATE_IOU }) kept += column
        }
        return kept
    }

    private fun intersectionOverUnion(a: Box, b: Box): Float {
        val width = max(0, min(a.right, b.right) - max(a.left, b.left))
        val height = max(0, min(a.bottom, b.bottom) - max(a.top, b.top))
        val intersection = (width * height).toFloat()
        val union = a.width.toFloat() * a.height + b.width.toFloat() * b.height - intersection
        return if (union <= 0f) 0f else intersection / union
    }
}
