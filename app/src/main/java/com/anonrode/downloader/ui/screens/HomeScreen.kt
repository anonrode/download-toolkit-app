package com.anonrode.downloader.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.anonrode.downloader.data.models.ShowItem
import com.anonrode.downloader.data.models.TaskStatus
import com.anonrode.downloader.ui.theme.*
import com.anonrode.downloader.viewmodel.MainViewModel

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
    val keyboardController = LocalSoftwareKeyboardController.current

    val activeCount = tasks.count { it.status == TaskStatus.DOWNLOADING || it.status == TaskStatus.RESOLVING }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            // Premium Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Anon",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "High-Performance Media Downloader",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Subtitles Hub Icon
                IconButton(
                    onClick = onOpenSubtitles,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SurfaceElevated)
                ) {
                    Icon(Icons.Rounded.Subtitles, contentDescription = "Subtitles", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Downloads Badge Button
                IconButton(
                    onClick = onOpenDownloads,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (activeCount > 0) SealPrimary else SurfaceElevated)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = "Downloads",
                            tint = if (activeCount > 0) PureBlack else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Settings Button
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SurfaceElevated)
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 1. Universal Search / Link Capsule
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(Radius.lg))
                    .background(SurfaceCard)
                    .border(1.dp, CardBorder, RoundedCornerShape(Radius.lg))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                TextField(
                    value = uiState.query,
                    onValueChange = { viewModel.onQueryChanged(it) },
                    placeholder = {
                        Text(
                            "Search drama or paste video link...",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboardController?.hide()
                            if (viewModel.apiClient.isDirectUrl(uiState.query)) {
                                onOpenSocialModal(uiState.query.trim())
                            } else {
                                viewModel.performSearch()
                            }
                        }
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                if (uiState.query.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.onQueryChanged("") },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                } else {
                    // Quick Paste from Clipboard
                    IconButton(
                        onClick = {
                            val clip = clipboardManager.getText()?.text ?: ""
                            if (clip.isNotBlank()) {
                                viewModel.onQueryChanged(clip)
                                if (viewModel.apiClient.isDirectUrl(clip)) {
                                    onOpenSocialModal(clip.trim())
                                }
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Search / Go Button
                Button(
                    onClick = {
                        keyboardController?.hide()
                        if (viewModel.apiClient.isDirectUrl(uiState.query)) {
                            onOpenSocialModal(uiState.query.trim())
                        } else {
                            viewModel.performSearch()
                        }
                    },
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(Radius.md),
                    colors = ButtonDefaults.buttonColors(containerColor = SealPrimary, contentColor = PureBlack),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Text("Search", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            // 2. Filter Pills
            val filters = listOf("all" to "All", "nkiri" to "NKiri", "dramakey" to "DramaKey", "pluto" to "Pluto", "9jarocks" to "9jaRocks", "viki" to "Viki")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                filters.forEach { (key, label) ->
                    val isSelected = uiState.selectedFilter == key
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(Radius.md))
                            .background(if (isSelected) SealPrimary else SurfaceCard)
                            .border(1.dp, if (isSelected) SealPrimary else CardBorder, RoundedCornerShape(Radius.md))
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

            // 3. Main Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    uiState.isSearching -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = SealPrimary, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(14.dp))
                                Text("Searching dramas & resolving index...", color = TextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                    uiState.searchError != null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = TextMuted, modifier = Modifier.size(44.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(uiState.searchError ?: "Search failed", color = TextSecondary, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = onOpenSettings,
                                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated, contentColor = SealPrimary),
                                    shape = RoundedCornerShape(Radius.sm)
                                ) {
                                    Text("Open Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    uiState.searchResults.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.MovieFilter, contentDescription = null, tint = OverlayLight, modifier = Modifier.size(56.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Search drama series or paste a link", color = TextMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("NKiri • DramaKey • Pluto • 9jaRocks • Viki", color = TextMuted.copy(alpha = 0.5f), fontSize = 11.sp)
                            }
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(uiState.searchResults, key = { it.url.ifBlank { it.title } }) { show ->
                                DramaPosterCard(show = show, onClick = { viewModel.openEpisodeDrawer(show) })
                            }
                        }
                    }
                }
            }

            // 4. Floating Mini Download Bar (Seal Style)
            val active = tasks.filter { it.status == TaskStatus.DOWNLOADING || it.status == TaskStatus.RESOLVING }
            if (active.isNotEmpty()) {
                val current = active.first()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(Radius.lg))
                        .background(SurfaceElevated)
                        .border(1.dp, CardBorder, RoundedCornerShape(Radius.lg))
                        .clickable { onOpenDownloads() }
                        .padding(14.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${current.showName} • ${current.episodeTitle}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (current.formattedSpeed.isNotBlank()) "● ${current.formattedSpeed}" else "● Connecting...",
                                color = SealPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { current.progressPercent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(Radius.sm)),
                            color = SealPrimary,
                            trackColor = OverlayLighter
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DramaPosterCard(show: ShowItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(0.70f)
            .clip(RoundedCornerShape(Radius.lg))
            .background(SurfaceCard)
            .border(1.dp, CardBorder, RoundedCornerShape(Radius.lg))
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
                Icon(Icons.Rounded.Movie, contentDescription = null, tint = OverlayLight, modifier = Modifier.size(36.dp))
            }
        }

        // Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    // Poster scrim: a designed 3-stop black ramp for title
                    // legibility over artwork, not a themeable surface color.
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0x66000000), Color(0xFA000000)),
                        startY = 90f
                    )
                )
        )

        // Site Tag Top Right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(Scrim)
                .border(1.dp, CardBorder, RoundedCornerShape(Radius.sm))
                .padding(horizontal = 7.dp, vertical = 3.dp)
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
                overflow = TextOverflow.Ellipsis,
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
