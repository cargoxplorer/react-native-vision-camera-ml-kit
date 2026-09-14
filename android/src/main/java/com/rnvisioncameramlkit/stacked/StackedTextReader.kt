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
import com.rnvisioncameramlkit.utils.ImageUtils
import com.rnvisioncameramlkit.utils.Logger
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class StackedTextReader {

    data class GlyphReading(val text: String, val box: Rect)

    data class StackedBlock(val text: String, val bounds: Rect, val glyphs: List<GlyphReading>)

    class Prepared(val strip: Bitmap, val bounds: Rect, val glyphBoxes: List<Rect>, val widths: IntArray)

    private val workspace = StackedTextDetector.Workspace()
    private var pixels = IntArray(0)
    private val reds = IntArray(COLOUR_SAMPLES * COLOUR_SAMPLES)
    private val greens = IntArray(COLOUR_SAMPLES * COLOUR_SAMPLES)
    private val blues = IntArray(COLOUR_SAMPLES * COLOUR_SAMPLES)

    fun read(source: Bitmap, recognizer: TextRecognizer, timeoutSeconds: Long): List<StackedBlock> {
        val started = System.currentTimeMillis()
        val scale = min(1f, STATIC_DETECTION_LONG_SIDE.toFloat() / max(source.width, source.height))
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
        Logger.performance("stacked.detect", System.currentTimeMillis() - started)
        Logger.debug("stacked.columns=${columns.size} ${columns.map { it.glyphs.size }} in ${detectionWidth}x${detectionHeight}")

        val prepared = prepare(columns, 1f / scale, source, 0, source.width, source.height)
        return recognize(prepared, recognizer, timeoutSeconds)
    }

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

    fun prepare(luma: ImageUtils.LumaPlane, source: Bitmap, rotationDegrees: Int): List<Prepared> {
        val started = System.currentTimeMillis()
        val columns = StackedTextDetector.detect(luma.bytes, luma.width, luma.height, workspace)
        Logger.performance("stacked.detect", System.currentTimeMillis() - started)
        Logger.debug("stacked.columns=${columns.size} ${columns.map { it.glyphs.size }} in ${luma.width}x${luma.height}, passes=${workspace.passesRun}")

        val upright = rotationDegrees % 180 == 0
        val uprightWidth = if (upright) source.width else source.height
        val uprightHeight = if (upright) source.height else source.width
        return prepare(columns, luma.step.toFloat(), source, rotationDegrees, uprightWidth, uprightHeight)
    }

    fun recognize(prepared: List<Prepared>, recognizer: TextRecognizer, timeoutSeconds: Long): List<StackedBlock> {
        try {
            return prepared.mapNotNull { readStrip(it, recognizer, timeoutSeconds) }
        } finally {
            prepared.forEach { it.strip.recycle() }
        }
    }

    private fun prepare(
        columns: List<StackedTextDetector.Column>,
        inverse: Float,
        source: Bitmap,
        rotationDegrees: Int,
        uprightWidth: Int,
        uprightHeight: Int
    ): List<Prepared> {
        if (columns.isEmpty()) return emptyList()
        val started = System.currentTimeMillis()
        val ranked = columns
            .map { it to StackedTextDetector.refine(it) }
            .sortedBy { it.second.score }
            .take(MAX_COLUMNS_TO_READ)

        val prepared = ArrayList<Prepared>(ranked.size)
        try {
            for ((column, refined) in ranked) {
                val glyphBoxes = refined.glyphs.map { it.toSourceRect(inverse, uprightWidth, uprightHeight) }
                val tiles = glyphBoxes.indices.map { padded(glyphBoxes, it, refined.boxed[it], uprightWidth, uprightHeight) }
                val widths = IntArray(tiles.size) {
                    max(MIN_TILE_WIDTH, (tiles[it].width() * TILE_HEIGHT.toFloat() / tiles[it].height()).roundToInt())
                }
                val region = Rect(tiles.first())
                for (i in 1 until tiles.size) region.union(tiles[i])

                val crop = uprightCrop(source, region, rotationDegrees, uprightWidth, uprightHeight)
                try {
                    prepared += Prepared(
                        buildStrip(crop, tiles, region.left, region.top, widths),
                        column.bounds.toSourceRect(inverse, uprightWidth, uprightHeight),
                        glyphBoxes,
                        widths
                    )
                } finally {
                    if (crop !== source) crop.recycle()
                }
            }
        } catch (e: Exception) {
            prepared.forEach { it.strip.recycle() }
            throw e
        }
        Logger.performance("stacked.strip.build", System.currentTimeMillis() - started)
        return prepared
    }

    private fun readStrip(prepared: Prepared, recognizer: TextRecognizer, timeoutSeconds: Long): StackedBlock? {
        val started = System.currentTimeMillis()
        val text: Text
        try {
            text = Tasks.await(
                recognizer.process(InputImage.fromBitmap(prepared.strip, 0)),
                timeoutSeconds,
                TimeUnit.SECONDS
            )
        } catch (e: Exception) {
            Logger.error("Stacked strip recognition failed", e)
            return null
        }
        Logger.performance("stacked.strip.mlkit", System.currentTimeMillis() - started)

        val lines = text.textBlocks.flatMap { it.lines }.sortedBy { it.boundingBox?.left ?: 0 }
        val reading = lines.joinToString(" ") { it.text }.trim()
        Logger.debug("Stacked strip read: '$reading'")
        if (reading.isEmpty()) return null

        return StackedBlock(reading, prepared.bounds, assignGlyphText(text, prepared.glyphBoxes, prepared.widths))
    }

    private fun uprightCrop(
        source: Bitmap,
        region: Rect,
        rotationDegrees: Int,
        uprightWidth: Int,
        uprightHeight: Int
    ): Bitmap {
        val inSource = toSourceCoordinates(region, rotationDegrees, uprightWidth, uprightHeight)
        val matrix = if (rotationDegrees % 360 == 0) null else Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(source, inSource.left, inSource.top, inSource.width(), inSource.height(), matrix, true)
    }

    private fun buildStrip(crop: Bitmap, tiles: List<Rect>, offsetX: Int, offsetY: Int, widths: IntArray): Bitmap {
        val strip = Bitmap.createBitmap(
            widths.sum() + TILE_GAP * (tiles.size + 1),
            TILE_HEIGHT + TILE_GAP * 2,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(strip)
        canvas.drawColor(medianColour(crop))
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val tile = Rect()
        var x = TILE_GAP
        for (i in tiles.indices) {
            tile.set(tiles[i])
            tile.offset(-offsetX, -offsetY)
            canvas.drawBitmap(crop, tile, Rect(x, TILE_GAP, x + widths[i], TILE_GAP + TILE_HEIGHT), paint)
            x += widths[i] + TILE_GAP
        }
        return strip
    }

    private fun assignGlyphText(text: Text, glyphBoxes: List<Rect>, widths: IntArray): List<GlyphReading> {
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

    private fun medianColour(crop: Bitmap): Int {
        val stepY = max(1, crop.height / COLOUR_SAMPLES)
        val stepX = max(1, crop.width / COLOUR_SAMPLES)
        var n = 0
        var y = 0
        while (y < crop.height && n < reds.size) {
            var x = 0
            while (x < crop.width && n < reds.size) {
                val colour = crop.getPixel(x, y)
                reds[n] = Color.red(colour)
                greens[n] = Color.green(colour)
                blues[n] = Color.blue(colour)
                n++
                x += stepX
            }
            y += stepY
        }
        if (n == 0) return Color.BLACK
        reds.sort(0, n)
        greens.sort(0, n)
        blues.sort(0, n)
        return Color.rgb(reds[n / 2], greens[n / 2], blues[n / 2])
    }

    private fun padded(all: List<Rect>, index: Int, boxed: Boolean, width: Int, height: Int): Rect {
        val box = all[index]
        val pad = if (boxed) 0 else (box.height() * GLYPH_PADDING).roundToInt()
        val gapUp = if (index > 0) box.top - all[index - 1].bottom else pad * 2
        val gapDown = if (index + 1 < all.size) all[index + 1].top - box.bottom else pad * 2
        val padY = min(pad, min(max(0, gapUp / 2), max(0, gapDown / 2)))
        val left = (box.left - pad).coerceIn(0, width - 1)
        val top = (box.top - padY).coerceIn(0, height - 1)
        return Rect(
            left,
            top,
            (box.right + pad).coerceIn(left + 1, width),
            (box.bottom + padY).coerceIn(top + 1, height)
        )
    }

    private fun StackedTextDetector.Box.toSourceRect(inverse: Float, width: Int, height: Int): Rect {
        val left = (left * inverse).roundToInt().coerceIn(0, width - 1)
        val top = (top * inverse).roundToInt().coerceIn(0, height - 1)
        return Rect(
            left,
            top,
            (right * inverse).roundToInt().coerceIn(left + 1, width),
            (bottom * inverse).roundToInt().coerceIn(top + 1, height)
        )
    }

    companion object {
        const val FRAME_DETECTION_LONG_SIDE = 1920
        const val STATIC_DETECTION_LONG_SIDE = 1200
        const val MAX_COLUMNS_TO_READ = 6
        private const val TILE_HEIGHT = 96
        private const val TILE_GAP = 24
        private const val MIN_TILE_WIDTH = 8
        private const val GLYPH_PADDING = 0.20f
        private const val COLOUR_SAMPLES = 24
        private val ROTATION_SWEEP = intArrayOf(0, 90, 270, 180)

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
