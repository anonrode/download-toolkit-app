package com.anonrode.downloader.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.anonrode.downloader.data.models.ShowItem
import com.anonrode.downloader.data.models.TaskStatus
import com.anonrode.downloader.ui.theme.*
import com.anonrode.downloader.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onOpenSocialModal: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val tasks by viewModel.engine.tasks.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    var showSuspiciousDialog by remember { mutableStateOf(false) }

    val filterOptions = listOf(
        "all" to "All Sites",
        "nkiri" to "Nkiri",
        "dramakey" to "DramaKey",
        "pluto" to "Pluto",
        "9jarocks" to "9jaRocks",
        "viki" to "Viki"
    )

    fun handleInput(text: String) {
        if (viewModel.apiClient.isDirectUrl(text)) {
            if (viewModel.engine.instantSocialDownload) {
                viewModel.engine.enqueue("Social", 1, "Video", text)
            } else {
                onOpenSocialModal(text)
            }
        } else if (viewModel.isSuspicious(text)) {
            showSuspiciousDialog = true
        } else {
            viewModel.performSearch()
        }
    }

    Scaffold(
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 1. Top Header (Seal Style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceDark)
                    .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ANONRODE",
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    letterSpacing = 1.2.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceElevated)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = String.format("%.1f GB Free", uiState.freeStorageGb),
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = onOpenSubtitles,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceElevated)
                ) {
                    Icon(Icons.Rounded.Subtitles, contentDescription = "Subtitles", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceElevated)
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = "Settings", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.width(8.dp))

                val activeCount = tasks.count { it.status == TaskStatus.DOWNLOADING || it.status == TaskStatus.QUEUED }
                IconButton(
                    onClick = onOpenDownloads,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (activeCount > 0) SealAccent.copy(alpha = 0.15f) else SurfaceElevated)
                ) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = "Downloads",
                        tint = if (activeCount > 0) SealAccent else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 2. Universal Search Capsule
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceDark)
                    .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))

                TextField(
                    value = uiState.query,
                    onValueChange = { viewModel.onQueryChanged(it) },
                    placeholder = { Text("Search drama or paste link...", color = TextMuted, fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                IconButton(
                    onClick = {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrBlank()) {
                            viewModel.onQueryChanged(clip)
                            handleInput(clip)
                        }
                    }
                ) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste", tint = TextMuted, modifier = Modifier.size(18.dp))
                }

                IconButton(
                    onClick = { handleInput(uiState.query) },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SealPrimary)
                ) {
                    Icon(Icons.Rounded.ArrowDownward, contentDescription = "Search", tint = PureBlack, modifier = Modifier.size(18.dp))
                }
            }

            // 3. Filter Chips Carousel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                filterOptions.forEach { (key, label) ->
                    val isSelected = uiState.selectedFilter == key
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) SealPrimary else SurfaceDark)
                            .border(1.dp, if (isSelected) SealPrimary else CardBorder, RoundedCornerShape(12.dp))
                            .clickable { viewModel.onFilterSelected(key) }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) PureBlack else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // 4. Drama Posters Grid
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    uiState.isSearching -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = SealAccent, strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Scanning sources...", color = TextMuted, fontSize = 13.sp)
                        }
                    }
                    uiState.searchError != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color.Red, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(uiState.searchError ?: "Error", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { viewModel.performSearch() },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated, contentColor = SealPrimary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Retry Search")
                            }
                        }
                    }
                    uiState.searchResults.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Rounded.MovieFilter, contentDescription = null, tint = Color(0x22FFFFFF), modifier = Modifier.size(54.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Search drama or paste a video link", color = TextMuted, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("NKiri • DramaKey • Pluto • 9jaRocks • Viki", color = TextMuted.copy(alpha = 0.5f), fontSize = 11.sp)
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(uiState.searchResults, key = { it.url }) { show ->
                                DramaPosterCard(show = show, onClick = { viewModel.openEpisodeDrawer(show) })
                            }
                        }
                    }
                }
            }

            // 5. Mini Bottom Download Bar (Seal style)
            val active = tasks.filter { it.status == TaskStatus.DOWNLOADING }
            if (active.isNotEmpty()) {
                val current = active.first()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceElevated)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                        .clickable { onOpenDownloads() }
                        .padding(12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${current.showName} • ${current.episodeTitle}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "● ${current.formattedSpeed}",
                                color = SealAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { current.progressPercent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = SealAccent,
                            trackColor = Color(0x1AFFFFFF)
                        )
                    }
                }
            }
        }
    }

    if (showSuspiciousDialog) {
        AlertDialog(
            onDismissRequest = { showSuspiciousDialog = false },
            containerColor = SurfaceDark,
            title = { Text("Confirm Search", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = { Text(""${uiState.query}" looks like random text. Do you want to search anyway?", color = TextSecondary, fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        showSuspiciousDialog = false
                        viewModel.performSearch()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SealPrimary, contentColor = PureBlack),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Search Anyway", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSuspiciousDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

@Composable
fun DramaPosterCard(show: ShowItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceDark)
            .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        if (!show.poster.isNullOrBlank()) {
            AsyncImage(
                model = show.poster,
                contentDescription = show.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Movie, contentDescription = null, tint = Color(0x22FFFFFF), modifier = Modifier.size(40.dp))
            }
        }

        // Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0x66000000), Color(0xF0000000)),
                        startY = 80f
                    )
                )
        )

        // Site Tag Top Right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xD9000000))
                .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(
                text = show.site.uppercase(),
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Title Bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        ) {
            Text(
                text = show.displayTitle,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.ellipsis,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (show.episodeCount > 0) "${show.episodeCount} Episodes" else "Episodes Available",
                color = TextSecondary,
                fontSize = 10.sp
            )
        }
    }
}
