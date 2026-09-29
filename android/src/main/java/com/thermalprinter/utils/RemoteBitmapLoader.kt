package com.thermalprinter.utils

import android.graphics.Bitmap
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.target.Target
import com.thermalprinter.ApplicationContextProvider
import java.util.concurrent.TimeUnit

/**
 * Downloads the images to print (logos, QR codes sent as URLs) through Glide,
 * which the host app already ships via expo-image.
 *
 * Decoding a remote image by hand with BitmapFactory loads it at full size,
 * which Play Console flags (bitmap memory) and which costs a lot on the
 * low-end phones the apps run on. Glide downsamples while decoding and caches
 * by URL, so the same logo is not fetched again for every receipt.
 */
object RemoteBitmapLoader {
    private const val TAG = "RemoteBitmapLoader"

    // Printed images end up at most one paper width wide (576 dots on 80 mm,
    // 832 on 112 mm printers), so nothing wider is ever needed.
    private const val MAX_WIDTH_PX = 1024
    private const val TIMEOUT_SECONDS = 10L

    /** Blocking: call from a background thread. Returns null on any failure. */
    @JvmStatic
    fun load(url: String): Bitmap? {
        val context = ApplicationContextProvider.context ?: run {
            Log.e(TAG, "Application context not initialized")
            return null
        }
        return try {
            Glide.with(context)
                .asBitmap()
                .load(url)
                // Height left as is: only the width is capped, never upscaled.
                .downsample(DownsampleStrategy.CENTER_INSIDE)
                .override(MAX_WIDTH_PX, Target.SIZE_ORIGINAL)
                .submit()
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load image: $url", e)
            null
        }
    }
}
