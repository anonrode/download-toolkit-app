package com.anonrode.downloader.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.anonrode.downloader.data.models.DownloadTask
import com.anonrode.downloader.data.models.TaskStatus
import com.anonrode.downloader.ui.theme.*
import com.anonrode.downloader.viewmodel.MainViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val tasks by viewModel.engine.tasks.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }

    val active = tasks.filter { it.status != TaskStatus.COMPLETED }
    val completed = tasks.filter { it.status == TaskStatus.COMPLETED }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = { Text("Downloads Hub", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White) },
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
            // Storage Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceDark)
                    .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.SdStorage, contentDescription = null, tint = SealAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Internal Storage", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            String.format("%.1f GB Free of %.0f GB", uiState.freeStorageGb, uiState.totalStorageGb),
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val pct = if (uiState.totalStorageGb > 0) ((uiState.totalStorageGb - uiState.freeStorageGb) / uiState.totalStorageGb).toFloat() else 0.5f
                    LinearProgressIndicator(
                        progress = { pct.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = SealAccent,
                        trackColor = Color(0x1AFFFFFF)
                    )
                }
            }

            // Tab Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark)
                    .padding(4.dp)
            ) {
                TabButton("Active (${active.size})", isSelected = selectedTab == 0, modifier = Modifier.weight(1f)) { selectedTab = 0 }
                TabButton("Completed (${completed.size})", isSelected = selectedTab == 1, modifier = Modifier.weight(1f)) { selectedTab = 1 }
            }

            // List View
            if (selectedTab == 0) {
                if (active.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No active downloads", color = TextMuted, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(active, key = { it.id }) { task ->
                            ActiveDownloadCard(task, viewModel)
                        }
                    }
                }
            } else {
                if (completed.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No completed downloads yet", color = TextMuted, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(completed, key = { it.id }) { task ->
                            CompletedDownloadCard(task, viewModel, context)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TabButton(label: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) SealPrimary else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) PureBlack else TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ActiveDownloadCard(task: DownloadTask, viewModel: MainViewModel) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${task.showName}: ${task.episodeTitle}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (task.status) {
                        TaskStatus.DOWNLOADING -> "● ${task.formattedSpeed}"
                        TaskStatus.RESOLVING -> "Resolving..."
                        TaskStatus.PAUSED -> "Paused"
                        TaskStatus.QUEUED -> "Queued"
                        else -> "Failed"
                    },
                    color = if (task.status == TaskStatus.DOWNLOADING) SealAccent else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { task.progressPercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = SealAccent,
                trackColor = Color(0x1AFFFFFF)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${task.formattedSize} (${(task.progressPercent * 100).toInt()}%)",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = {
                        if (task.status == TaskStatus.DOWNLOADING) viewModel.engine.pause(task.id)
                        else viewModel.engine.resume(task.id)
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        if (task.status == TaskStatus.DOWNLOADING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Control",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = { viewModel.engine.cancel(task.id) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cancel", tint = Color.Red, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun CompletedDownloadCard(task: DownloadTask, viewModel: MainViewModel, context: android.content.Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(EmeraldSuccess.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(18.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${task.showName}: ${task.episodeTitle}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(task.formattedSize, color = TextMuted, fontSize = 11.sp)
        }

        Button(
            onClick = {
                try {
                    val file = File(task.targetFilePath)
                    if (file.exists()) {
                        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "video/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                    }
                } catch (_: Exception) {}
            },
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated, contentColor = SealPrimary),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Play", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.width(6.dp))

        IconButton(
            onClick = { viewModel.engine.deleteCompleted(task) },
            modifier = Modifier.size(30.dp)
        ) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(16.dp))
        }
    }
}
