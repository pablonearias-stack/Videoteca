package com.example.video

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.model.ExportedVideo
import com.example.model.VideoEditPlan
import com.example.model.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val TAG = "VideoExportManager"

@UnstableApi
class VideoExportManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = MainScope()
    private var activeTransformer: Transformer? = null
    private var progressRunnable: Runnable? = null

    fun startExport(
        video: VideoMetadata,
        plan: VideoEditPlan,
        onProgress: (Int) -> Unit,
        onSuccess: (ExportedVideo) -> Unit,
        onError: (String) -> Unit
    ) {
        cancelExport()

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val tempOutputFile = File(exportDir, "PromptCut_$timestamp.mp4")

        val safeStart = plan.trimStartMs.coerceIn(0L, (video.durationMs - 500L).coerceAtLeast(0L))
        val safeEnd = plan.trimEndMs.coerceIn(safeStart + 500L, video.durationMs)

        val mediaItem = MediaItem.Builder()
            .setUri(video.uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(safeStart)
                    .setEndPositionMs(safeEnd)
                    .build()
            )
            .build()

        val effects = Media3EffectsFactory.buildExportEffects(plan)
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(plan.muteAudio)
            .setEffects(effects)
            .build()

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    stopProgressPolling()
                    activeTransformer = null
                    scope.launch {
                        val exported = persistExportedVideo(
                            tempFile = tempOutputFile,
                            title = plan.editTitle,
                            summary = plan.summary,
                            durationMs = ((safeEnd - safeStart) / plan.speedMultiplier).toLong().coerceAtLeast(500L)
                        )
                        if (exported != null) {
                            onSuccess(exported)
                        } else {
                            onError("No se pudo guardar el video renderizado.")
                        }
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    stopProgressPolling()
                    activeTransformer = null
                    tempOutputFile.delete()
                    Log.e(TAG, "Transformer export failed", exportException)
                    onError(exportException.localizedMessage ?: "Error al exportar el video.")
                }
            })
            .build()

        val sequence = EditedMediaItemSequence.Builder(editedMediaItem).build()
        val composition = Composition.Builder(sequence)
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()

        activeTransformer = transformer
        transformer.start(composition, tempOutputFile.absolutePath)

        val holder = ProgressHolder()
        val runnable = object : Runnable {
            override fun run() {
                val t = activeTransformer ?: return
                val state = t.getProgress(holder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress.coerceIn(0, 99))
                }
                if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    mainHandler.postDelayed(this, 250L)
                }
            }
        }
        progressRunnable = runnable
        mainHandler.post(runnable)
    }

    fun cancelExport() {
        stopProgressPolling()
        activeTransformer?.cancel()
        activeTransformer = null
    }

    private fun stopProgressPolling() {
        progressRunnable?.let { mainHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    private suspend fun persistExportedVideo(
        tempFile: File,
        title: String,
        summary: String,
        durationMs: Long
    ): ExportedVideo? = withContext(Dispatchers.IO) {
        if (!tempFile.exists() || tempFile.length() == 0L) return@withContext null

        val fileName = tempFile.name
        val sizeBytes = tempFile.length()
        var finalUri: Uri? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/PromptCut")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    finalUri = uri
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaStore save failed, using FileProvider cache URI", e)
            }
        }

        val permanentFile = File(File(context.filesDir, "exports").apply { mkdirs() }, fileName)
        tempFile.copyTo(permanentFile, overwrite = true)
        tempFile.delete()

        val shareableUri = finalUri ?: FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            permanentFile
        )

        ExportedVideo(
            id = UUID.randomUUID().toString(),
            uri = shareableUri,
            filePath = permanentFile.absolutePath,
            title = title,
            durationMs = durationMs,
            createdAtMs = System.currentTimeMillis(),
            sizeBytes = sizeBytes,
            summary = summary
        )
    }
}
