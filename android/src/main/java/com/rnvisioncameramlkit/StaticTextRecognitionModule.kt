package com.rnvisioncameramlkit

import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
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
import com.rnvisioncameramlkit.utils.Logger
import java.io.IOException

/**
 * Static Text Recognition Module
 *
 * Provides text recognition for static images (from file URIs)
 * Complements the frame processor plugin for non-realtime use cases
 */
class StaticTextRecognitionModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "StaticTextRecognitionModule"

    /**
     * Recognize text from a static image
     *
     * @param options Map containing uri, language, and optional orientation
     * @param promise Promise to resolve with recognition result or reject with error
     */
    @ReactMethod
    fun recognizeText(options: ReadableMap, promise: Promise) {
        val startTime = System.currentTimeMillis()

        try {
            val uri = options.getString("uri")
            if (uri == null) {
                promise.reject("INVALID_URI", "URI is required")
                return
            }

            val language = options.getString("language") ?: "latin"
            val orientation = if (options.hasKey("orientation")) options.getInt("orientation") else 0

            Logger.debug("Recognizing text from static image: $uri (language: $language, orientation: $orientation)")

            // Create recognizer based on language
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

            val recognizer: TextRecognizer = TextRecognition.getClient(recognizerOptions)

            // Load image from URI
            val parsedUri = Uri.parse(uri)
            val image: InputImage = try {
                when {
                    uri.startsWith("file://") || uri.startsWith("content://") -> {
                        InputImage.fromFilePath(reactApplicationContext, parsedUri)
                    }
                    else -> {
                        // Try as file path
                        InputImage.fromFilePath(reactApplicationContext, Uri.parse("file://$uri"))
                    }
                }
            } catch (e: IOException) {
                Logger.error("Failed to load image from URI: $uri", e)
                promise.reject("IMAGE_LOAD_ERROR", "Failed to load image: ${e.message}", e)
                return
            }

            // Process image
            val task: Task<Text> = recognizer.process(image)

            try {
                val text: Text = Tasks.await(task)

                val processingTime = System.currentTimeMillis() - startTime
                Logger.performance("Static text recognition processing", processingTime)

                if (text.text.isEmpty()) {
                    Logger.debug("No text detected in static image")
                    promise.resolve(null)
                    return
                }

                Logger.debug("Text detected in static image: ${text.text.length} characters, ${text.textBlocks.size} blocks")

                val result = WritableNativeMap().apply {
                    putString("text", text.text)
                    putArray("blocks", processBlocks(text.textBlocks))
                }

                promise.resolve(result)

            } catch (e: Exception) {
                val processingTime = System.currentTimeMillis() - startTime
                Logger.error("Error during static text recognition", e)
                Logger.performance("Static text recognition processing (error)", processingTime)
                promise.reject("RECOGNITION_ERROR", "Text recognition failed: ${e.message}", e)
            } finally {
                recognizer.close()
            }

        } catch (e: Exception) {
            Logger.error("Unexpected error in static text recognition", e)
            promise.reject("UNEXPECTED_ERROR", "Unexpected error: ${e.message}", e)
        }
    }

    companion object {
        /**
         * Process text blocks (reuse from TextRecognitionPlugin)
         */
        private fun processBlocks(blocks: List<Text.TextBlock>): WritableNativeArray {
            val blockArray = WritableNativeArray()

            for (block in blocks) {
                val blockMap = WritableNativeMap().apply {
                    putString("text", block.text)
                    putMap("frame", processRect(block.boundingBox))
                    putArray("cornerPoints", processCornerPoints(block.cornerPoints))
                    putArray("lines", processLines(block.lines))

                    block.recognizedLanguage?.let { lang ->
                        putString("recognizedLanguage", lang)
                    }
                }
                blockArray.pushMap(blockMap)
            }

            return blockArray
        }

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
