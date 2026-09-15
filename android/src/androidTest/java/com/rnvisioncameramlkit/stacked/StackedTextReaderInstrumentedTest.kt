package com.rnvisioncameramlkit.stacked

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.rnvisioncameramlkit.utils.ImageUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StackedTextReaderInstrumentedTest {

    private companion object {
        const val CONTAINER_NUMBER = "CAAU9314819"
        const val WIDTH = 900
        const val HEIGHT = 1600
        val DOOR_COLOUR = Color.rgb(38, 44, 52)
    }

    private lateinit var recognizer: TextRecognizer
    private lateinit var reader: StackedTextReader

    @Before
    fun setUp() {
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        reader = StackedTextReader()
    }

    @After
    fun tearDown() {
        recognizer.close()
    }

    @Test
    fun readsAContainerNumberPaintedAsAColumn() {
        val door = renderDoor(CONTAINER_NUMBER)
        try {
            assertReadsTheColumn(reader.read(door, recognizer, 30))
        } finally {
            door.recycle()
        }
    }

    @Test
    fun readsAColumnFromALumaPlaneOfARotatedFrame() {
        val door = renderDoor(CONTAINER_NUMBER)
        val lying = StackedTextReader.rotatedCopy(door, 270)
        try {
            val luma = ImageUtils.LumaPlane()
            luma.set(halvedLuma(door), door.width / 2, door.height / 2, 2)

            val upright = reader.recognize(reader.prepare(luma, door, 0), recognizer, 30)
            val rotated = reader.recognize(reader.prepare(luma, lying, 90), recognizer, 30)

            assertReadsTheColumn(upright)
            assertReadsTheColumn(rotated)
            assertEquals(upright.single().bounds, rotated.single().bounds)
            assertEquals(upright.single().glyphs.map { it.box }, rotated.single().glyphs.map { it.box })
        } finally {
            door.recycle()
            lying.recycle()
        }
    }

    @Test
    fun findsNothingOnABlankDoor() {
        val blank = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            blank.eraseColor(DOOR_COLOUR)
            assertTrue(reader.read(blank, recognizer, 30).isEmpty())
        } finally {
            blank.recycle()
        }
    }

    private fun assertReadsTheColumn(blocks: List<StackedTextReader.StackedBlock>) {
        assertEquals(1, blocks.size)
        val block = blocks.single()
        assertEquals(CONTAINER_NUMBER, block.text.filter { it.isLetterOrDigit() }.uppercase())
        assertEquals(CONTAINER_NUMBER.length, block.glyphs.size)
        block.glyphs.forEach { assertTrue(block.bounds.contains(it.box)) }
    }

    private fun halvedLuma(bitmap: Bitmap): ByteArray {
        val width = bitmap.width / 2
        val height = bitmap.height / 2
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val luma = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = 2 * y * bitmap.width + 2 * x
                val sum = grey(pixels[i]) + grey(pixels[i + 1]) + grey(pixels[i + bitmap.width]) + grey(pixels[i + bitmap.width + 1])
                luma[y * width + x] = ((sum + 2) shr 2).toByte()
            }
        }
        return luma
    }

    private fun grey(colour: Int): Int = (Color.red(colour) * 299 + Color.green(colour) * 587 + Color.blue(colour) * 114) / 1000

    private fun renderDoor(number: String): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(DOOR_COLOUR)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 96f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        number.forEachIndexed { index, character ->
            canvas.drawText(character.toString(), 450f, 220f + index * 120f, paint)
        }
        return bitmap
    }
}
