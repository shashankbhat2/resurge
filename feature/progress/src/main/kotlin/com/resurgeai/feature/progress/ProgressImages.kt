package com.resurgeai.feature.progress

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.resurgeai.core.data.progress.PhotoVariant
import com.resurgeai.core.data.progress.ProgressRepository
import com.resurgeai.core.designsystem.components.SkeletonBox
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.ProgressPhoto
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt

/** A photo ready to save: a 3:4 JPEG with no metadata (so no location), its thumbnail and size. */
internal class PreparedProgressPhoto(val full: ByteArray, val thumb: ByteArray, val width: Int, val height: Int, val takenAt: Instant)

internal object ProgressImages {
    private const val TAG = "ProgressImages"
    private const val LONG_EDGE = 1600
    private const val THUMB_LONG_EDGE = 480

    /** Decrypted bitmaps, so scrolling back through the grid doesn't decrypt again. */
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun load(repository: ProgressRepository, photo: ProgressPhoto, variant: PhotoVariant): Bitmap? {
        val key = "${photo.id}/${variant.file}"
        cache.get(key)?.let { return it }
        val bytes = repository.image(photo, variant) ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    fun forget(photo: ProgressPhoto) = PhotoVariant.entries.forEach { cache.remove("${photo.id}/${it.file}") }

    fun clear() = cache.evictAll()

    fun newCaptureFile(context: Context): File {
        val dir = File(context.cacheDir, "progress_capture").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        return File(dir, "capture_${System.currentTimeMillis()}.jpg")
    }

    /**
     * Upright (every EXIF orientation, including the mirror flip CameraX writes for front camera
     * shots, so they match the preview and the ghost lines up), cropped to 3:4 and scaled. Re-encoding drops all EXIF, including GPS. The date taken
     * comes from EXIF for gallery imports.
     */
    fun prepare(context: Context, uri: Uri, fallbackTakenAt: Instant = Instant.now()): PreparedProgressPhoto? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.w(TAG, "Couldn't read photo bounds: $uri")
            return null
        }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= LONG_EDGE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        var orientation = ExifInterface.ORIENTATION_NORMAL
        var takenAt: Instant? = null
        resolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            takenAt = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { raw ->
                runCatching { LocalDateTime.parse(raw, EXIF_DATE).atZone(ZoneId.systemDefault()).toInstant() }.getOrNull()
            }
        }
        val upright = orientationMatrix(orientation)?.let { Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, it, true) } ?: decoded
        val cropped = cropTo3x4(upright)
        val full = scale(cropped, LONG_EDGE)
        val thumb = scale(full, THUMB_LONG_EDGE)
        return PreparedProgressPhoto(jpeg(full, 86), jpeg(thumb, 76), full.width, full.height, takenAt?.takeIf { it.isBefore(Instant.now()) } ?: fallbackTakenAt)
    }

    /** The transform that undoes an EXIF orientation, or null when it's already upright. */
    private fun orientationMatrix(orientation: Int): Matrix? = when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { setScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { setRotate(180f) }
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> Matrix().apply { setRotate(180f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSPOSE -> Matrix().apply { setRotate(90f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply { setRotate(90f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> Matrix().apply { setRotate(-90f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply { setRotate(-90f) }
        else -> null
    }

    private fun cropTo3x4(b: Bitmap): Bitmap {
        val target = 3f / 4f
        val ratio = b.width.toFloat() / b.height
        return when {
            ratio > target + 0.01f -> {
                val w = (b.height * target).roundToInt()
                Bitmap.createBitmap(b, (b.width - w) / 2, 0, w, b.height)
            }
            ratio < target - 0.01f -> {
                val h = (b.width / target).roundToInt()
                Bitmap.createBitmap(b, 0, (b.height - h) / 2, b.width, h)
            }
            else -> b
        }
    }

    private fun scale(b: Bitmap, longEdge: Int): Bitmap {
        val longest = max(b.width, b.height)
        if (longest <= longEdge) return b
        val f = longEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(b, (b.width * f).roundToInt(), (b.height * f).roundToInt(), true)
    }

    private fun jpeg(b: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().use { out -> b.compress(Bitmap.CompressFormat.JPEG, quality, out); out.toByteArray() }

    private val EXIF_DATE = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
}

/** A decrypted progress photo, with a skeleton while it loads. */
@Composable
internal fun EncryptedImage(
    repository: ProgressRepository,
    photo: ProgressPhoto,
    variant: PhotoVariant,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = 1f,
) {
    val bitmap by produceState<ImageBitmap?>(null, photo.id, variant) {
        value = runCatching { ProgressImages.load(repository, photo, variant) }.getOrNull()?.asImageBitmap()
    }
    val image = bitmap
    if (image != null) {
        Image(image, contentDescription = null, modifier = modifier, contentScale = contentScale, alpha = alpha)
    } else if (alpha >= 1f) {
        SkeletonBox(modifier, Resurge.shape.sm)
    }
}
