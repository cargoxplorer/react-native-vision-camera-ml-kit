package com.rnvisioncameramlkit.stacked

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StackedTextDetectorTest {

    private companion object {
        const val WIDTH = 240
        const val HEIGHT = 640
        const val GLYPH_WIDTH = 18
        const val GLYPH_HEIGHT = 30
        const val GLYPH_PITCH = 40
        const val FIRST_GLYPH_TOP = 40
        const val CONTAINER_NUMBER_LENGTH = 11
        const val DARK_PAINT = 20
        const val DARK_BACKGROUND = 40
        const val LIGHT_BACKGROUND = 235
        const val WHITE_PAINT = 255
    }

    private class Image(val width: Int, val height: Int, background: Int) {
        val pixels = IntArray(width * height) { grey(background) }

        fun fill(left: Int, top: Int, boxWidth: Int, boxHeight: Int, value: Int) {
            val colour = grey(value)
            for (y in top until top + boxHeight) {
                for (x in left until left + boxWidth) {
                    pixels[y * width + x] = colour
                }
            }
        }

        fun paintColumn(centerX: Int, value: Int, count: Int = CONTAINER_NUMBER_LENGTH) {
            for (i in 0 until count) {
                fill(centerX - GLYPH_WIDTH / 2, FIRST_GLYPH_TOP + i * GLYPH_PITCH, GLYPH_WIDTH, GLYPH_HEIGHT, value)
            }
        }

        fun detect(): List<StackedTextDetector.Column> = StackedTextDetector.detect(pixels, width, height)

        private fun grey(value: Int) = (0xFF shl 24) or (value shl 16) or (value shl 8) or value
    }

    @Test
    fun `reads a column of bright paint top to bottom`() {
        val image = Image(WIDTH, HEIGHT, DARK_BACKGROUND)
        image.paintColumn(centerX = 120, value = WHITE_PAINT)

        val columns = image.detect()

        assertEquals(1, columns.size)
        val column = columns.single()
        assertEquals(CONTAINER_NUMBER_LENGTH, column.glyphs.size)
        val tops = column.glyphs.map { it.top }
        assertEquals(tops.sorted(), tops)
        assertEquals(FIRST_GLYPH_TOP, column.bounds.top)
        assertEquals(120 - GLYPH_WIDTH / 2, column.bounds.left)
    }

    @Test
    fun `reads dark paint on a light door`() {
        val image = Image(WIDTH, HEIGHT, LIGHT_BACKGROUND)
        image.paintColumn(centerX = 120, value = DARK_PAINT)

        assertEquals(CONTAINER_NUMBER_LENGTH, image.detect().single().glyphs.size)
    }

    @Test
    fun `ignores specks, edges and oversized blobs`() {
        val image = Image(WIDTH, HEIGHT, DARK_BACKGROUND)
        image.paintColumn(centerX = 120, value = WHITE_PAINT)
        image.fill(10, 10, 2, 2, WHITE_PAINT)
        image.fill(20, 100, 1, 200, WHITE_PAINT)
        image.fill(170, 500, 60, 60, WHITE_PAINT)

        assertEquals(CONTAINER_NUMBER_LENGTH, image.detect().single().glyphs.size)
    }

    @Test
    fun `rejects a run shorter than eight glyphs`() {
        val image = Image(WIDTH, HEIGHT, DARK_BACKGROUND)
        image.paintColumn(centerX = 120, value = WHITE_PAINT, count = 7)

        assertTrue(image.detect().isEmpty())
    }

    @Test
    fun `separates two columns`() {
        val image = Image(WIDTH, HEIGHT, DARK_BACKGROUND)
        image.paintColumn(centerX = 70, value = WHITE_PAINT)
        image.paintColumn(centerX = 180, value = WHITE_PAINT)

        val columns = image.detect()

        assertEquals(2, columns.size)
        assertEquals(listOf(70f, 180f), columns.map { it.bounds.centerX }.sorted())
    }

    @Test
    fun `finds nothing in a blank frame or a horizontal row`() {
        assertTrue(Image(WIDTH, HEIGHT, DARK_BACKGROUND).detect().isEmpty())

        val row = Image(WIDTH, HEIGHT, DARK_BACKGROUND)
        for (i in 0 until 10) row.fill(5 + i * 22, 300, GLYPH_WIDTH, GLYPH_HEIGHT, WHITE_PAINT)
        assertTrue(row.detect().isEmpty())
    }
}
