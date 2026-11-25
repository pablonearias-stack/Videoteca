package com.example.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.media3.common.Effect
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.Effects
import com.example.model.AspectCrop
import com.example.model.FilterPreset
import com.example.model.OverlayPosition
import com.example.model.VideoEditPlan
import kotlin.math.abs

@UnstableApi
object Media3EffectsFactory {

    fun buildPreviewVideoEffects(plan: VideoEditPlan): List<Effect> {
        val effects = mutableListOf<Effect>()

        when (plan.filterPreset) {
            FilterPreset.NONE -> Unit
            FilterPreset.CINEMA_WARM -> {
                effects += RgbAdjustment.Builder()
                    .setRedScale(1.14f)
                    .setGreenScale(1.02f)
                    .setBlueScale(0.86f)
                    .build()
                effects += Contrast(0.14f)
            }
            FilterPreset.NOIR_BW -> {
                effects += RgbFilter.createGrayscaleFilter()
                effects += Contrast(0.28f)
            }
            FilterPreset.VINTAGE_FILM -> {
                effects += RgbAdjustment.Builder()
                    .setRedScale(1.12f)
                    .setGreenScale(1.04f)
                    .setBlueScale(0.78f)
                    .build()
                effects += HslAdjustment.Builder()
                    .adjustSaturation(-22f)
                    .build()
                effects += Brightness(0.04f)
            }
            FilterPreset.CYBER_COOL -> {
                effects += RgbAdjustment.Builder()
                    .setRedScale(0.85f)
                    .setGreenScale(1.04f)
                    .setBlueScale(1.18f)
                    .build()
                effects += Contrast(0.16f)
            }
            FilterPreset.VIVID_POP -> {
                effects += HslAdjustment.Builder()
                    .adjustSaturation(32f)
                    .build()
                effects += Contrast(0.12f)
            }
        }

        if (abs(plan.brightness) > 0.02f) {
            effects += Brightness(plan.brightness.coerceIn(-0.8f, 0.8f))
        }
        if (abs(plan.contrast) > 0.02f) {
            effects += Contrast(plan.contrast.coerceIn(-0.8f, 0.9f))
        }
        if (abs(plan.saturation) > 0.02f) {
            effects += HslAdjustment.Builder()
                .adjustSaturation((plan.saturation * 100f).coerceIn(-100f, 100f))
                .build()
        }

        if (plan.rotationDegrees != 0) {
            effects += ScaleAndRotateTransformation.Builder()
                .setRotationDegrees(plan.rotationDegrees.toFloat())
                .build()
        }

        plan.aspectCrop.ratio?.let { targetRatio ->
            effects += Presentation.createForAspectRatio(
                targetRatio,
                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            )
        }

        if (plan.overlayTitle.isNotBlank()) {
            val bitmap = createCaptionBitmap(plan.overlayTitle)
            val overlaySettings = StaticOverlaySettings.Builder()
                .setBackgroundFrameAnchor(0f, plan.overlayPosition.yAnchor)
                .setOverlayFrameAnchor(0f, 0f)
                .setAlphaScale(0.96f)
                .build()
            val bitmapOverlay = BitmapOverlay.createStaticBitmapOverlay(bitmap, overlaySettings)
            effects += OverlayEffect(listOf(bitmapOverlay))
        }

        return effects
    }

    fun buildExportEffects(plan: VideoEditPlan): Effects {
        val videoEffects = buildPreviewVideoEffects(plan).toMutableList()
        val audioProcessors = mutableListOf<AudioProcessor>()

        if (abs(plan.speedMultiplier - 1.0f) > 0.02f) {
            val speed = plan.speedMultiplier.coerceIn(0.5f, 2.5f)
            val speedProvider = object : SpeedProvider {
                override fun getSpeed(timeUs: Long): Float = speed
                override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = androidx.media3.common.C.TIME_UNSET
            }
            val speedEffectPair = Effects.createExperimentalSpeedChangingEffect(speedProvider)
            if (!plan.muteAudio) {
                audioProcessors += speedEffectPair.first
            }
            videoEffects += speedEffectPair.second
        }

        return Effects(audioProcessors, videoEffects)
    }

    private fun createCaptionBitmap(text: String): Bitmap {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 54f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            setShadowLayer(6f, 0f, 3f, Color.argb(180, 0, 0, 0))
        }

        val horizontalPadding = 48f
        val verticalPadding = 26f
        val measuredWidth = textPaint.measureText(text).coerceIn(120f, 980f)
        val fontMetrics = textPaint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent

        val width = (measuredWidth + horizontalPadding * 2).toInt()
        val height = (textHeight + verticalPadding * 2).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(185, 13, 16, 26)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 255, 90, 54)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        val rect = RectF(2f, 2f, width - 2f, height - 2f)
        canvas.drawRoundRect(rect, 28f, 28f, bgPaint)
        canvas.drawRoundRect(rect, 28f, 28f, borderPaint)

        val baselineY = height / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(text, width / 2f, baselineY, textPaint)
        return bitmap
    }
}
