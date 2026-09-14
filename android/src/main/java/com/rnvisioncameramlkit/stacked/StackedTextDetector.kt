package com.rnvisioncameramlkit.stacked

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object StackedTextDetector {

    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
        val centerX: Float get() = (left + right) / 2f
        val centerY: Float get() = (top + bottom) / 2f
    }

    data class Column(val bounds: Box, val glyphs: List<Box>)

    class Refined(val glyphs: List<Box>, val boxed: BooleanArray, val score: Float)

    private val BRIGHT_THRESHOLDS = intArrayOf(180, 210, 150)
    private const val DARK_THRESHOLD = 90
    private const val DARK_PASS = 3
    private const val PASS_COUNT = 4
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

    const val CONTAINER_NUMBER_LENGTH = 11
    private const val MERGED_HEIGHT_RATIO = 1.7f
    private const val BOXED_WIDTH_RATIO = 1.35f
    private const val BOXED_HEIGHT_RATIO = 1.12f
    private const val OUTLIER_MIN_RATIO = 0.6f
    private const val OUTLIER_MAX_RATIO = 1.6f
    private const val OFF_CENTRE_IN_WIDTHS = 0.5f
    private const val SCORE_PER_MISSING_GLYPH = 0.5f
    private const val SCORE_OFF_CENTRE_WEIGHT = 2f

    class Workspace {
        internal var low = ByteArray(0)
        internal var high = ByteArray(0)
        internal val lowHistogram = IntArray(256)
        internal val highHistogram = IntArray(256)
        internal var rowStart = IntArray(0)
        internal var runStart = IntArray(4096)
        internal var runEnd = IntArray(4096)
        internal var runRow = IntArray(4096)
        internal var parent = IntArray(4096)
        internal var boxLeft = IntArray(4096)
        internal var boxTop = IntArray(4096)
        internal var boxRight = IntArray(4096)
        internal var boxBottom = IntArray(4096)
        internal var area = IntArray(4096)
        var passesRun = 0
            internal set

        internal fun ensurePixels(count: Int) {
            if (low.size < count) low = ByteArray(count)
            if (high.size < count) high = ByteArray(count)
        }

        internal fun ensureRows(height: Int) {
            if (rowStart.size < height + 1) rowStart = IntArray(height + 1)
        }

        internal fun growRuns() {
            val size = runStart.size * 2
            runStart = runStart.copyOf(size)
            runEnd = runEnd.copyOf(size)
            runRow = runRow.copyOf(size)
            parent = parent.copyOf(size)
            boxLeft = IntArray(size)
            boxTop = IntArray(size)
            boxRight = IntArray(size)
            boxBottom = IntArray(size)
            area = IntArray(size)
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
        workspace.ensurePixels(count)

        val low = workspace.low
        val high = workspace.high
        val lowHistogram = workspace.lowHistogram
        val highHistogram = workspace.highHistogram
        lowHistogram.fill(0)
        highHistogram.fill(0)
        for (i in 0 until count) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val lo = min(r, min(g, b))
            val hi = max(r, max(g, b))
            low[i] = lo.toByte()
            high[i] = hi.toByte()
            if (hi - lo < MAX_CHROMA) lowHistogram[lo]++
            highHistogram[hi]++
        }
        return detect(workspace, low, high, width, height)
    }

    @JvmOverloads
    fun detect(
        luma: ByteArray,
        width: Int,
        height: Int,
        workspace: Workspace = Workspace()
    ): List<Column> {
        if (width < 2 || height < 2) return emptyList()
        val count = width * height
        require(luma.size >= count)

        val histogram = workspace.highHistogram
        histogram.fill(0)
        for (i in 0 until count) histogram[luma[i].toInt() and 0xFF]++
        histogram.copyInto(workspace.lowHistogram)
        return detect(workspace, luma, luma, width, height)
    }

    fun refine(column: Column): Refined = refine(column.glyphs)

    private fun detect(workspace: Workspace, low: ByteArray, high: ByteArray, width: Int, height: Int): List<Column> {
        workspace.ensureRows(height)
        workspace.passesRun = 0
        val found = ArrayList<Column>()
        for (pass in 0 until PASS_COUNT) collectColumns(workspace, low, high, width, height, pass, found)
        return deduplicate(found)
    }

    private fun collectColumns(
        workspace: Workspace,
        low: ByteArray,
        high: ByteArray,
        width: Int,
        height: Int,
        pass: Int,
        into: MutableList<Column>
    ) {
        val bright = pass != DARK_PASS
        val threshold = if (bright) BRIGHT_THRESHOLDS[pass] else DARK_THRESHOLD
        val count = width * height

        var foreground = 0
        if (bright) {
            for (v in threshold..255) foreground += workspace.lowHistogram[v]
        } else {
            for (v in 0 until threshold) foreground += workspace.highHistogram[v]
        }
        if (foreground == 0 || foreground > count * MAX_FOREGROUND_FRACTION) return

        workspace.passesRun++
        val glyphs = labelGlyphs(workspace, low, high, width, height, bright, threshold)
        if (glyphs.size < MIN_GLYPHS_PER_COLUMN) return
        into += cluster(glyphs)
    }

    internal fun labelGlyphs(
        workspace: Workspace,
        low: ByteArray,
        high: ByteArray,
        width: Int,
        height: Int,
        bright: Boolean,
        threshold: Int
    ): List<Box> {
        workspace.ensureRows(height)
        val rowStart = workspace.rowStart
        var runStart = workspace.runStart
        var runEnd = workspace.runEnd
        var runRow = workspace.runRow
        var parent = workspace.parent
        var runs = 0

        for (y in 0 until height) {
            rowStart[y] = runs
            val previousFrom = if (y > 0) rowStart[y - 1] else 0
            val previousTo = runs
            val rowOffset = y * width
            var j = previousFrom
            var x = 0
            while (x < width) {
                while (x < width && !isOn(low, high, rowOffset + x, bright, threshold)) x++
                if (x >= width) break
                val start = x
                while (x < width && isOn(low, high, rowOffset + x, bright, threshold)) x++

                if (runs == runStart.size) {
                    workspace.growRuns()
                    runStart = workspace.runStart
                    runEnd = workspace.runEnd
                    runRow = workspace.runRow
                    parent = workspace.parent
                }
                val run = runs++
                runStart[run] = start
                runEnd[run] = x
                runRow[run] = y
                parent[run] = run

                while (j < previousTo && runEnd[j] <= start) j++
                var k = j
                while (k < previousTo && runStart[k] < x) {
                    union(parent, run, k)
                    k++
                }
            }
        }
        rowStart[height] = runs

        val boxLeft = workspace.boxLeft
        val boxTop = workspace.boxTop
        val boxRight = workspace.boxRight
        val boxBottom = workspace.boxBottom
        val area = workspace.area
        for (i in 0 until runs) {
            if (parent[i] != i) continue
            boxLeft[i] = runStart[i]
            boxRight[i] = runEnd[i]
            boxTop[i] = runRow[i]
            boxBottom[i] = runRow[i] + 1
            area[i] = runEnd[i] - runStart[i]
        }
        for (i in 0 until runs) {
            if (parent[i] == i) continue
            val root = find(parent, i)
            if (runStart[i] < boxLeft[root]) boxLeft[root] = runStart[i]
            if (runEnd[i] > boxRight[root]) boxRight[root] = runEnd[i]
            if (runRow[i] + 1 > boxBottom[root]) boxBottom[root] = runRow[i] + 1
            area[root] += runEnd[i] - runStart[i]
        }

        val longSide = max(width, height)
        val minHeight = MIN_HEIGHT_FRACTION * longSide
        val maxHeight = MAX_HEIGHT_FRACTION * longSide
        val minWidth = MIN_WIDTH_FRACTION * longSide
        val maxWidth = MAX_WIDTH_FRACTION * longSide
        val glyphs = ArrayList<Box>()
        for (i in 0 until runs) {
            if (parent[i] != i) continue
            val boxWidth = boxRight[i] - boxLeft[i]
            val boxHeight = boxBottom[i] - boxTop[i]
            if (boxWidth < minWidth || boxWidth > maxWidth) continue
            if (boxHeight < minHeight || boxHeight > maxHeight) continue
            val aspect = boxHeight.toFloat() / boxWidth
            if (aspect < MIN_ASPECT || aspect > MAX_ASPECT) continue
            if (area[i].toFloat() / (boxWidth * boxHeight) < MIN_FILL) continue
            glyphs += Box(boxLeft[i], boxTop[i], boxRight[i], boxBottom[i])
        }
        return glyphs
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun isOn(low: ByteArray, high: ByteArray, index: Int, bright: Boolean, threshold: Int): Boolean {
        val lo = low[index].toInt() and 0xFF
        val hi = high[index].toInt() and 0xFF
        return if (bright) lo >= threshold && hi - lo < MAX_CHROMA else hi < threshold
    }

    private fun find(parent: IntArray, index: Int): Int {
        var i = index
        while (parent[i] != i) {
            parent[i] = parent[parent[i]]
            i = parent[i]
        }
        return i
    }

    private fun union(parent: IntArray, a: Int, b: Int) {
        val rootA = find(parent, a)
        val rootB = find(parent, b)
        if (rootA == rootB) return
        if (rootA < rootB) parent[rootB] = rootA else parent[rootA] = rootB
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
            Column(boundsOf(column), column.toList())
        }
    }

    private fun boundsOf(glyphs: List<Box>): Box {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var bottom = Int.MIN_VALUE
        for (glyph in glyphs) {
            if (glyph.left < left) left = glyph.left
            if (glyph.top < top) top = glyph.top
            if (glyph.right > right) right = glyph.right
            if (glyph.bottom > bottom) bottom = glyph.bottom
        }
        return Box(left, top, right, bottom)
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

    private fun refine(detected: List<Box>): Refined {
        val medianWidth = median(detected) { it.width }
        val medianHeight = median(detected) { it.height }

        val split = ArrayList<Box>(detected.size + 2)
        for (g in detected) {
            val parts = (g.height.toFloat() / medianHeight).roundToInt()
            if (parts < 2 || g.height <= medianHeight * MERGED_HEIGHT_RATIO) {
                split += g
                continue
            }
            val step = g.height.toFloat() / parts
            for (k in 0 until parts) {
                split += Box(g.left, g.top + (k * step).toInt(), g.right, g.top + ((k + 1) * step).toInt())
            }
        }

        val boxed = BooleanArray(split.size)
        val glyphs = ArrayList<Box>(split.size)
        for (i in split.indices) {
            val g = split[i]
            if (g.width > medianWidth * BOXED_WIDTH_RATIO && g.height > medianHeight * BOXED_HEIGHT_RATIO) {
                boxed[i] = true
                val dx = (g.width - medianWidth) / 2
                val dy = (g.height - medianHeight) / 2
                glyphs += Box(g.left + dx, g.top + dy, g.right - dx, g.bottom - dy)
            } else {
                glyphs += g
            }
        }

        var from = 0
        var to = glyphs.size
        while (to - from > CONTAINER_NUMBER_LENGTH && isOutlier(glyphs[from], medianWidth, medianHeight)) from++
        while (to - from > CONTAINER_NUMBER_LENGTH && isOutlier(glyphs[to - 1], medianWidth, medianHeight)) to--
        val trimmed = if (from == 0 && to == glyphs.size) glyphs else glyphs.subList(from, to)
        val trimmedBoxed = if (from == 0 && to == glyphs.size) boxed else boxed.copyOfRange(from, to)

        return Refined(trimmed, trimmedBoxed, score(trimmed))
    }

    private fun isOutlier(glyph: Box, medianWidth: Int, medianHeight: Int): Boolean =
        glyph.width < medianWidth * OUTLIER_MIN_RATIO || glyph.width > medianWidth * OUTLIER_MAX_RATIO ||
            glyph.height < medianHeight * OUTLIER_MIN_RATIO || glyph.height > medianHeight * OUTLIER_MAX_RATIO

    internal fun score(glyphs: List<Box>): Float {
        if (glyphs.size < 2) return Float.MAX_VALUE
        val medianWidth = median(glyphs) { it.width }
        var offCentre = 0
        val medianCentre = medianFloat(glyphs) { it.centerX }
        for (g in glyphs) if (abs(g.centerX - medianCentre) > OFF_CENTRE_IN_WIDTHS * medianWidth) offCentre++
        return abs(glyphs.size - CONTAINER_NUMBER_LENGTH) * SCORE_PER_MISSING_GLYPH +
            pitchVariation(glyphs) +
            variation(glyphs) { it.height.toFloat() } +
            variation(glyphs) { it.width.toFloat() } +
            SCORE_OFF_CENTRE_WEIGHT * offCentre / glyphs.size
    }

    private fun pitchVariation(glyphs: List<Box>): Float {
        if (glyphs.size < 3) return 0f
        val pitches = FloatArray(glyphs.size - 1) { glyphs[it + 1].centerY - glyphs[it].centerY }
        return variation(pitches)
    }

    private inline fun variation(glyphs: List<Box>, value: (Box) -> Float): Float =
        variation(FloatArray(glyphs.size) { value(glyphs[it]) })

    private fun variation(values: FloatArray): Float {
        if (values.isEmpty()) return 0f
        var sum = 0f
        for (v in values) sum += v
        val mean = sum / values.size
        if (mean == 0f) return Float.MAX_VALUE
        var squares = 0f
        for (v in values) squares += (v - mean) * (v - mean)
        return sqrt(squares / values.size) / abs(mean)
    }

    private inline fun median(glyphs: List<Box>, value: (Box) -> Int): Int {
        val sorted = IntArray(glyphs.size) { value(glyphs[it]) }
        sorted.sort()
        return sorted[sorted.size / 2]
    }

    private inline fun medianFloat(glyphs: List<Box>, value: (Box) -> Float): Float {
        val sorted = FloatArray(glyphs.size) { value(glyphs[it]) }
        sorted.sort()
        return sorted[sorted.size / 2]
    }
}
