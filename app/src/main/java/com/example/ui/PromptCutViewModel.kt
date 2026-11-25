package com.example.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.ai.GeminiVideoEditorService
import com.example.model.AiEditUiState
import com.example.model.AspectCrop
import com.example.model.ExportUiState
import com.example.model.ExportedVideo
import com.example.model.FilterPreset
import com.example.model.OverlayPosition
import com.example.model.StudioTab
import com.example.model.VideoEditPlan
import com.example.model.VideoMetadata
import com.example.video.VideoExportManager
import com.example.video.VideoMetadataReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "PromptCutViewModel"

data class QuickPromptSuggestion(
    val label: String,
    val prompt: String
)

@OptIn(UnstableApi::class)
class PromptCutViewModel(application: Application) : AndroidViewModel(application) {

    private val aiService = GeminiVideoEditorService()
    private val exportManager = VideoExportManager(application.applicationContext)

    val quickPrompts = listOf(
        QuickPromptSuggestion(
            label = "Cine cálido",
            prompt = "Dale un estilo cinematográfico cálido con contraste suave y velocidad 0.9x"
        ),
        QuickPromptSuggestion(
            label = "Noir dramático",
            prompt = "Convierte el video a blanco y negro estilo cine noir con alto contraste"
        ),
        QuickPromptSuggestion(
            label = "Vertical 9:16",
            prompt = "Adapta el video a formato vertical 9:16, colores vibrantes y velocidad 1.25x"
        ),
        QuickPromptSuggestion(
            label = "Cámara lenta",
            prompt = "Ponlo en cámara lenta 0.6x, silencia el audio y aplica filtro vintage 35mm"
        ),
        QuickPromptSuggestion(
            label = "Con título IA",
            prompt = "Analiza la escena y añade un título corto atractivo abajo con acabado vibrante"
        ),
        QuickPromptSuggestion(
            label = "Recorte rápido",
            prompt = "Recorta el inicio y final para dejar lo mejor del centro a velocidad 1.5x"
        )
    )

    private val _selectedVideo = MutableStateFlow<VideoMetadata?>(null)
    val selectedVideo: StateFlow<VideoMetadata?> = _selectedVideo.asStateFlow()

    private val _isLoadingVideo = MutableStateFlow(false)
    val isLoadingVideo: StateFlow<Boolean> = _isLoadingVideo.asStateFlow()

    private val _editPlan = MutableStateFlow(VideoEditPlan())
    val editPlan: StateFlow<VideoEditPlan> = _editPlan.asStateFlow()

    private val _promptText = MutableStateFlow("")
    val promptText: StateFlow<String> = _promptText.asStateFlow()

    private val _promptHistory = MutableStateFlow<List<String>>(emptyList())
    val promptHistory: StateFlow<List<String>> = _promptHistory.asStateFlow()

    private val _aiState = MutableStateFlow<AiEditUiState>(AiEditUiState.Idle)
    val aiState: StateFlow<AiEditUiState> = _aiState.asStateFlow()

    private val _exportState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

    private val _exportedVideos = MutableStateFlow<List<ExportedVideo>>(emptyList())
    val exportedVideos: StateFlow<List<ExportedVideo>> = _exportedVideos.asStateFlow()

    private val _activeTab = MutableStateFlow(StudioTab.AI_PROMPT)
    val activeTab: StateFlow<StudioTab> = _activeTab.asStateFlow()

    private val _previewingExport = MutableStateFlow<ExportedVideo?>(null)
    val previewingExport: StateFlow<ExportedVideo?> = _previewingExport.asStateFlow()

    fun selectTab(tab: StudioTab) {
        _activeTab.value = tab
    }

    fun updatePromptText(text: String) {
        _promptText.value = text
    }

    fun onVideoSelected(uri: Uri) {
        viewModelScope.launch {
            _isLoadingVideo.value = true
            _aiState.value = AiEditUiState.Idle
            _exportState.value = ExportUiState.Idle
            _previewingExport.value = null
            try {
                val metadata = VideoMetadataReader.readMetadata(getApplication(), uri)
                _selectedVideo.value = metadata
                _editPlan.value = VideoEditPlan.defaultForDuration(metadata.durationMs)
                _activeTab.value = StudioTab.AI_PROMPT
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error loading selected video", e)
                _aiState.value = AiEditUiState.Error("No se pudo abrir el video seleccionado.")
            } finally {
                _isLoadingVideo.value = false
            }
        }
    }

    fun applyQuickPrompt(prompt: String) {
        _promptText.value = prompt
        applyAiPrompt(prompt)
    }

    fun applyAiPrompt(overridePrompt: String? = null) {
        val video = _selectedVideo.value ?: return
        val prompt = (overridePrompt ?: _promptText.value).trim()
        if (prompt.isEmpty()) {
            _aiState.value = AiEditUiState.Error("Escribe primero qué edición quieres aplicar.")
            return
        }

        viewModelScope.launch {
            _aiState.value = AiEditUiState.AnalyzingVideo
            try {
                val generatedPlan = aiService.generateEditPlan(
                    context = getApplication(),
                    video = video,
                    userPrompt = prompt,
                    currentPlan = _editPlan.value,
                    onStageChanged = { isGenerating ->
                        _aiState.value = if (isGenerating) {
                            AiEditUiState.GeneratingPlan
                        } else {
                            AiEditUiState.AnalyzingVideo
                        }
                    }
                )
                _editPlan.value = generatedPlan
                _promptHistory.value = (listOf(prompt) + _promptHistory.value).distinct().take(6)
                _aiState.value = AiEditUiState.Success(generatedPlan, prompt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI edit failed", e)
                _aiState.value = AiEditUiState.Error(
                    e.localizedMessage ?: "No se pudo completar la edición con IA."
                )
            }
        }
    }

    fun resetEdits() {
        val duration = _selectedVideo.value?.durationMs ?: 1000L
        _editPlan.value = VideoEditPlan.defaultForDuration(duration)
        _aiState.value = AiEditUiState.Idle
    }

    fun updateTrimRange(startMs: Long, endMs: Long) {
        val duration = _selectedVideo.value?.durationMs ?: return
        val safeStart = startMs.coerceIn(0L, (duration - 500L).coerceAtLeast(0L))
        val safeEnd = endMs.coerceIn(safeStart + 500L, duration)
        _editPlan.value = _editPlan.value.copy(
            trimStartMs = safeStart,
            trimEndMs = safeEnd,
            editTitle = if (_editPlan.value.editTitle == "Sin edición aplicada") "Edición personalizada" else _editPlan.value.editTitle
        )
    }

    fun updateSpeed(speed: Float) {
        _editPlan.value = _editPlan.value.copy(speedMultiplier = speed.coerceIn(0.5f, 2.5f))
    }

    fun updateFilter(preset: FilterPreset) {
        _editPlan.value = _editPlan.value.copy(filterPreset = preset)
    }

    fun updateBrightness(value: Float) {
        _editPlan.value = _editPlan.value.copy(brightness = value.coerceIn(-0.45f, 0.45f))
    }

    fun updateContrast(value: Float) {
        _editPlan.value = _editPlan.value.copy(contrast = value.coerceIn(-0.4f, 0.7f))
    }

    fun updateSaturation(value: Float) {
        _editPlan.value = _editPlan.value.copy(saturation = value.coerceIn(-0.9f, 0.9f))
    }

    fun updateMute(muted: Boolean) {
        _editPlan.value = _editPlan.value.copy(muteAudio = muted)
    }

    fun updateOverlayTitle(title: String) {
        _editPlan.value = _editPlan.value.copy(overlayTitle = title.take(60))
    }

    fun updateOverlayPosition(position: OverlayPosition) {
        _editPlan.value = _editPlan.value.copy(overlayPosition = position)
    }

    fun rotate90() {
        val next = (_editPlan.value.rotationDegrees + 90) % 360
        _editPlan.value = _editPlan.value.copy(rotationDegrees = next)
    }

    fun updateAspectCrop(crop: AspectCrop) {
        _editPlan.value = _editPlan.value.copy(aspectCrop = crop)
    }

    fun exportVideo() {
        val video = _selectedVideo.value ?: return
        val plan = _editPlan.value
        _exportState.value = ExportUiState.Exporting(0)

        exportManager.startExport(
            video = video,
            plan = plan,
            onProgress = { pct ->
                _exportState.value = ExportUiState.Exporting(pct)
            },
            onSuccess = { exported ->
                _exportedVideos.value = listOf(exported) + _exportedVideos.value
                _exportState.value = ExportUiState.Success(exported)
                _activeTab.value = StudioTab.EXPORTS
            },
            onError = { message ->
                _exportState.value = ExportUiState.Error(message)
            }
        )
    }

    fun cancelExport() {
        exportManager.cancelExport()
        _exportState.value = ExportUiState.Idle
    }

    fun dismissExportBanner() {
        if (_exportState.value !is ExportUiState.Exporting) {
            _exportState.value = ExportUiState.Idle
        }
    }

    fun openExportedPreview(video: ExportedVideo) {
        _previewingExport.value = video
    }

    fun closeExportedPreview() {
        _previewingExport.value = null
    }

    override fun onCleared() {
        super.onCleared()
        exportManager.cancelExport()
    }
}
