package com.example.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.model.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "VideoMetadataReader"

object VideoMetadataReader {

    suspend fun readMetadata(context: Context, uri: Uri): VideoMetadata = withContext(Dispatchers.IO) {
        var displayName = "video_grabado.mp4"
        var sizeBytes = 0L

        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) {
                        displayName = cursor.getString(nameIndex) ?: displayName
                    }
                    if (sizeIndex >= 0) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }
        }

        val mimeType = context.contentResolver.getType(uri) ?: "video/mp4"

        var durationMs = 0L
        var width = 0
        var height = 0
        var thumbnail: Bitmap? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) {
                width = rawH
                height = rawW
            } else {
                width = rawW
                height = rawH
            }

            val rawThumb = retriever.getFrameAtTime(
                (durationMs * 1000L / 4L).coerceAtLeast(0L),
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
            thumbnail = rawThumb?.let { scaleBitmap(it, 640) }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading video metadata", e)
        } finally {
            runCatching { retriever.release() }
        }

        VideoMetadata(
            uri = uri,
            displayName = displayName,
            durationMs = durationMs.coerceAtLeast(1000L),
            width = width,
            height = height,
            sizeBytes = sizeBytes,
            mimeType = mimeType,
            thumbnail = thumbnail
        )
    }

    suspend fun readBytesIfUnderLimit(
        context: Context,
        uri: Uri,
        maxBytes: Long = 12_000_000L
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                if (afd.length > maxBytes) return@withContext null
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(chunk)
                    if (read == -1) break
                    total += read
                    if (total > maxBytes) return@withContext null
                    out.write(chunk, 0, read)
                }
                if (total > 0L) out.toByteArray() else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read video bytes directly, falling back to keyframes", e)
            null
        }
    }

    suspend fun extractKeyframes(
        context: Context,
        uri: Uri,
        durationMs: Long,
        count: Int = 6
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val frames = mutableListOf<Bitmap>()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val safeDurationUs = (durationMs.coerceAtLeast(1000L)) * 1000L
            for (i in 0 until count) {
                val fraction = (i + 0.5f) / count.toFloat()
                val timeUs = (safeDurationUs * fraction).toLong()
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    frames.add(scaleBitmap(frame, 640))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed extracting keyframes", e)
        } finally {
            runCatching { retriever.release() }
        }
        frames
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / maxSide.toFloat()
        val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}
