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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// Needs a device or emulator with Google Play services.
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
            val blocks = reader.read(door, recognizer, 30)

            assertEquals(1, blocks.size)
            val block = blocks.single()
            assertEquals(CONTAINER_NUMBER, block.text.filter { it.isLetterOrDigit() }.uppercase())
            assertEquals(CONTAINER_NUMBER.length, block.glyphs.size)
            block.glyphs.forEach { assertTrue(block.bounds.contains(it.box)) }
        } finally {
            door.recycle()
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
