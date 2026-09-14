package com.rnvisioncameramlkit.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.media.Image
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max

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

    class LumaPlane {
        var bytes = ByteArray(0)
            private set
        var width = 0
            internal set
        var height = 0
            internal set
        var step = 1
            internal set
        internal var rows = ByteArray(0)

        internal fun ensure(count: Int, rowBytes: Int) {
            if (bytes.size < count) bytes = ByteArray(count)
            if (rows.size < rowBytes) rows = ByteArray(rowBytes)
        }

        internal fun set(bytes: ByteArray, width: Int, height: Int, step: Int) {
            this.bytes = bytes
            this.width = width
            this.height = height
            this.step = step
        }
    }

    fun readLuma(image: Image, rotationDegrees: Int, longSide: Int, into: LumaPlane) {
        val plane = image.planes[0]
        val buffer = plane.buffer.duplicate()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val sourceWidth = image.width
        val sourceHeight = image.height
        val step = max(1, max(sourceWidth, sourceHeight) / longSide)
        val width = sourceWidth / step
        val height = sourceHeight / step
        val rowLength = (sourceWidth - 1) * pixelStride + 1
        into.ensure(width * height, rowLength * step)
        val out = into.bytes
        val rows = into.rows
        val rotation = (rotationDegrees % 360 + 360) % 360
        val samples = step * step
        val half = samples / 2

        for (dy in 0 until height) {
            for (s in 0 until step) {
                buffer.position((dy * step + s) * rowStride)
                buffer.get(rows, s * rowLength, rowLength)
            }
            var index: Int
            val strideX: Int
            when (rotation) {
                90 -> { index = height - 1 - dy; strideX = height }
                180 -> { index = (height - 1 - dy) * width + (width - 1); strideX = -1 }
                270 -> { index = (width - 1) * height + dy; strideX = -height }
                else -> { index = dy * width; strideX = 1 }
            }
            if (step == 1 && pixelStride == 1) {
                for (dx in 0 until width) {
                    out[index] = rows[dx]
                    index += strideX
                }
            } else if (step == 2 && pixelStride == 1) {
                var offset = 0
                for (dx in 0 until width) {
                    val sum = (rows[offset].toInt() and 0xFF) + (rows[offset + 1].toInt() and 0xFF) +
                        (rows[rowLength + offset].toInt() and 0xFF) + (rows[rowLength + offset + 1].toInt() and 0xFF)
                    out[index] = ((sum + 2) shr 2).toByte()
                    index += strideX
                    offset += 2
                }
            } else {
                for (dx in 0 until width) {
                    var sum = 0
                    val offset = dx * step * pixelStride
                    for (s in 0 until step) {
                        val rowOffset = s * rowLength + offset
                        for (t in 0 until step) sum += rows[rowOffset + t * pixelStride].toInt() and 0xFF
                    }
                    out[index] = ((sum + half) / samples).toByte()
                    index += strideX
                }
            }
        }

        into.width = if (rotation % 180 == 0) width else height
        into.height = if (rotation % 180 == 0) height else width
        into.step = step
    }

    fun clearBuffers() {
        rgbBufferLocal.remove()
    }
}

