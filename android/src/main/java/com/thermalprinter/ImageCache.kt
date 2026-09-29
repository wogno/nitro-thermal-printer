package com.thermalprinter

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.thermalprinter.utils.RemoteBitmapLoader

/**
 * LRU Image cache for thermal printer images.
 * Caches up to maxSize images, evicting least recently used when full.
 * Uses URL as key for caching.
 */
class ImageCache(private val maxSize: Int = 10) {

    private val cache = object : LinkedHashMap<String, Bitmap>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            return size > maxSize
        }
    }

    /**
     * Cache an image from URL
     * @param url Image URL to download and cache
     * @param key Unique key to reference the cached image
     */
    suspend fun cacheFromUrl(url: String, key: String) {
        withContext(Dispatchers.IO) {
            val bitmap = downloadBitmap(url)
            bitmap?.let {
                synchronized(cache) {
                    cache[key] = it
                }
            } ?: throw IllegalArgumentException("Failed to download image from: $url")
        }
    }

    /**
     * Cache a bitmap directly
     * @param bitmap Bitmap to cache
     * @param key Unique key to reference the cached image
     */
    fun cacheBitmap(bitmap: Bitmap, key: String) {
        synchronized(cache) {
            cache[key] = bitmap
        }
    }

    /**
     * Get a cached image by key
     * @param key Key used when caching
     * @returns Cached bitmap or null if not found
     */
    fun get(key: String): Bitmap? {
        return synchronized(cache) {
            cache[key]
        }
    }

    /**
     * Check if an image is cached
     * @param key Key to check
     * @returns true if image is cached
     */
    fun contains(key: String): Boolean {
        return synchronized(cache) {
            cache.containsKey(key)
        }
    }

    /**
     * Remove a cached image
     * @param key Key of image to remove
     */
    fun remove(key: String) {
        synchronized(cache) {
            cache.remove(key)?.let { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    /**
     * Clear all cached images
     */
    fun clear() {
        synchronized(cache) {
            cache.values.forEach { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            cache.clear()
        }
    }

    /**
     * Get current cache size
     */
    fun size(): Int {
        return synchronized(cache) {
            cache.size
        }
    }

    /**
     * Download a bitmap from URL
     */
    private fun downloadBitmap(src: String): Bitmap? = RemoteBitmapLoader.load(src)

    /**
     * Download and decode a bitmap from URL (public version for direct use)
     */
    suspend fun downloadBitmapAsync(url: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            downloadBitmap(url)
        }
    }
}
