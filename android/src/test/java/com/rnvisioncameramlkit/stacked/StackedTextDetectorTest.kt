package com.rnvisioncameramlkit.stacked

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

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

        fun detect(workspace: StackedTextDetector.Workspace = StackedTextDetector.Workspace()): List<StackedTextDetector.Column> =
            StackedTextDetector.detect(pixels, width, height, workspace)

        fun luma(): ByteArray = ByteArray(width * height) { (pixels[it] and 0xFF).toByte() }

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

    @Test
    fun `drops outliers at the column ends`() {
        val image = Image(WIDTH, HEIGHT, DARK_BACKGROUND)
        image.paintColumn(centerX = 120, value = WHITE_PAINT)
        val below = FIRST_GLYPH_TOP + CONTAINER_NUMBER_LENGTH * GLYPH_PITCH
        image.fill(117, below, 6, 6, WHITE_PAINT)
        image.fill(117, below + 12, 6, 6, WHITE_PAINT)

        val column = image.detect().single()
        val refined = StackedTextDetector.refine(column)

        assertEquals(CONTAINER_NUMBER_LENGTH + 2, column.glyphs.size)
        assertEquals(CONTAINER_NUMBER_LENGTH, refined.glyphs.size)
        assertEquals(column.glyphs.take(CONTAINER_NUMBER_LENGTH), refined.glyphs)
    }

    @Test
    fun `luma path equals ARGB path`() {
        val image = Image(WIDTH, HEIGHT, DARK_BACKGROUND)
        image.paintColumn(centerX = 70, value = WHITE_PAINT)
        image.paintColumn(centerX = 180, value = 200)

        assertEquals(image.detect(), StackedTextDetector.detect(image.luma(), WIDTH, HEIGHT))
    }

    @Test
    fun `labels components identically to the reference BFS`() {
        val random = Random(304)
        val workspace = StackedTextDetector.Workspace()
        repeat(200) {
            val width = 40 + random.nextInt(120)
            val height = 40 + random.nextInt(160)
            val luma = ByteArray(width * height) { if (random.nextFloat() < 0.35f) 255.toByte() else 0 }
            repeat(random.nextInt(8)) {
                val boxWidth = 1 + random.nextInt(12)
                val boxHeight = 1 + random.nextInt(16)
                val left = random.nextInt(width - boxWidth)
                val top = random.nextInt(height - boxHeight)
                for (y in top until top + boxHeight) {
                    for (x in left until left + boxWidth) luma[y * width + x] = 255.toByte()
                }
            }
            assertLabelsMatch(workspace, luma, width, height)
        }
    }

    private fun assertLabelsMatch(workspace: StackedTextDetector.Workspace, luma: ByteArray, width: Int, height: Int) {
        for ((bright, threshold) in listOf(true to 180, true to 210, true to 150, false to 90)) {
            val mask = ReferenceLabeller.mask(luma, width * height, bright, threshold)
            val expected = ReferenceLabeller.label(mask, width, height)
            val actual = StackedTextDetector.labelGlyphs(workspace, luma, luma, width, height, bright, threshold)
            assertEquals("${width}x$height bright=$bright T=$threshold", expected, actual)
        }
    }

    @Test
    fun `keeps a narrow last glyph`() {
        val image = Image(WIDTH, HEIGHT, DARK_BACKGROUND)
        image.paintColumn(centerX = 120, value = WHITE_PAINT, count = CONTAINER_NUMBER_LENGTH - 1)
        image.fill(117, FIRST_GLYPH_TOP + (CONTAINER_NUMBER_LENGTH - 1) * GLYPH_PITCH, 6, GLYPH_HEIGHT, WHITE_PAINT)

        val column = image.detect().single()

        assertEquals(CONTAINER_NUMBER_LENGTH, column.glyphs.size)
        assertEquals(CONTAINER_NUMBER_LENGTH, StackedTextDetector.refine(column).glyphs.size)
    }
}
