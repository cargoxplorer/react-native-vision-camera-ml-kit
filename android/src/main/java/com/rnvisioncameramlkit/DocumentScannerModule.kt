package com.rnvisioncameramlkit

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableNativeArray
import com.facebook.react.bridge.WritableNativeMap
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.rnvisioncameramlkit.utils.Logger

/**
 * Document Scanner Module
 *
 * Provides ML Kit Document Scanner functionality
 * Note: This launches Google's document scanner UI, not a frame processor
 */
class DocumentScannerModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext), ActivityEventListener {

    private var scannerPromise: Promise? = null
    private val DOCUMENT_SCAN_REQUEST_CODE = 9001

    init {
        reactContext.addActivityEventListener(this)
    }

    override fun getName(): String = "DocumentScannerModule"

    /**
     * Scan documents using ML Kit Document Scanner UI
     *
     * @param options Map containing mode, pageLimit, galleryImportEnabled
     * @param promise Promise to resolve with scan result or reject with error
     */
    @ReactMethod
    fun scanDocument(options: ReadableMap, promise: Promise) {
        val currentActivity = currentActivity

        if (currentActivity == null) {
            promise.reject("NO_ACTIVITY", "Activity is null")
            return
        }

        if (scannerPromise != null) {
            promise.reject("SCANNER_BUSY", "A document scan is already in progress")
            return
        }

        scannerPromise = promise

        try {
            // Parse scanner mode
            val mode = options.getString("mode") ?: "full"
            val scannerMode = when (mode.lowercase()) {
                "base" -> GmsDocumentScannerOptions.SCANNER_MODE_BASE
                "basewithfilter" -> GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER
                "full" -> GmsDocumentScannerOptions.SCANNER_MODE_FULL
                else -> {
                    Logger.warn("Unknown scanner mode: $mode, using FULL")
                    GmsDocumentScannerOptions.SCANNER_MODE_FULL
                }
            }

            // Parse page limit (default: 1)
            val pageLimit = if (options.hasKey("pageLimit")) {
                options.getInt("pageLimit")
            } else {
                1
            }

            // Parse gallery import enabled (default: true)
            val galleryImportEnabled = if (options.hasKey("galleryImportEnabled")) {
                options.getBoolean("galleryImportEnabled")
            } else {
                true
            }

            Logger.info("Launching document scanner: mode=$mode, pageLimit=$pageLimit, gallery=$galleryImportEnabled")

            // Build scanner options
            val scannerOptions = GmsDocumentScannerOptions.Builder()
                .setScannerMode(scannerMode)
                .setPageLimit(pageLimit)
                .setGalleryImportAllowed(galleryImportEnabled)
                .setResultFormats(
                    GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                    GmsDocumentScannerOptions.RESULT_FORMAT_PDF
                )
                .build()

            // Create scanner
            val scanner: GmsDocumentScanner = GmsDocumentScanning.getClient(scannerOptions)

            // Launch scanner
            scanner.getStartScanIntent(currentActivity)
                .addOnSuccessListener { intentSender: android.content.IntentSender ->
                    currentActivity.startIntentSenderForResult(
                        intentSender,
                        DOCUMENT_SCAN_REQUEST_CODE,
                        null,
                        0,
                        0,
                        0
                    )
                }
                .addOnFailureListener { e: Exception ->
                    Logger.error("Failed to start document scanner", e)
                    val errorMessage = "Failed to start scanner: ${e.message}"
                    scannerPromise?.reject("SCANNER_START_FAILED", errorMessage, e)
                    scannerPromise = null
                }

        } catch (e: Exception) {
            Logger.error("Error launching document scanner", e)
            scannerPromise?.reject("SCANNER_ERROR", "Scanner error: ${e.message}", e)
            scannerPromise = null
        }
    }

    override fun onActivityResult(
        activity: Activity?,
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        if (requestCode == DOCUMENT_SCAN_REQUEST_CODE && scannerPromise != null) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val result = GmsDocumentScanningResult.fromActivityResultIntent(data)

                if (result != null) {
                    try {
                        val pages = WritableNativeArray()
                        var pageNumber = 1

                        result.pages?.forEach { page ->
                            val pageMap = WritableNativeMap().apply {
                                putString("uri", page.imageUri?.toString() ?: "")
                                putInt("pageNumber", pageNumber)

                                // Add image dimensions if available
                                // Note: ML Kit doesn't provide original/processed size directly
                                // These would need to be read from the actual image file
                            }
                            pages.pushMap(pageMap)
                            pageNumber++
                        }

                        val resultMap = WritableNativeMap().apply {
                            putArray("pages", pages)
                            putInt("pageCount", result.pages?.size ?: 0)

                            // Add PDF URI if available
                            result.pdf?.let { pdf ->
                                putString("pdfUri", pdf.uri?.toString() ?: "")
                                putInt("pageCount", pdf.pageCount)
                            }
                        }

                        Logger.info("Document scan completed: ${result.pages?.size ?: 0} page(s)")
                        scannerPromise?.resolve(resultMap)

                    } catch (e: Exception) {
                        Logger.error("Error processing scan result", e)
                        scannerPromise?.reject("RESULT_PROCESSING_ERROR", "Error processing result: ${e.message}", e)
                    }
                } else {
                    Logger.warn("Document scan result is null")
                    scannerPromise?.resolve(null)
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Logger.info("Document scan cancelled by user")
                scannerPromise?.reject("SCAN_CANCELLED", "User cancelled the scan")
            } else {
                Logger.warn("Document scan failed with result code: $resultCode")
                scannerPromise?.reject("SCAN_FAILED", "Scan failed with result code: $resultCode")
            }

            scannerPromise = null
        }
    }

    override fun onNewIntent(intent: Intent?) {
        // Not used
    }
}
