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
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.rnvisioncameramlkit.utils.Logger
import java.io.IOException

/**
 * Static Barcode Scanner Module
 *
 * Provides barcode scanning for static images (from file URIs)
 */
class StaticBarcodeScannerModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "StaticBarcodeScannerModule"

    @ReactMethod
    fun scanBarcode(options: ReadableMap, promise: Promise) {
        val startTime = System.currentTimeMillis()

        try {
            val uri = options.getString("uri")
            if (uri == null) {
                promise.reject("INVALID_URI", "URI is required")
                return
            }

            Logger.debug("Scanning barcode from static image: $uri")

            // Parse formats if specified
            val formats = if (options.hasKey("formats")) {
                options.getArray("formats")?.toArrayList()
            } else {
                null
            }

            val scannerOptions = if (formats != null && formats.isNotEmpty()) {
                val barcodeFormats = formats.mapNotNull { formatString ->
                    parseBarcodeFormat(formatString.toString())
                }

                if (barcodeFormats.isEmpty()) {
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                        .build()
                } else {
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(
                            barcodeFormats.first(),
                            *barcodeFormats.drop(1).toIntArray()
                        )
                        .build()
                }
            } else {
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .build()
            }

            val scanner: BarcodeScanner = BarcodeScanning.getClient(scannerOptions)

            // Load image from URI
            val parsedUri = Uri.parse(uri)
            val image: InputImage = try {
                when {
                    uri.startsWith("file://") || uri.startsWith("content://") -> {
                        InputImage.fromFilePath(reactApplicationContext, parsedUri)
                    }
                    else -> {
                        InputImage.fromFilePath(reactApplicationContext, Uri.parse("file://$uri"))
                    }
                }
            } catch (e: IOException) {
                Logger.error("Failed to load image from URI: $uri", e)
                promise.reject("IMAGE_LOAD_ERROR", "Failed to load image: ${e.message}", e)
                return
            }

            // Process image
            val task: Task<List<Barcode>> = scanner.process(image)

            try {
                val barcodes: List<Barcode> = Tasks.await(task)

                val processingTime = System.currentTimeMillis() - startTime
                Logger.performance("Static barcode scanning processing", processingTime)

                if (barcodes.isEmpty()) {
                    Logger.debug("No barcodes detected in static image")
                    promise.resolve(null)
                    return
                }

                Logger.debug("Barcodes detected in static image: ${barcodes.size} barcode(s)")

                val result = WritableNativeMap().apply {
                    putArray("barcodes", processBarcodes(barcodes))
                }

                promise.resolve(result)

            } catch (e: Exception) {
                val processingTime = System.currentTimeMillis() - startTime
                Logger.error("Error during static barcode scanning", e)
                Logger.performance("Static barcode scanning processing (error)", processingTime)
                promise.reject("SCANNING_ERROR", "Barcode scanning failed: ${e.message}", e)
            } finally {
                scanner.close()
            }

        } catch (e: Exception) {
            Logger.error("Unexpected error in static barcode scanning", e)
            promise.reject("UNEXPECTED_ERROR", "Unexpected error: ${e.message}", e)
        }
    }

    companion object {
        // Reuse helper functions from BarcodeScanningPlugin
        private fun parseBarcodeFormat(format: String): Int? {
            return when (format.lowercase()) {
                "codabar" -> Barcode.FORMAT_CODABAR
                "code39" -> Barcode.FORMAT_CODE_39
                "code93" -> Barcode.FORMAT_CODE_93
                "code128" -> Barcode.FORMAT_CODE_128
                "ean8" -> Barcode.FORMAT_EAN_8
                "ean13" -> Barcode.FORMAT_EAN_13
                "itf" -> Barcode.FORMAT_ITF
                "upca" -> Barcode.FORMAT_UPC_A
                "upce" -> Barcode.FORMAT_UPC_E
                "aztec" -> Barcode.FORMAT_AZTEC
                "datamatrix" -> Barcode.FORMAT_DATA_MATRIX
                "pdf417" -> Barcode.FORMAT_PDF417
                "qrcode" -> Barcode.FORMAT_QR_CODE
                else -> {
                    Logger.warn("Unknown barcode format: $format")
                    null
                }
            }
        }

        private fun barcodeFormatToString(format: Int): String {
            return when (format) {
                Barcode.FORMAT_CODABAR -> "codabar"
                Barcode.FORMAT_CODE_39 -> "code39"
                Barcode.FORMAT_CODE_93 -> "code93"
                Barcode.FORMAT_CODE_128 -> "code128"
                Barcode.FORMAT_EAN_8 -> "ean8"
                Barcode.FORMAT_EAN_13 -> "ean13"
                Barcode.FORMAT_ITF -> "itf"
                Barcode.FORMAT_UPC_A -> "upca"
                Barcode.FORMAT_UPC_E -> "upce"
                Barcode.FORMAT_AZTEC -> "aztec"
                Barcode.FORMAT_DATA_MATRIX -> "dataMatrix"
                Barcode.FORMAT_PDF417 -> "pdf417"
                Barcode.FORMAT_QR_CODE -> "qrCode"
                else -> "unknown"
            }
        }

        private fun valueTypeToString(valueType: Int): String {
            return when (valueType) {
                Barcode.TYPE_TEXT -> "text"
                Barcode.TYPE_URL -> "url"
                Barcode.TYPE_EMAIL -> "email"
                Barcode.TYPE_PHONE -> "phone"
                Barcode.TYPE_SMS -> "sms"
                Barcode.TYPE_WIFI -> "wifi"
                Barcode.TYPE_GEO -> "geo"
                Barcode.TYPE_CONTACT_INFO -> "contact"
                Barcode.TYPE_CALENDAR_EVENT -> "calendarEvent"
                Barcode.TYPE_DRIVER_LICENSE -> "driverLicense"
                else -> "unknown"
            }
        }

        private fun processBarcodes(barcodes: List<Barcode>): WritableNativeArray {
            val barcodeArray = WritableNativeArray()

            for (barcode in barcodes) {
                val barcodeMap = WritableNativeMap().apply {
                    putString("rawValue", barcode.rawValue ?: "")
                    putString("displayValue", barcode.displayValue ?: "")
                    putString("format", barcodeFormatToString(barcode.format))
                    putString("valueType", valueTypeToString(barcode.valueType))
                    putMap("frame", processRect(barcode.boundingBox))
                    putArray("cornerPoints", processCornerPoints(barcode.cornerPoints))

                    // Add structured data (WiFi, URL, etc.) - same as plugin
                    // ... (same implementation as BarcodeScanningPlugin)
                }
                barcodeArray.pushMap(barcodeMap)
            }

            return barcodeArray
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
