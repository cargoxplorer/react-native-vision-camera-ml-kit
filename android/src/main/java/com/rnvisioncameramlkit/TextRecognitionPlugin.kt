package com.rnvisioncameramlkit

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.media.Image
import com.facebook.react.bridge.WritableNativeArray
import com.facebook.react.bridge.WritableNativeMap
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.TextRecognizerOptionsInterface
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mrousavy.camera.frameprocessors.Frame
import com.mrousavy.camera.frameprocessors.FrameProcessorPlugin
import com.mrousavy.camera.frameprocessors.VisionCameraProxy
import com.rnvisioncameramlkit.stacked.StackedTextBlocks
import com.rnvisioncameramlkit.stacked.StackedTextReader
import com.rnvisioncameramlkit.stacked.TextLayout
import com.rnvisioncameramlkit.utils.ImageUtils
import com.rnvisioncameramlkit.utils.Logger
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class TextRecognitionPlugin(
    proxy: VisionCameraProxy,
    options: Map<String, Any>?
) : FrameProcessorPlugin(), AutoCloseable {

    private var recognizer: TextRecognizer
    private val textLayout: TextLayout
    private val async: Boolean
    private val stackedReader: StackedTextReader by lazy { StackedTextReader() }
    private val stackedExecutorDelegate = lazy {
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "stacked-text") }
    }
    private val stackedExecutor: ExecutorService by stackedExecutorDelegate
    private val processingExecutorDelegate = lazy {
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "text-recognition") }
    }
    private val processingExecutor: ExecutorService by processingExecutorDelegate
    private val luma = ImageUtils.LumaPlane()
    private var pendingResult: Future<HashMap<String, Any?>?>? = null

    private class Captured(val bitmap: Bitmap, val rotationDegrees: Int, val stacked: Boolean, val startTime: Long)

    init {
        val language = options?.get("language")?.toString() ?: "latin"
        Logger.info("Initializing text recognition with language: $language")

        val recognizerOptions: TextRecognizerOptionsInterface = when (language.lowercase()) {
            "chinese" -> ChineseTextRecognizerOptions.Builder().build()
            "devanagari" -> DevanagariTextRecognizerOptions.Builder().build()
            "japanese" -> JapaneseTextRecognizerOptions.Builder().build()
            "korean" -> KoreanTextRecognizerOptions.Builder().build()
            "latin", "default" -> TextRecognizerOptions.Builder().build()
            else -> {
                Logger.warn("Unknown language '$language', defaulting to Latin")
                TextRecognizerOptions.Builder().build()
            }
        }

        recognizer = TextRecognition.getClient(recognizerOptions)
        textLayout = TextLayout.from(options?.get("textLayout")?.toString())
        async = options?.get("async") == true
        Logger.info("Text recognition initialized successfully (textLayout: $textLayout, async: $async)")
    }

    override fun close() {
        try {
            ImageUtils.clearBuffers()
            if (processingExecutorDelegate.isInitialized()) processingExecutor.shutdown()
            if (stackedExecutorDelegate.isInitialized()) stackedExecutor.shutdown()
            recognizer.close()
            Logger.debug("Text recognizer resources cleaned up successfully")
        } catch (e: Exception) {
            Logger.error("Error cleaning up text recognizer resources", e)
        }
    }

    override fun callback(frame: Frame, arguments: Map<String, Any>?): Any? {
        val startTime = System.currentTimeMillis()
        try {
            if (!async) {
                val captured = capture(frame, startTime) ?: return null
                return process(captured)
            }

            val pending = pendingResult
            if (pending != null && !pending.isDone) return null
            pendingResult = null
            val finished = pending?.let { takeResult(it) }

            val captured = capture(frame, startTime) ?: return finished
            pendingResult = processingExecutor.submit(Callable { process(captured) })
            return finished
        } catch (e: Exception) {
            Logger.error("Error during text recognition", e)
            Logger.performance("Text recognition processing (error)", System.currentTimeMillis() - startTime)
            return null
        }
    }

    private fun takeResult(pending: Future<HashMap<String, Any?>?>): HashMap<String, Any?>? =
        try {
            pending.get()
        } catch (e: Exception) {
            Logger.error("Error during text recognition", e)
            null
        }

    private fun capture(frame: Frame, startTime: Long): Captured? {
        val mediaImage: Image = frame.image
        val rotationDegrees = frame.imageProxy.imageInfo.rotationDegrees

        if (Logger.isDebugEnabled()) {
            Logger.debug("Processing frame: ${frame.width}x${frame.height}, rotation: $rotationDegrees")
        }

        val clonedBitmap = ImageUtils.imageToBitmap(mediaImage, 0)
        if (clonedBitmap == null) {
            Logger.error("Failed to clone camera image to bitmap")
            return null
        }

        val stacked = textLayout != TextLayout.HORIZONTAL
        if (stacked) {
            try {
                ImageUtils.readLuma(mediaImage, rotationDegrees, StackedTextReader.FRAME_DETECTION_LONG_SIDE, luma)
            } catch (e: Exception) {
                clonedBitmap.recycle()
                throw e
            }
        }
        return Captured(clonedBitmap, rotationDegrees, stacked, startTime)
    }

    private fun process(captured: Captured): HashMap<String, Any?>? {
        val clonedBitmap = captured.bitmap
        val rotationDegrees = captured.rotationDegrees
        var pending: Future<List<StackedTextReader.Prepared>>? = null
        try {
            if (textLayout == TextLayout.STACKED) {
                pending = stackedExecutor.submit(Callable { stackedReader.prepare(luma, clonedBitmap, rotationDegrees) })
            }

            val image = InputImage.fromBitmap(clonedBitmap, rotationDegrees)
            val task: Task<Text> = recognizer.process(image)
            val text: Text = Tasks.await(task)

            val stackedBlocks = when {
                pending != null -> {
                    val prepared = awaitPrepared(pending)
                    pending = null
                    stackedReader.recognize(prepared, recognizer, STACKED_TIMEOUT_SECONDS)
                }
                captured.stacked && textLayout.shouldReadStacked(text) -> readStackedColumns(clonedBitmap, rotationDegrees)
                else -> emptyList()
            }

            Logger.performance("Text recognition processing", System.currentTimeMillis() - captured.startTime)

            if (text.text.isEmpty() && stackedBlocks.isEmpty()) {
                Logger.debug("No text detected in frame")
                return null
            }

            Logger.debug("Text detected: ${text.text.length} characters, ${text.textBlocks.size} blocks, ${stackedBlocks.size} stacked")

            val blocks = processBlocks(text.textBlocks)
            stackedBlocks.forEach { blocks.pushMap(StackedTextBlocks.toMap(it)) }

            val result = WritableNativeMap().apply {
                putString("text", StackedTextBlocks.combineText(text.text, stackedBlocks))
                putArray("blocks", blocks)
            }
            return result.toHashMap()
        } catch (e: Exception) {
            Logger.error("Error during text recognition", e)
            Logger.performance("Text recognition processing (error)", System.currentTimeMillis() - captured.startTime)
            return null
        } finally {
            pending?.let { awaitPrepared(it).forEach { prepared -> prepared.strip.recycle() } }
            clonedBitmap.recycle()
        }
    }

    private fun awaitPrepared(pending: Future<List<StackedTextReader.Prepared>>): List<StackedTextReader.Prepared> =
        try {
            pending.get()
        } catch (e: Exception) {
            Logger.error("Stacked text pass failed", e)
            emptyList()
        }

    private fun readStackedColumns(clonedBitmap: Bitmap, rotationDegrees: Int): List<StackedTextReader.StackedBlock> {
        return try {
            val prepared = stackedReader.prepare(luma, clonedBitmap, rotationDegrees)
            stackedReader.recognize(prepared, recognizer, STACKED_TIMEOUT_SECONDS)
        } catch (e: Exception) {
            Logger.error("Stacked text pass failed", e)
            emptyList()
        }
    }

    companion object {
        private const val STACKED_TIMEOUT_SECONDS = 5L

        /**
         * Process text blocks into React Native compatible format
         */
        private fun processBlocks(blocks: List<Text.TextBlock>): WritableNativeArray {
            val blockArray = WritableNativeArray()

            for (block in blocks) {
                val blockMap = WritableNativeMap().apply {
                    putString("text", block.text)
                    putMap("frame", processRect(block.boundingBox))
                    putArray("cornerPoints", processCornerPoints(block.cornerPoints))
                    putArray("lines", processLines(block.lines))

                    // Add language if recognized
                    block.recognizedLanguage?.let { lang ->
                        putString("recognizedLanguage", lang)
                    }

                    // Add confidence if available (ML Kit doesn't provide this for v2, but keeping for future)
                    // putDouble("confidence", block.confidence?.toDouble() ?: 0.0)
                }
                blockArray.pushMap(blockMap)
            }

            return blockArray
        }

        /**
         * Process text lines into React Native compatible format
         */
        private fun processLines(lines: List<Text.Line>): WritableNativeArray {
            val lineArray = WritableNativeArray()

            for (line in lines) {
                val lineMap = WritableNativeMap().apply {
                    putString("text", line.text)
                    putMap("frame", processRect(line.boundingBox))
                    putArray("cornerPoints", processCornerPoints(line.cornerPoints))
                    putArray("elements", processElements(line.elements))

                    line.recognizedLanguage?.let { lang ->
                        putString("recognizedLanguage", lang)
                    }
                }
                lineArray.pushMap(lineMap)
            }

            return lineArray
        }

        /**
         * Process text elements (words) into React Native compatible format
         */
        private fun processElements(elements: List<Text.Element>): WritableNativeArray {
            val elementArray = WritableNativeArray()

            for (element in elements) {
                val elementMap = WritableNativeMap().apply {
                    putString("text", element.text)
                    putMap("frame", processRect(element.boundingBox))
                    putArray("cornerPoints", processCornerPoints(element.cornerPoints))
                    putArray("symbols", processSymbols(element.symbols))

                    element.recognizedLanguage?.let { lang ->
                        putString("recognizedLanguage", lang)
                    }
                }
                elementArray.pushMap(elementMap)
            }

            return elementArray
        }

        /**
         * Process text symbols (characters) into React Native compatible format
         */
        private fun processSymbols(symbols: List<Text.Symbol>): WritableNativeArray {
            val symbolArray = WritableNativeArray()

            for (symbol in symbols) {
                val symbolMap = WritableNativeMap().apply {
                    putString("text", symbol.text)
                    putMap("frame", processRect(symbol.boundingBox))
                    putArray("cornerPoints", processCornerPoints(symbol.cornerPoints))

                    symbol.recognizedLanguage?.let { lang ->
                        putString("recognizedLanguage", lang)
                    }
                }
                symbolArray.pushMap(symbolMap)
            }

            return symbolArray
        }

        /**
         * Convert Android Rect to React Native format
         */
        private fun processRect(boundingBox: Rect?): WritableNativeMap {
            val rectMap = WritableNativeMap()

            boundingBox?.let { box ->
                rectMap.putDouble("x", box.exactCenterX().toDouble())
                rectMap.putDouble("y", box.exactCenterY().toDouble())
                rectMap.putInt("width", box.width())
                rectMap.putInt("height", box.height())
            }

            return rectMap
        }

        /**
         * Convert Android corner points to React Native format
         */
        private fun processCornerPoints(cornerPoints: Array<Point>?): WritableNativeArray {
            val pointsArray = WritableNativeArray()

            cornerPoints?.forEach { point ->
                val pointMap = WritableNativeMap().apply {
                    putInt("x", point.x)
                    putInt("y", point.y)
                }
                pointsArray.pushMap(pointMap)
            }

            return pointsArray
        }
    }
}
