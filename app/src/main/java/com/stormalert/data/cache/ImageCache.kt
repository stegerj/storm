package com.stormalert.data.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Simple disk cache for radar images
 */
class ImageCache(context: Context) {
    
    private val cacheDir = File(context.cacheDir, "radar_images")
    private val cacheDuration = 10 * 60 * 1000L // 10 minutes in milliseconds
    
    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }
    
    /**
     * Generate cache key from location and mode
     */
    private fun generateCacheKey(latitude: Double, longitude: Double, mode: String): String {
        val input = "$latitude,$longitude,$mode"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Get cached image if available and not expired
     */
    fun getCachedImage(latitude: Double, longitude: Double, mode: String): Bitmap? {
        try {
            val key = generateCacheKey(latitude, longitude, mode)
            val cacheFile = File(cacheDir, "$key.png")
            
            if (cacheFile.exists()) {
                val age = System.currentTimeMillis() - cacheFile.lastModified()
                if (age < cacheDuration) {
                    Log.d("ImageCache", "Cache hit for $mode at $latitude,$longitude")
                    return BitmapFactory.decodeFile(cacheFile.absolutePath)
                } else {
                    Log.d("ImageCache", "Cache expired for $mode at $latitude,$longitude")
                    cacheFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("ImageCache", "Error reading from cache", e)
        }
        return null
    }
    
    /**
     * Cache image from Base64 string
     */
    suspend fun cacheImage(
        latitude: Double,
        longitude: Double,
        mode: String,
        base64Image: String
    ) = withContext(Dispatchers.IO) {
        try {
            val key = generateCacheKey(latitude, longitude, mode)
            val cacheFile = File(cacheDir, "$key.png")
            
            val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            
            if (bitmap != null) {
                FileOutputStream(cacheFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                Log.d("ImageCache", "Cached image for $mode at $latitude,$longitude")
            }
        } catch (e: Exception) {
            Log.e("ImageCache", "Error caching image", e)
        }
    }
    
    /**
     * Clear expired cache entries
     */
    fun clearExpiredCache() {
        try {
            val now = System.currentTimeMillis()
            cacheDir.listFiles()?.forEach { file ->
                if (now - file.lastModified() > cacheDuration) {
                    file.delete()
                    Log.d("ImageCache", "Deleted expired cache file: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e("ImageCache", "Error clearing cache", e)
        }
    }
    
    /**
     * Clear all cache
     */
    fun clearAllCache() {
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
            Log.d("ImageCache", "Cleared all cache")
        } catch (e: Exception) {
            Log.e("ImageCache", "Error clearing all cache", e)
        }
    }
}
