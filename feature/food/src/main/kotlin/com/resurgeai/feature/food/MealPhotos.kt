package com.resurgeai.feature.food

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

/** A photo ready to send (≈1568 px JPEG) plus the small thumbnail stored with the meal. */
class PreparedPhoto(val jpeg: ByteArray, val thumbnail: ByteArray)

internal object MealPhotos {
    private const val TAG = "MealPhotos"
    /** Long edge sent to the model; larger images cost more tokens without adding accuracy. */
    private const val MAX_EDGE = 1568
    private const val THUMB_EDGE = 320

    /** A fresh file in cache/meal_photos and its content URI for the camera app. */
    fun newCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, "meal_photos").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** A fresh file in cache/meal_photos for the in-app camera to write to. */
    fun newCaptureFile(context: Context): File {
        val dir = File(context.cacheDir, "meal_photos").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        return File(dir, "capture_${System.currentTimeMillis()}.jpg")
    }

    /**
     * Decodes, rotates upright, scales and compresses. Runs on a background thread. Some camera apps
     * report success a moment before the file is fully written, so an unreadable image is retried briefly.
     */
    fun prepare(context: Context, uri: Uri): PreparedPhoto? {
        val resolver = context.contentResolver
        var bounds = BitmapFactory.Options()
        for (attempt in 1..10) {
            bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth > 0 && bounds.outHeight > 0) break
            Log.w(TAG, "Photo not readable yet (attempt $attempt): $uri")
            Thread.sleep(300)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.w(TAG, "Couldn't decode photo bounds: $uri")
            return null
        }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: run {
            Log.w(TAG, "Couldn't decode photo ${bounds.outWidth}x${bounds.outHeight} (${bounds.outMimeType}): $uri")
            return null
        }
        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val upright = if (rotation == 0f) decoded else
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation) }, true)
        val full = scale(upright, MAX_EDGE)
        val thumb = scale(full, THUMB_EDGE)
        return PreparedPhoto(jpeg(full, 82), jpeg(thumb, 70))
    }

    private fun scale(bitmap: Bitmap, edge: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= edge) return bitmap
        val factor = edge.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * factor).toInt(), (bitmap.height * factor).toInt(), true)
    }

    private fun jpeg(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out); out.toByteArray() }
}
