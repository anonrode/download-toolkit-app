package com.anonrode.downloader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anonrode.downloader.data.models.ShowItem
import com.anonrode.downloader.ui.theme.*
import com.anonrode.downloader.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeDrawer(
    show: ShowItem,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val episodes = uiState.drawerEpisodes
    val selectedCount = episodes.count { it.isSelected }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkBackground,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x33FFFFFF))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
        ) {
            // Header Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = show.displayTitle,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${show.site.uppercase()} • ${episodes.size} Episodes",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            // Quick Batch Range Pills
            if (episodes.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    BatchChip("All (${episodes.size})") { viewModel.applyBatchSelection("all") }
                    BatchChip("Deselect") { viewModel.applyBatchSelection("none") }
                    if (episodes.size > 8) {
                        BatchChip("Ep 1-8") { viewModel.applyBatchSelection("1-8") }
                        BatchChip("Ep 9-16") { viewModel.applyBatchSelection("9-16") }
                    }
                    if (episodes.size > 16) {
                        BatchChip("Ep 17-24") { viewModel.applyBatchSelection("17-24") }
                    }
                }
            }

            Divider(color = CardBorder, thickness = 1.dp)

            // Episode List
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (uiState.isEpisodesLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SealAccent, strokeWidth = 3.dp)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 90.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(episodes, key = { it.episode }) { ep ->
                            val isDownloaded = viewModel.engine.isEpisodeDownloaded(show.displayTitle, ep.episode)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (ep.isSelected) SurfaceElevated else SurfaceDark)
                                    .border(1.dp, if (ep.isSelected) SealAccent else CardBorder, RoundedCornerShape(14.dp))
                                    .clickable { viewModel.toggleEpisodeSelection(ep) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Squircle Badge
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (ep.isSelected) SealPrimary else SurfaceElevated),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = String.format("%02d", ep.episode),
                                        color = if (ep.isSelected) PureBlack else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ep.displayTitle,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "720p HD",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }

                                if (isDownloaded) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(EmeraldSuccess.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(text = "Saved ✓", color = EmeraldSuccess, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    IconButton(
                                        onClick = { viewModel.downloadSingleEpisode(show, ep) }
                                    ) {
                                        Icon(
                                            if (ep.isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                                            contentDescription = "Download",
                                            tint = if (ep.isSelected) SealAccent else TextMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Sticky Bottom Download Action
                if (selectedCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Button(
                            onClick = { viewModel.downloadSelectedEpisodes(show) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SealPrimary, contentColor = PureBlack),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Download Selected ($selectedCount Episodes)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BatchChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = label, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
