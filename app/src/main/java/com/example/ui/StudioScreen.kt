package com.example.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.ExportUiState
import com.example.model.ExportedVideo
import com.example.model.StudioTab
import com.example.model.VideoEditPlan
import com.example.model.VideoMetadata
import com.example.ui.components.AiPromptPanel
import com.example.ui.components.ExportsPanel
import com.example.ui.components.ManualAdjustPanel
import com.example.ui.components.VideoPreviewPlayer
import com.example.ui.components.formatTimecode

@Composable
fun StudioScreen(
    viewModel: PromptCutViewModel
) {
    val context = LocalContext.current
    val selectedVideo by viewModel.selectedVideo.collectAsStateWithLifecycle()
    val isLoadingVideo by viewModel.isLoadingVideo.collectAsStateWithLifecycle()
    val editPlan by viewModel.editPlan.collectAsStateWithLifecycle()
    val promptText by viewModel.promptText.collectAsStateWithLifecycle()
    val promptHistory by viewModel.promptHistory.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val exportedVideos by viewModel.exportedVideos.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val previewingExport by viewModel.previewingExport.collectAsStateWithLifecycle()

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onVideoSelected(uri)
        }
    }

    val launchPicker = {
        videoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
    }

    val shareVideo: (ExportedVideo) -> Unit = { exported ->
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, exported.uri)
            putExtra(Intent.EXTRA_SUBJECT, exported.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(shareIntent, context.getString(R.string.action_share_video))
        )
    }

    previewingExport?.let { exported ->
        BackHandler {
            viewModel.closeExportedPreview()
        }
        ExportedPreviewOverlay(
            exported = exported,
            onShare = { shareVideo(exported) },
            onClose = viewModel::closeExportedPreview
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            StudioTopBar(
                selectedVideo = selectedVideo,
                onPickVideo = launchPicker
            )

            if (isLoadingVideo) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Cargando video del dispositivo…",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else if (selectedVideo == null) {
                EmptyStudioHero(
                    exportedVideos = exportedVideos,
                    onPickVideo = launchPicker,
                    onPlayExported = viewModel::openExportedPreview,
                    onShareExported = shareVideo
                )
            } else {
                val video = selectedVideo!!
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isWide = maxWidth >= 720.dp
                    if (isWide) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(end = 16.dp, top = 8.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                NavigationRailItem(
                                    selected = activeTab == StudioTab.AI_PROMPT,
                                    onClick = { viewModel.selectTab(StudioTab.AI_PROMPT) },
                                    icon = {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = stringResource(R.string.tab_ai_prompt))
                                    },
                                    label = { Text(stringResource(R.string.tab_ai_prompt)) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("rail_ai_prompt")
                                )
                                NavigationRailItem(
                                    selected = activeTab == StudioTab.ADJUST,
                                    onClick = { viewModel.selectTab(StudioTab.ADJUST) },
                                    icon = {
                                        Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.tab_manual_adjust))
                                    },
                                    label = { Text(stringResource(R.string.tab_manual_adjust)) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("rail_manual_adjust")
                                )
                                NavigationRailItem(
                                    selected = activeTab == StudioTab.EXPORTS,
                                    onClick = { viewModel.selectTab(StudioTab.EXPORTS) },
                                    icon = {
                                        Icon(Icons.Default.VideoLibrary, contentDescription = stringResource(R.string.tab_exports))
                                    },
                                    label = {
                                        val suffix = if (exportedVideos.isNotEmpty()) " (${exportedVideos.size})" else ""
                                        Text(stringResource(R.string.tab_exports) + suffix)
                                    },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("rail_exports")
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                PreviewAndExportBlock(
                                    video = video,
                                    plan = editPlan,
                                    exportState = exportState,
                                    onToggleMute = viewModel::updateMute,
                                    onExport = viewModel::exportVideo,
                                    onCancelExport = viewModel::cancelExport,
                                    onDismissExportBanner = viewModel::dismissExportBanner,
                                    onPlayExported = viewModel::openExportedPreview
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1.05f)
                                    .fillMaxHeight()
                            ) {
                                StudioTabsWorkspace(
                                    activeTab = activeTab,
                                    onSelectTab = viewModel::selectTab,
                                    video = video,
                                    editPlan = editPlan,
                                    promptText = promptText,
                                    quickPrompts = viewModel.quickPrompts,
                                    promptHistory = promptHistory,
                                    aiState = aiState,
                                    exportedVideos = exportedVideos,
                                    viewModel = viewModel,
                                    onPlayExported = viewModel::openExportedPreview,
                                    onShareExported = shareVideo,
                                    showTabRow = false
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                                .verticalScroll(rememberScrollState())
                                .navigationBarsPadding(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PreviewAndExportBlock(
                                video = video,
                                plan = editPlan,
                                exportState = exportState,
                                onToggleMute = viewModel::updateMute,
                                onExport = viewModel::exportVideo,
                                onCancelExport = viewModel::cancelExport,
                                onDismissExportBanner = viewModel::dismissExportBanner,
                                onPlayExported = viewModel::openExportedPreview
                            )

                            StudioTabsWorkspace(
                                activeTab = activeTab,
                                onSelectTab = viewModel::selectTab,
                                video = video,
                                editPlan = editPlan,
                                promptText = promptText,
                                quickPrompts = viewModel.quickPrompts,
                                promptHistory = promptHistory,
                                aiState = aiState,
                                exportedVideos = exportedVideos,
                                viewModel = viewModel,
                                onPlayExported = viewModel::openExportedPreview,
                                onShareExported = shareVideo,
                                scrollableContent = false
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioTopBar(
    selectedVideo: VideoMetadata?,
    onPickVideo: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MovieCreation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = selectedVideo?.let {
                        "${it.width}×${it.height} · ${formatTimecode(it.durationMs)}"
                    } ?: stringResource(R.string.studio_tagline),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (selectedVideo != null) {
            OutlinedButton(
                onClick = onPickVideo,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("change_video_button")
            ) {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = stringResource(R.string.cd_pick_video),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.action_change_video),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyStudioHero(
    exportedVideos: List<ExportedVideo>,
    onPickVideo: () -> Unit,
    onPlayExported: (ExportedVideo) -> Unit,
    onShareExported: (ExportedVideo) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_studio_hero_1795627404637),
                contentDescription = stringResource(R.string.cd_studio_hero),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x330B0D12),
                                Color(0xCC0B0D12),
                                Color(0xFF131620)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "GEMINI MULTIMODAL + MEDIA3",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.hero_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Column(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.hero_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CapabilityPill(icon = Icons.Default.Tune, text = "Recorte y formato")
                CapabilityPill(icon = Icons.Default.Palette, text = "Color cinematográfico")
                CapabilityPill(icon = Icons.Default.SlowMotionVideo, text = "Ritmo y subtítulos")
            }

            Button(
                onClick = onPickVideo,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("pick_video_button")
            ) {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.action_pick_video),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (exportedVideos.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Exportados en esta sesión",
                    style = MaterialTheme.typography.titleLarge
                )
                ExportsPanel(
                    exportedVideos = exportedVideos,
                    onPlayExported = onPlayExported,
                    onShareExported = onShareExported
                )
            }
        }
    }
}

@Composable
private fun CapabilityPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PreviewAndExportBlock(
    video: VideoMetadata,
    plan: VideoEditPlan,
    exportState: ExportUiState,
    onToggleMute: (Boolean) -> Unit,
    onExport: () -> Unit,
    onCancelExport: () -> Unit,
    onDismissExportBanner: () -> Unit,
    onPlayExported: (ExportedVideo) -> Unit
) {
    VideoPreviewPlayer(
        videoUri = video.uri,
        totalDurationMs = video.durationMs,
        plan = plan,
        onToggleMute = onToggleMute
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = plan.editTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = plan.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            val isExporting = exportState is ExportUiState.Exporting
            Button(
                onClick = { if (isExporting) onCancelExport() else onExport() },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isExporting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                    contentColor = if (isExporting) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSecondary
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier
                    .height(48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("export_video_button")
            ) {
                Icon(
                    imageVector = if (isExporting) Icons.Default.Close else Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isExporting) "Cancelar" else stringResource(R.string.action_export_mp4),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }

    AnimatedVisibility(visible = exportState !is ExportUiState.Idle) {
        when (exportState) {
            is ExportUiState.Exporting -> {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.export_status_rendering, exportState.progressPercent),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        LinearProgressIndicator(
                            progress = { exportState.progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
            is ExportUiState.Success -> {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = stringResource(R.string.export_success_saved),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        TextButton(
                            onClick = { onPlayExported(exportState.exportedVideo) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Ver")
                        }
                        IconButton(
                            onClick = onDismissExportBanner,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar")
                        }
                    }
                }
            }
            is ExportUiState.Error -> {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = exportState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismissExportBanner,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar")
                        }
                    }
                }
            }
            ExportUiState.Idle -> Unit
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioTabsWorkspace(
    activeTab: StudioTab,
    onSelectTab: (StudioTab) -> Unit,
    video: VideoMetadata,
    editPlan: VideoEditPlan,
    promptText: String,
    quickPrompts: List<QuickPromptSuggestion>,
    promptHistory: List<String>,
    aiState: com.example.model.AiEditUiState,
    exportedVideos: List<ExportedVideo>,
    viewModel: PromptCutViewModel,
    onPlayExported: (ExportedVideo) -> Unit,
    onShareExported: (ExportedVideo) -> Unit,
    scrollableContent: Boolean = true,
    showTabRow: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showTabRow) {
            SecondaryTabRow(
                selectedTabIndex = activeTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Tab(
                    selected = activeTab == StudioTab.AI_PROMPT,
                    onClick = { onSelectTab(StudioTab.AI_PROMPT) },
                    text = { Text(stringResource(R.string.tab_ai_prompt)) },
                    modifier = Modifier.testTag("tab_ai_prompt")
                )
                Tab(
                    selected = activeTab == StudioTab.ADJUST,
                    onClick = { onSelectTab(StudioTab.ADJUST) },
                    text = { Text(stringResource(R.string.tab_manual_adjust)) },
                    modifier = Modifier.testTag("tab_manual_adjust")
                )
                Tab(
                    selected = activeTab == StudioTab.EXPORTS,
                    onClick = { onSelectTab(StudioTab.EXPORTS) },
                    text = {
                        val countSuffix = if (exportedVideos.isNotEmpty()) " (${exportedVideos.size})" else ""
                        Text(stringResource(R.string.tab_exports) + countSuffix)
                    },
                    modifier = Modifier.testTag("tab_exports")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        val contentModifier = if (scrollableContent) {
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        } else {
            Modifier.fillMaxWidth()
        }

        Box(modifier = contentModifier) {
            when (activeTab) {
                StudioTab.AI_PROMPT -> {
                    AiPromptPanel(
                        promptText = promptText,
                        quickPrompts = quickPrompts,
                        promptHistory = promptHistory,
                        aiState = aiState,
                        onPromptChange = viewModel::updatePromptText,
                        onApplyPrompt = { viewModel.applyAiPrompt() },
                        onSelectQuickPrompt = viewModel::applyQuickPrompt
                    )
                }
                StudioTab.ADJUST -> {
                    ManualAdjustPanel(
                        totalDurationMs = video.durationMs,
                        plan = editPlan,
                        onTrimChange = viewModel::updateTrimRange,
                        onSpeedChange = viewModel::updateSpeed,
                        onFilterChange = viewModel::updateFilter,
                        onBrightnessChange = viewModel::updateBrightness,
                        onContrastChange = viewModel::updateContrast,
                        onSaturationChange = viewModel::updateSaturation,
                        onMuteChange = viewModel::updateMute,
                        onOverlayTitleChange = viewModel::updateOverlayTitle,
                        onOverlayPositionChange = viewModel::updateOverlayPosition,
                        onRotate90 = viewModel::rotate90,
                        onAspectCropChange = viewModel::updateAspectCrop,
                        onReset = viewModel::resetEdits
                    )
                }
                StudioTab.EXPORTS -> {
                    ExportsPanel(
                        exportedVideos = exportedVideos,
                        onPlayExported = onPlayExported,
                        onShareExported = onShareExported
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportedPreviewOverlay(
    exported: ExportedVideo,
    onShare: () -> Unit,
    onClose: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("close_exported_preview_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver"
                            )
                        }
                    }
                    Column {
                        Text(
                            text = exported.title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Listo para compartir",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Button(
                    onClick = onShare,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("share_preview_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_share_video))
                }
            }

            VideoPreviewPlayer(
                videoUri = exported.uri,
                totalDurationMs = exported.durationMs,
                plan = VideoEditPlan.defaultForDuration(exported.durationMs),
                isExportPreview = true
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = exported.summary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
    }
}
