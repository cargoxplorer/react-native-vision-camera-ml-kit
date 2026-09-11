package com.rnvisioncameramlkit.stacked

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognizer
import com.rnvisioncameramlkit.utils.Logger
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Re-lays a detected column as a horizontal strip, reads it with ML Kit and maps frames back.
class StackedTextReader {

    data class GlyphReading(val text: String, val box: Rect)

    data class StackedBlock(val text: String, val bounds: Rect, val glyphs: List<GlyphReading>)

    private val workspace = StackedTextDetector.Workspace()
    private var pixels = IntArray(0)

    fun read(source: Bitmap, recognizer: TextRecognizer, timeoutSeconds: Long): List<StackedBlock> {
        val started = System.currentTimeMillis()
        val scale = min(1f, DETECTION_LONG_SIDE.toFloat() / max(source.width, source.height))
        val detectionWidth = max(1, (source.width * scale).roundToInt())
        val detectionHeight = max(1, (source.height * scale).roundToInt())

        val scaled = if (detectionWidth == source.width && detectionHeight == source.height) source
        else Bitmap.createScaledBitmap(source, detectionWidth, detectionHeight, true)

        val columns: List<StackedTextDetector.Column>
        try {
            val count = scaled.width * scaled.height
            if (pixels.size < count) pixels = IntArray(count)
            scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
            columns = StackedTextDetector.detect(pixels, scaled.width, scaled.height, workspace)
        } finally {
            if (scaled !== source) scaled.recycle()
        }

        Logger.performance("Stacked column detection", System.currentTimeMillis() - started)
        Logger.debug("Stacked detection: ${columns.size} column(s) ${columns.map { it.glyphs.size }} in ${detectionWidth}x${detectionHeight}")
        if (columns.isEmpty()) return emptyList()

        val inverse = 1f / scale
        return columns.mapNotNull { column ->
            readColumn(source, recognizer, timeoutSeconds, column, inverse)
        }
    }

    // rotationDegrees on InputImage does not affect the Latin recognizer, so pixels are rotated.
    fun readWithRotationFallback(
        source: Bitmap,
        recognizer: TextRecognizer,
        timeoutSeconds: Long
    ): List<StackedBlock> {
        for (degrees in ROTATION_SWEEP) {
            val rotated = rotatedCopy(source, degrees)
            try {
                val blocks = read(rotated, recognizer, timeoutSeconds)
                if (blocks.isEmpty()) continue
                if (degrees == 0) return blocks
                return blocks.map { block ->
                    StackedBlock(
                        block.text,
                        toSourceCoordinates(block.bounds, degrees, rotated.width, rotated.height),
                        block.glyphs.map {
                            GlyphReading(
                                it.text,
                                toSourceCoordinates(it.box, degrees, rotated.width, rotated.height)
                            )
                        }
                    )
                }
            } finally {
                if (rotated !== source) rotated.recycle()
            }
        }
        return emptyList()
    }

    private fun readColumn(
        source: Bitmap,
        recognizer: TextRecognizer,
        timeoutSeconds: Long,
        column: StackedTextDetector.Column,
        inverse: Float
    ): StackedBlock? {
        val (glyphs, boxed) = refineGlyphs(column.glyphs)
        val glyphBoxes = glyphs.map { it.toSourceRect(inverse, source) }
        val tiles = glyphBoxes.mapIndexed { i, box -> padded(box, glyphBoxes, i, boxed.contains(i), source) }
        val widths = tiles.map { max(MIN_TILE_WIDTH, (it.width() * TILE_HEIGHT.toFloat() / it.height()).roundToInt()) }

        val strip = buildStrip(source, tiles, widths)
        val text: Text
        try {
            text = Tasks.await(
                recognizer.process(InputImage.fromBitmap(strip, 0)),
                timeoutSeconds,
                TimeUnit.SECONDS
            )
        } catch (e: Exception) {
            Logger.error("Stacked strip recognition failed", e)
            return null
        } finally {
            strip.recycle()
        }

        val lines = text.textBlocks.flatMap { it.lines }.sortedBy { it.boundingBox?.left ?: 0 }
        val reading = lines.joinToString(" ") { it.text }.trim()
        Logger.debug("Stacked strip read: '$reading'")
        if (reading.isEmpty()) return null

        return StackedBlock(
            reading,
            column.bounds.toSourceRect(inverse, source),
            assignGlyphText(text, glyphBoxes, widths)
        )
    }

    private fun buildStrip(source: Bitmap, tiles: List<Rect>, widths: List<Int>): Bitmap {
        val strip = Bitmap.createBitmap(
            widths.sum() + TILE_GAP * (tiles.size + 1),
            TILE_HEIGHT + TILE_GAP * 2,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(strip)
        canvas.drawColor(medianColour(source, tiles))
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        var x = TILE_GAP
        for (i in tiles.indices) {
            canvas.drawBitmap(source, tiles[i], Rect(x, TILE_GAP, x + widths[i], TILE_GAP + TILE_HEIGHT), paint)
            x += widths[i] + TILE_GAP
        }
        return strip
    }

    // Assigns each symbol ML Kit read in the strip to the glyph whose tile it fell into.
    private fun assignGlyphText(
        text: Text,
        glyphBoxes: List<Rect>,
        widths: List<Int>
    ): List<GlyphReading> {
        val readings = Array(glyphBoxes.size) { StringBuilder() }
        val starts = IntArray(widths.size)
        var x = TILE_GAP
        for (i in widths.indices) {
            starts[i] = x
            x += widths[i] + TILE_GAP
        }

        for (block in text.textBlocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    for (symbol in element.symbols) {
                        val box = symbol.boundingBox ?: continue
                        val centre = box.exactCenterX()
                        val tile = starts.indices.lastOrNull { centre >= starts[it] } ?: continue
                        if (centre > starts[tile] + widths[tile]) continue
                        readings[tile].append(symbol.text)
                    }
                }
            }
        }

        return glyphBoxes.mapIndexed { i, box -> GlyphReading(readings[i].toString(), box) }
    }

    // Two characters stuck together become one tall glyph; the check-digit frame merges with its digit.
    private fun refineGlyphs(detected: List<StackedTextDetector.Box>): Pair<List<StackedTextDetector.Box>, Set<Int>> {
        val medianWidth = detected.map { it.width }.sorted()[detected.size / 2]
        val medianHeight = detected.map { it.height }.sorted()[detected.size / 2]
        val glyphs = detected.flatMap { g ->
            val parts = (g.height.toFloat() / medianHeight).roundToInt()
            if (parts < 2 || g.height <= medianHeight * MERGED_HEIGHT_RATIO) return@flatMap listOf(g)
            val step = g.height.toFloat() / parts
            (0 until parts).map { k ->
                StackedTextDetector.Box(g.left, g.top + (k * step).toInt(), g.right, g.top + ((k + 1) * step).toInt())
            }
        }
        val boxed = mutableSetOf<Int>()
        val refined = glyphs.mapIndexed { i, g ->
            if (g.width > medianWidth * BOXED_WIDTH_RATIO && g.height > medianHeight * BOXED_HEIGHT_RATIO) {
                boxed += i
                val dx = (g.width - medianWidth) / 2
                val dy = (g.height - medianHeight) / 2
                StackedTextDetector.Box(g.left + dx, g.top + dy, g.right - dx, g.bottom - dy)
            } else {
                g
            }
        }
        return refined to boxed
    }

    // Median rather than mean: the door dominates the area, the paint must not lighten the background.
    private fun medianColour(source: Bitmap, tiles: List<Rect>): Int {
        val bounds = Rect(tiles.first())
        tiles.drop(1).forEach { bounds.union(it) }
        val reds = ArrayList<Int>()
        val greens = ArrayList<Int>()
        val blues = ArrayList<Int>()
        val stepY = max(1, bounds.height() / COLOUR_SAMPLES)
        val stepX = max(1, bounds.width() / COLOUR_SAMPLES)
        var y = bounds.top
        while (y < bounds.bottom) {
            var x = bounds.left
            while (x < bounds.right) {
                val colour = source.getPixel(x, y)
                reds += Color.red(colour)
                greens += Color.green(colour)
                blues += Color.blue(colour)
                x += stepX
            }
            y += stepY
        }
        if (reds.isEmpty()) return Color.BLACK
        reds.sort(); greens.sort(); blues.sort()
        val mid = reds.size / 2
        return Color.rgb(reds[mid], greens[mid], blues[mid])
    }

    // Vertical padding stops halfway to the neighbouring glyph; a boxed digit gets none, or its frame returns.
    private fun padded(box: Rect, all: List<Rect>, index: Int, boxed: Boolean, source: Bitmap): Rect {
        val pad = if (boxed) 0 else (box.height() * GLYPH_PADDING).roundToInt()
        val gapUp = if (index > 0) box.top - all[index - 1].bottom else pad * 2
        val gapDown = if (index + 1 < all.size) all[index + 1].top - box.bottom else pad * 2
        val padY = min(pad, min(max(0, gapUp / 2), max(0, gapDown / 2)))
        val left = (box.left - pad).coerceIn(0, source.width - 1)
        val top = (box.top - padY).coerceIn(0, source.height - 1)
        return Rect(
            left,
            top,
            (box.right + pad).coerceIn(left + 1, source.width),
            (box.bottom + padY).coerceIn(top + 1, source.height)
        )
    }

    private fun StackedTextDetector.Box.toSourceRect(inverse: Float, source: Bitmap): Rect {
        val left = (left * inverse).roundToInt().coerceIn(0, source.width - 1)
        val top = (top * inverse).roundToInt().coerceIn(0, source.height - 1)
        return Rect(
            left,
            top,
            (right * inverse).roundToInt().coerceIn(left + 1, source.width),
            (bottom * inverse).roundToInt().coerceIn(top + 1, source.height)
        )
    }

    companion object {
        const val DETECTION_LONG_SIDE = 1200
        private const val TILE_HEIGHT = 96
        private const val TILE_GAP = 24
        private const val MIN_TILE_WIDTH = 8
        private const val GLYPH_PADDING = 0.20f
        private const val MERGED_HEIGHT_RATIO = 1.7f
        private const val BOXED_WIDTH_RATIO = 1.35f
        private const val BOXED_HEIGHT_RATIO = 1.12f
        private const val COLOUR_SAMPLES = 24
        private val ROTATION_SWEEP = intArrayOf(0, 90, 270, 180)

        // Returns the source itself for a whole turn; compare by identity before recycling.
        fun rotatedCopy(source: Bitmap, degrees: Int): Bitmap {
            if (degrees % 360 == 0) return source
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }

        fun toSourceCoordinates(rect: Rect, degrees: Int, rotatedWidth: Int, rotatedHeight: Int): Rect =
            when ((degrees % 360 + 360) % 360) {
                90 -> Rect(rect.top, rotatedWidth - rect.right, rect.bottom, rotatedWidth - rect.left)
                180 -> Rect(
                    rotatedWidth - rect.right,
                    rotatedHeight - rect.bottom,
                    rotatedWidth - rect.left,
                    rotatedHeight - rect.top
                )
                270 -> Rect(rotatedHeight - rect.bottom, rect.left, rotatedHeight - rect.top, rect.right)
                else -> Rect(rect)
            }
    }
}
