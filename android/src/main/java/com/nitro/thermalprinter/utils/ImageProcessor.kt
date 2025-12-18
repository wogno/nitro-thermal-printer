package com.nitro.thermalprinter.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Image processor for thermal printer.
 * Handles image download, resize, and conversion to ESC/POS bitmap format.
 */
class ImageProcessor {

    companion object {
        private const val DEFAULT_THRESHOLD = 127
        private const val DEFAULT_MAX_SIZE = 200

        // ESC/POS commands
        val SELECT_BIT_IMAGE_MODE = byteArrayOf(0x1B, 0x2A, 33)
        val SET_LINE_SPACE_24 = byteArrayOf(0x1B, 0x33, 24)
        val SET_LINE_SPACE_32 = byteArrayOf(0x1B, 0x33, 32)
        val LINE_FEED = byteArrayOf(0x0A)
        val CENTER_ALIGN = byteArrayOf(0x1B, 0x61, 0x31)
        val LEFT_ALIGN = byteArrayOf(0x1B, 0x61, 0x00)
    }

    /**
     * Download a bitmap from URL
     */
    suspend fun downloadBitmap(url: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.doInput = true
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.connect()

                val inputStream = connection.inputStream
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                connection.disconnect()

                bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Decode bitmap from Base64 string
     */
    fun decodeBitmapFromBase64(base64: String): Bitmap? {
        return try {
            val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Resize bitmap for printing
     */
    fun resizeForPrinting(
        bitmap: Bitmap,
        targetWidth: Int = 0,
        targetHeight: Int = 0
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // Calculate new dimensions
        val newWidth: Int
        val newHeight: Int

        if (targetWidth > 0 && targetHeight > 0) {
            newWidth = targetWidth
            newHeight = targetHeight
        } else if (targetWidth > 0) {
            val ratio = targetWidth.toFloat() / width
            newWidth = targetWidth
            newHeight = (height * ratio).toInt()
        } else if (targetHeight > 0) {
            val ratio = targetHeight.toFloat() / height
            newHeight = targetHeight
            newWidth = (width * ratio).toInt()
        } else if (width > DEFAULT_MAX_SIZE || height > DEFAULT_MAX_SIZE) {
            val ratio = if (width > height) {
                DEFAULT_MAX_SIZE.toFloat() / width
            } else {
                DEFAULT_MAX_SIZE.toFloat() / height
            }
            newWidth = (width * ratio).toInt()
            newHeight = (height * ratio).toInt()
        } else {
            return bitmap
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Extract pixel data from bitmap
     * @returns 2D array of RGB values [row][column]
     */
    fun getPixels(bitmap: Bitmap, targetWidth: Int = 0, targetHeight: Int = 0): Array<IntArray> {
        val resized = resizeForPrinting(bitmap, targetWidth, targetHeight)
        val width = resized.width
        val height = resized.height

        return Array(height) { row ->
            IntArray(width) { col ->
                resized.getPixel(col, row)
            }
        }
    }

    /**
     * Check if a pixel should be printed (black) based on luminance
     */
    fun shouldPrintPixel(color: Int, threshold: Int = DEFAULT_THRESHOLD): Boolean {
        val alpha = Color.alpha(color)

        // Ignore transparent pixels
        if (alpha != 255) {
            return false
        }

        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)

        // Calculate luminance using standard formula
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

        return luminance < threshold
    }

    /**
     * Recollect a 24-pixel vertical slice for ESC/POS printing
     * @param y Starting y coordinate
     * @param x X coordinate
     * @param pixels 2D pixel array
     * @returns 3 bytes representing 24 vertical pixels
     */
    fun recollectSlice(y: Int, x: Int, pixels: Array<IntArray>): ByteArray {
        val slices = ByteArray(3)

        // Process 24 pixels (3 bytes × 8 bits each)
        for (i in 0 until 3) {
            var slice: Byte = 0
            for (b in 0 until 8) {
                val yy = y + (i * 8) + b
                if (yy >= pixels.size) continue

                val color = pixels[yy][x]
                val shouldPrint = shouldPrintPixel(color)
                if (shouldPrint) {
                    slice = (slice.toInt() or (1 shl (7 - b))).toByte()
                }
            }
            slices[i] = slice
        }

        return slices
    }

    /**
     * Convert bitmap to ESC/POS print commands
     */
    fun bitmapToEscPos(bitmap: Bitmap, targetWidth: Int = 0, targetHeight: Int = 0): ByteArray {
        val pixels = getPixels(bitmap, targetWidth, targetHeight)
        val output = mutableListOf<Byte>()

        // Set line spacing and alignment
        output.addAll(SET_LINE_SPACE_24.toList())
        output.addAll(CENTER_ALIGN.toList())

        // Process image in 24-pixel strips
        for (y in pixels.indices step 24) {
            val rowWidth = if (y < pixels.size) pixels[y].size else 0

            // Select bit image mode
            output.addAll(SELECT_BIT_IMAGE_MODE.toList())

            // Width bytes (nL, nH)
            output.add((rowWidth and 0xFF).toByte())
            output.add(((rowWidth shr 8) and 0xFF).toByte())

            // Send each column's 24-bit data
            for (x in 0 until rowWidth) {
                output.addAll(recollectSlice(y, x, pixels).toList())
            }

            // Line feed
            output.addAll(LINE_FEED.toList())
        }

        // Reset line spacing
        output.addAll(SET_LINE_SPACE_32.toList())
        output.addAll(LINE_FEED.toList())

        return output.toByteArray()
    }
}
