package com.rnvisioncameramlkit.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.media.Image
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * Utility class for efficient image cloning and conversion.
 * 
 * Purpose: Clone camera Image data to Bitmap to release the original Image immediately.
 * This prevents "maxImages has already been acquired" errors when ML Kit processing
 * takes longer than the camera frame rate.
 * 
 * The ImageReader used by CameraX has a limited buffer (typically 6 images).
 * If we hold references to Image objects during ML Kit processing, the buffer fills up.
 * By copying to a Bitmap first, we can release the original Image immediately.
 */
object ImageUtils {

    // Reusable buffers to avoid per-frame allocations
    // ThreadLocal ensures thread safety when multiple frame processors run concurrently
    private val rgbBufferLocal = ThreadLocal<IntArray>()

    /**
     * Clone an Image to a Bitmap for independent processing.
     * 
     * This method extracts pixel data from the camera Image and creates a new Bitmap.
     * The original Image can be released immediately after this call returns.
     * 
     * @param image The camera Image (YUV_420_888 format)
     * @param rotationDegrees Rotation to apply (0, 90, 180, 270)
     * @return A new Bitmap containing the image data, or null on failure
     */
    fun imageToBitmap(image: Image, rotationDegrees: Int = 0): Bitmap? {
        return try {
            when (image.format) {
                ImageFormat.YUV_420_888 -> yuv420ToBitmap(image, rotationDegrees)
                ImageFormat.JPEG -> jpegToBitmap(image, rotationDegrees)
                else -> {
                    Logger.warn("Unsupported image format: ${image.format}, attempting YUV conversion")
                    yuv420ToBitmap(image, rotationDegrees)
                }
            }
        } catch (e: Exception) {
            val formatDetails = if (image.format == ImageFormat.YUV_420_888) {
                "pixelStride=${image.planes[1].pixelStride}, rowStride=${image.planes[1].rowStride}"
            } else {
                ""
            }
            Logger.error("Failed to convert image to bitmap (format=${image.format} ${formatDetails})", e)
            null
        }
    }

    /**
     * Convert YUV_420_888 image to grayscale Bitmap using Y plane only.
     *
     * ML Kit text recognition and barcode scanning only need luminance (Y plane),
     * not color information. This is much faster than full YUV->RGB conversion.
     *
     * Performance: ~3-5x faster than RGB conversion, uses less memory.
     */
    private fun yuv420ToBitmap(image: Image, rotationDegrees: Int): Bitmap? {
        val width = image.width
        val height = image.height
        val pixelCount = width * height

        // Get or create reusable buffer
        var grayBuffer = rgbBufferLocal.get()
        if (grayBuffer == null || grayBuffer.size < pixelCount) {
            grayBuffer = IntArray(pixelCount)
            rgbBufferLocal.set(grayBuffer)
        }

        // Extract Y plane (luminance) directly to grayscale pixels
        val yPlane = image.planes[0]
        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride

        var index = 0
        for (row in 0 until height) {
            val rowOffset = row * yRowStride
            for (col in 0 until width) {
                val y = yBuffer.get(rowOffset + col * yPixelStride).toInt() and 0xFF
                // Grayscale: R=G=B=Y, packed as ARGB
                grayBuffer[index++] = (0xFF shl 24) or (y shl 16) or (y shl 8) or y
            }
        }

        // Create bitmap from grayscale pixels
        var bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(grayBuffer, 0, width, 0, 0, width, height)

        // Apply rotation if needed
        if (rotationDegrees != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }
            bitmap = rotatedBitmap
        }

        return bitmap
    }

    /**
     * Convert JPEG Image to Bitmap (simple case).
     */
    private fun jpegToBitmap(image: Image, rotationDegrees: Int): Bitmap? {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        var bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        if (rotationDegrees != 0 && bitmap != null) {
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }
            bitmap = rotatedBitmap
        }

        return bitmap
    }

    // Decodes to an upright bitmap (EXIF applied), matching InputImage.fromFilePath coordinates.
    fun decodeBitmap(context: Context, uri: Uri, extraRotationDegrees: Int = 0): Bitmap? {
        return try {
            val decoded = context.contentResolver.openInputStream(uri).use { stream ->
                if (stream == null) null else BitmapFactory.decodeStream(stream)
            } ?: return null

            val exifDegrees = context.contentResolver.openInputStream(uri).use { stream ->
                if (stream == null) 0 else exifRotation(ExifInterface(stream))
            }

            rotate(decoded, exifDegrees + extraRotationDegrees)
        } catch (e: Exception) {
            Logger.error("Failed to decode bitmap from $uri", e)
            null
        }
    }

    // Recycles the input when a new bitmap is produced.
    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) return bitmap
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun exifRotation(exif: ExifInterface): Int =
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

    /**
     * Clear thread-local buffers to free memory.
     * Call this when the plugin is being destroyed.
     */
    fun clearBuffers() {
        rgbBufferLocal.remove()
    }
}

