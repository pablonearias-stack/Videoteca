package com.example.model

import android.graphics.Bitmap
import android.net.Uri

enum class FilterPreset(val label: String, val badge: String) {
    NONE("Original", "STD"),
    CINEMA_WARM("Cine Cálido", "WARM"),
    NOIR_BW("Noir B&N", "NOIR"),
    VINTAGE_FILM("Vintage 35mm", "FILM"),
    CYBER_COOL("Neón Frío", "COOL"),
    VIVID_POP("Vibrante", "POP")
}

enum class AspectCrop(val label: String, val ratio: Float?) {
    ORIGINAL("Original", null),
    PORTRAIT_9_16("9:16 Vertical", 9f / 16f),
    SQUARE_1_1("1:1 Cuadrado", 1f),
    LANDSCAPE_16_9("16:9 Cine", 16f / 9f)
}

enum class OverlayPosition(val label: String, val yAnchor: Float) {
    TOP("Arriba", 0.72f),
    CENTER("Centro", 0.0f),
    BOTTOM("Abajo", -0.72f)
}

data class VideoEditPlan(
    val editTitle: String = "Sin edición aplicada",
    val summary: String = "Selecciona un prompt o ajusta los controles del estudio.",
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val speedMultiplier: Float = 1.0f,
    val filterPreset: FilterPreset = FilterPreset.NONE,
    val brightness: Float = 0.0f,
    val contrast: Float = 0.0f,
    val saturation: Float = 0.0f,
    val muteAudio: Boolean = false,
    val overlayTitle: String = "",
    val overlayPosition: OverlayPosition = OverlayPosition.BOTTOM,
    val rotationDegrees: Int = 0,
    val aspectCrop: AspectCrop = AspectCrop.ORIGINAL
) {
    fun hasActiveEdits(totalDurationMs: Long): Boolean {
        val safeEnd = if (trimEndMs <= 0L) totalDurationMs else trimEndMs
        return trimStartMs > 150L ||
            (totalDurationMs > 0L && safeEnd < totalDurationMs - 150L) ||
            kotlin.math.abs(speedMultiplier - 1.0f) > 0.02f ||
            filterPreset != FilterPreset.NONE ||
            kotlin.math.abs(brightness) > 0.02f ||
            kotlin.math.abs(contrast) > 0.02f ||
            kotlin.math.abs(saturation) > 0.02f ||
            muteAudio ||
            overlayTitle.isNotBlank() ||
            rotationDegrees != 0 ||
            aspectCrop != AspectCrop.ORIGINAL
    }

    companion object {
        fun defaultForDuration(durationMs: Long): VideoEditPlan =
            VideoEditPlan(
                trimStartMs = 0L,
                trimEndMs = durationMs.coerceAtLeast(1000L)
            )
    }
}

data class VideoMetadata(
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val mimeType: String,
    val thumbnail: Bitmap?
)

data class ExportedVideo(
    val id: String,
    val uri: Uri,
    val filePath: String,
    val title: String,
    val durationMs: Long,
    val createdAtMs: Long,
    val sizeBytes: Long,
    val summary: String
)

sealed interface AiEditUiState {
    data object Idle : AiEditUiState
    data object AnalyzingVideo : AiEditUiState
    data object GeneratingPlan : AiEditUiState
    data class Success(val plan: VideoEditPlan, val promptUsed: String) : AiEditUiState
    data class Error(val message: String) : AiEditUiState
}

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Exporting(val progressPercent: Int) : ExportUiState
    data class Success(val exportedVideo: ExportedVideo) : ExportUiState
    data class Error(val message: String) : ExportUiState
}

enum class StudioTab {
    AI_PROMPT,
    ADJUST,
    EXPORTS
}
