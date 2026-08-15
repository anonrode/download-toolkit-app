package com.anonrode.downloader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anonrode.downloader.data.models.SubtitleItem
import com.anonrode.downloader.ui.theme.*
import com.anonrode.downloader.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleHubScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var subQuery by remember { mutableStateOf(viewModel.uiState.value.query) }
    var subs by remember { mutableStateOf<List<SubtitleItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun searchSubs() {
        if (subQuery.isBlank()) return
        scope.launch {
            isLoading = true
            subs = viewModel.apiClient.searchSubtitles(subQuery.trim())
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        if (subQuery.isNotBlank()) searchSubs()
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = { Text("Subtitle Hub & Matcher", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(SurfaceDark)
                    .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Subtitles, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                TextField(
                    value = subQuery,
                    onValueChange = { subQuery = it },
                    placeholder = { Text("Search drama subtitles (e.g. Vincenzo)...", color = TextMuted, fontSize = 13.sp) },
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
                IconButton(onClick = { searchSubs() }) {
                    Icon(Icons.Rounded.Search, contentDescription = "Search", tint = ElectricCyan)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Results List
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ElectricCyan, strokeWidth = 3.dp)
                    }
                } else if (subs.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Rounded.Subtitles, contentDescription = null, tint = Color(0x22FFFFFF), modifier = Modifier.size(54.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Search English Season Packs & Viki Subtitles", color = TextMuted, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(subs) { sub ->
                            SubtitleCard(sub = sub, onDownload = {
                                viewModel.engine.enqueue(
                                    showName = subQuery.ifBlank { "Subtitles" },
                                    episodeNumber = 1,
                                    episodeTitle = sub.name.ifBlank { "Subtitle Pack" },
                                    originalUrl = sub.download_url
                                )
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubtitleCard(sub: SubtitleItem, onDownload: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (sub.type == "pack") EmeraldGreen.copy(alpha = 0.15f) else ElectricCyan.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (sub.type == "pack") Icons.Rounded.FolderZip else Icons.Rounded.Subtitles,
                contentDescription = null,
                tint = if (sub.type == "pack") EmeraldGreen else ElectricCyan,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sub.name.ifBlank { "Season Complete Subtitle Pack" },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${sub.source.uppercase()} • ${sub.lang.uppercase()} • ${if (sub.episodes_count > 0) "${sub.episodes_count} Episodes" else "Complete"}",
                color = TextMuted,
                fontSize = 11.sp
            )
        }

        Button(
            onClick = onDownload,
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan.copy(alpha = 0.15f), contentColor = ElectricCyan),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Get", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
