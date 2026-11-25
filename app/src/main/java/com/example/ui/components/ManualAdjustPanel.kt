package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AspectCrop
import com.example.model.FilterPreset
import com.example.model.OverlayPosition
import com.example.model.VideoEditPlan
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualAdjustPanel(
    totalDurationMs: Long,
    plan: VideoEditPlan,
    onTrimChange: (Long, Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onFilterChange: (FilterPreset) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onMuteChange: (Boolean) -> Unit,
    onOverlayTitleChange: (String) -> Unit,
    onOverlayPositionChange: (OverlayPosition) -> Unit,
    onRotate90: () -> Unit,
    onAspectCropChange: (AspectCrop) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeDuration = totalDurationMs.coerceAtLeast(1000L).toFloat()
    val startVal = plan.trimStartMs.toFloat().coerceIn(0f, safeDuration - 500f)
    val endVal = plan.trimEndMs.toFloat().coerceIn(startVal + 500f, safeDuration)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Trim Card
        SectionCard(title = "Recorte temporal", icon = Icons.Default.ContentCut) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Inicio ${formatTimecode(startVal.toLong())}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Fin ${formatTimecode(endVal.toLong())}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            RangeSlider(
                value = startVal..endVal,
                onValueChange = { range ->
                    val s = range.start.toLong()
                    val e = range.endInclusive.toLong()
                    if (e - s >= 500L) {
                        onTrimChange(s, e)
                    }
                },
                valueRange = 0f..safeDuration,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.testTag("trim_range_slider")
            )
        }

        // Speed & Orientation Card
        SectionCard(title = "Ritmo y encuadre", icon = Icons.Default.Speed) {
            val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                speedOptions.forEach { spd ->
                    val selected = kotlin.math.abs(plan.speedMultiplier - spd) < 0.05f
                    FilterChip(
                        selected = selected,
                        onClick = { onSpeedChange(spd) },
                        label = {
                            Text(
                                text = "${spd}x",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("speed_chip_${spd}")
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AspectCrop.entries.forEach { crop ->
                    FilterChip(
                        selected = plan.aspectCrop == crop,
                        onClick = { onAspectCropChange(crop) },
                        label = { Text(crop.label, style = MaterialTheme.typography.bodyMedium) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("aspect_chip_${crop.name}")
                    )
                }
                OutlinedButton(
                    onClick = onRotate90,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("rotate_90_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.RotateRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${plan.rotationDegrees}°", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Filter Presets
        SectionCard(title = "Filtros cinematográficos", icon = Icons.Default.FilterVintage) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = plan.filterPreset == preset,
                        onClick = { onFilterChange(preset) },
                        label = { Text(preset.label, style = MaterialTheme.typography.bodyMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("filter_chip_${preset.name}")
                    )
                }
            }
        }

        // Color & Audio Sliders
        SectionCard(title = "Color y audio", icon = Icons.Default.Tune) {
            LabeledSlider(
                label = "Brillo",
                value = plan.brightness,
                valueRange = -0.45f..0.45f,
                displayText = String.format(Locale.US, "%+.0f%%", plan.brightness * 100),
                onValueChange = onBrightnessChange,
                tag = "brightness_slider"
            )
            LabeledSlider(
                label = "Contraste",
                value = plan.contrast,
                valueRange = -0.40f..0.70f,
                displayText = String.format(Locale.US, "%+.0f%%", plan.contrast * 100),
                onValueChange = onContrastChange,
                tag = "contrast_slider"
            )
            LabeledSlider(
                label = "Saturación",
                value = plan.saturation,
                valueRange = -0.90f..0.90f,
                displayText = "${(plan.saturation * 100).roundToInt()}%",
                onValueChange = onSaturationChange,
                tag = "saturation_slider"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Silenciar audio original",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = plan.muteAudio,
                    onCheckedChange = onMuteChange,
                    modifier = Modifier.testTag("mute_audio_switch")
                )
            }
        }

        // Caption Overlay
        SectionCard(title = "Título sobre el video", icon = Icons.Default.Title) {
            OutlinedTextField(
                value = plan.overlayTitle,
                onValueChange = onOverlayTitleChange,
                placeholder = { Text("Texto corto opcional (máx. 60 car.)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("overlay_title_input")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverlayPosition.entries.forEach { pos ->
                    FilterChip(
                        selected = plan.overlayPosition == pos,
                        onClick = { onOverlayPositionChange(pos) },
                        label = { Text(pos.label, style = MaterialTheme.typography.bodyMedium) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("overlay_pos_${pos.name}")
                    )
                }
            }
        }

        OutlinedButton(
            onClick = onReset,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("reset_edits_button")
        ) {
            Icon(
                imageVector = Icons.Default.RestartAlt,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.action_reset_edits))
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    displayText: String,
    onValueChange: (Float) -> Unit,
    tag: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = displayText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.secondary,
                activeTrackColor = MaterialTheme.colorScheme.secondary
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}
