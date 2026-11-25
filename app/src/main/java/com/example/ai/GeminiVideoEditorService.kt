package com.example.ai

import android.content.Context
import android.util.Log
import com.example.model.AspectCrop
import com.example.model.FilterPreset
import com.example.model.OverlayPosition
import com.example.model.VideoEditPlan
import com.example.model.VideoMetadata
import com.example.video.VideoMetadataReader
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiVideoEditor"

class GeminiVideoEditorService {

    private val editPlanSchema = Schema.obj(
        properties = mapOf(
            "editTitle" to Schema.string(
                description = "Título corto y atractivo en español de la edición aplicada (máx 40 caracteres)"
            ),
            "summary" to Schema.string(
                description = "Explicación breve en español (1-2 frases) de qué cambios se aplicaron al video según su contenido visual y el prompt"
            ),
            "trimStartMs" to Schema.integer(
                description = "Milisegundo de inicio del recorte (>= 0)"
            ),
            "trimEndMs" to Schema.integer(
                description = "Milisegundo de fin del recorte (<= duración total, al menos 500ms después de trimStartMs)"
            ),
            "speedMultiplier" to Schema.double(
                description = "Velocidad de reproducción entre 0.5 (cámara lenta) y 2.5 (rápido). Normal es 1.0"
            ),
            "filterPreset" to Schema.enumeration(
                values = listOf("NONE", "CINEMA_WARM", "NOIR_BW", "VINTAGE_FILM", "CYBER_COOL", "VIVID_POP"),
                description = "Preset de color cinematográfico principal"
            ),
            "brightness" to Schema.double(
                description = "Ajuste fino de brillo entre -0.45 y 0.45. Neutro es 0.0"
            ),
            "contrast" to Schema.double(
                description = "Ajuste fino de contraste entre -0.40 y 0.70. Neutro es 0.0"
            ),
            "saturation" to Schema.double(
                description = "Ajuste fino de saturación entre -0.90 y 0.90. Neutro es 0.0"
            ),
            "muteAudio" to Schema.boolean(
                description = "True para silenciar el audio original si el usuario lo pide o si conviene para cámara lenta dramática"
            ),
            "overlayTitle" to Schema.string(
                description = "Texto corto opcional de título o subtítulo superpuesto sobre el video (vacío si no aplica)"
            ),
            "overlayPosition" to Schema.enumeration(
                values = listOf("TOP", "CENTER", "BOTTOM"),
                description = "Posición del título superpuesto"
            ),
            "rotationDegrees" to Schema.enumeration(
                values = listOf("0", "90", "180", "270"),
                description = "Rotación del video en grados"
            ),
            "aspectCrop" to Schema.enumeration(
                values = listOf("ORIGINAL", "PORTRAIT_9_16", "SQUARE_1_1", "LANDSCAPE_16_9"),
                description = "Relación de aspecto final deseada"
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.35f
                responseMimeType = "application/json"
                responseSchema = editPlanSchema
            }
        )
    }

    suspend fun generateEditPlan(
        context: Context,
        video: VideoMetadata,
        userPrompt: String,
        currentPlan: VideoEditPlan,
        onStageChanged: (isGeneratingPlan: Boolean) -> Unit
    ): VideoEditPlan {
        onStageChanged(false)
        val inlineVideoBytes = VideoMetadataReader.readBytesIfUnderLimit(context, video.uri)
        val keyframes = if (inlineVideoBytes == null) {
            VideoMetadataReader.extractKeyframes(context, video.uri, video.durationMs, count = 6)
        } else {
            emptyList()
        }

        onStageChanged(true)

        val instruction = buildString {
            appendLine("Eres un director de montaje y colorista experto en edición automática de video para móviles.")
            appendLine("Analiza el video proporcionado y la instrucción del usuario para producir un plan de edición exacto en JSON.")
            appendLine()
            appendLine("Metadatos reales del video:")
            appendLine("- Nombre: ${video.displayName}")
            appendLine("- Duración total: ${video.durationMs} ms")
            appendLine("- Resolución: ${video.width}x${video.height}")
            appendLine()
            appendLine("Estado actual de edición (si el usuario pide refinar o agregar algo sobre lo anterior, consérvalo salvo que pida cambiarlo):")
            appendLine("- trimStartMs=${currentPlan.trimStartMs}, trimEndMs=${currentPlan.trimEndMs}")
            appendLine("- speedMultiplier=${currentPlan.speedMultiplier}, filterPreset=${currentPlan.filterPreset.name}")
            appendLine("- brightness=${currentPlan.brightness}, contrast=${currentPlan.contrast}, saturation=${currentPlan.saturation}")
            appendLine("- muteAudio=${currentPlan.muteAudio}, overlayTitle=\"${currentPlan.overlayTitle}\", overlayPosition=${currentPlan.overlayPosition.name}")
            appendLine("- rotationDegrees=${currentPlan.rotationDegrees}, aspectCrop=${currentPlan.aspectCrop.name}")
            appendLine()
            appendLine("Reglas estrictas:")
            appendLine("1. trimStartMs >= 0 y trimEndMs <= ${video.durationMs}, con al menos 500 ms de duración entre ambos.")
            appendLine("2. Si el usuario no menciona recortar duración, mantén trimStartMs=0 y trimEndMs=${video.durationMs} (o el recorte actual si estaba refinando).")
            appendLine("3. Si el usuario pide texto, título, frase o subtítulo, escribe un texto corto y limpio en overlayTitle; si no lo pide, deja overlayTitle vacío \"\".")
            appendLine("4. Responde editTitle y summary en español natural.")
            appendLine()
            appendLine("Prompt del usuario: \"$userPrompt\"")
        }

        val inputContent = content {
            if (inlineVideoBytes != null) {
                inlineData(inlineVideoBytes, video.mimeType)
            } else {
                for (frame in keyframes) {
                    image(frame)
                }
            }
            text(instruction)
        }

        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(inputContent)
            }
            val rawJson = response.text
            if (rawJson.isNullOrBlank()) {
                throw IllegalStateException("Gemini devolvió una respuesta vacía.")
            }
            parseAndClampPlan(rawJson, video.durationMs)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini video edit timed out", e)
            throw IllegalStateException("La IA tardó demasiado (>60s). Intenta con un video más corto o reintenta.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini video edit failed", e)
            throw IllegalStateException(
                e.localizedMessage ?: "No se pudo generar la edición con Gemini.",
                e
            )
        }
    }

    private fun parseAndClampPlan(jsonString: String, totalDurationMs: Long): VideoEditPlan {
        val json = JSONObject(jsonString)
        val safeDuration = totalDurationMs.coerceAtLeast(1000L)

        var startMs = json.optLong("trimStartMs", 0L).coerceIn(0L, safeDuration - 500L)
        var endMs = json.optLong("trimEndMs", safeDuration).coerceIn(500L, safeDuration)
        if (endMs - startMs < 500L) {
            startMs = 0L
            endMs = safeDuration
        }

        val speed = json.optDouble("speedMultiplier", 1.0).toFloat().coerceIn(0.5f, 2.5f)
        val filterName = json.optString("filterPreset", "NONE")
        val filterPreset = FilterPreset.entries.firstOrNull { it.name == filterName } ?: FilterPreset.NONE

        val brightness = json.optDouble("brightness", 0.0).toFloat().coerceIn(-0.45f, 0.45f)
        val contrast = json.optDouble("contrast", 0.0).toFloat().coerceIn(-0.40f, 0.70f)
        val saturation = json.optDouble("saturation", 0.0).toFloat().coerceIn(-0.90f, 0.90f)
        val muteAudio = json.optBoolean("muteAudio", false)
        val overlayTitle = json.optString("overlayTitle", "").trim().take(60)
        val posName = json.optString("overlayPosition", "BOTTOM")
        val overlayPos = OverlayPosition.entries.firstOrNull { it.name == posName } ?: OverlayPosition.BOTTOM
        val rotation = json.optString("rotationDegrees", "0").toIntOrNull()?.let {
            if (it in setOf(0, 90, 180, 270)) it else 0
        } ?: 0
        val cropName = json.optString("aspectCrop", "ORIGINAL")
        val aspectCrop = AspectCrop.entries.firstOrNull { it.name == cropName } ?: AspectCrop.ORIGINAL

        return VideoEditPlan(
            editTitle = json.optString("editTitle", "Edición IA").ifBlank { "Edición IA" },
            summary = json.optString("summary", "Edición generada automáticamente.").ifBlank { "Edición generada automáticamente." },
            trimStartMs = startMs,
            trimEndMs = endMs,
            speedMultiplier = speed,
            filterPreset = filterPreset,
            brightness = brightness,
            contrast = contrast,
            saturation = saturation,
            muteAudio = muteAudio,
            overlayTitle = overlayTitle,
            overlayPosition = overlayPos,
            rotationDegrees = rotation,
            aspectCrop = aspectCrop
        )
    }
}
