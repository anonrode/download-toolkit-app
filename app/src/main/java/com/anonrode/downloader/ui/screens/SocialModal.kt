package com.anonrode.downloader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.anonrode.downloader.ui.theme.*
import com.anonrode.downloader.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialModal(
    url: String,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var selectedQuality by remember { mutableStateOf(viewModel.engine.defaultQuality) }
    val isAudio = selectedQuality == "audio"

    val (platformName, platformIcon) = when {
        url.contains("instagram.com") -> "Instagram Reel" to Icons.Rounded.CameraAlt
        url.contains("youtube.com") || url.contains("youtu.be") -> "YouTube Video" to Icons.Rounded.PlayArrow
        url.contains("tiktok.com") -> "TikTok Video" to Icons.Rounded.MusicNote
        url.contains("twitter.com") || url.contains("x.com") -> "Twitter / X Video" to Icons.Rounded.Share
        else -> "Direct Media Stream" to Icons.Rounded.Link
    }

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
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(platformIcon, contentDescription = null, tint = SealAccent, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Quick Download", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(platformName, color = SealAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // URL Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark)
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = url,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.ellipsis
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Format & Quality", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))

            val qualityOptions = listOf(
                "best" to "Best Quality (1080p / High bitrate)",
                "720p" to "720p HD (Fast & Balanced)",
                "480p" to "480p (Data Saver)",
                "audio" to "Extract Audio Only (MP3)"
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                qualityOptions.forEach { (key, label) ->
                    val isSelected = selectedQuality == key
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) SurfaceElevated else SurfaceDark)
                            .border(1.dp, if (isSelected) SealAccent else CardBorder, RoundedCornerShape(12.dp))
                            .clickable { selectedQuality = key }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedQuality = key },
                            colors = RadioButtonDefaults.colors(selectedColor = SealAccent, unselectedColor = TextMuted)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else TextSecondary,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    viewModel.engine.enqueue(
                        showName = "Social",
                        episodeNumber = 1,
                        episodeTitle = if (isAudio) "$platformName Audio" else "$platformName Video",
                        originalUrl = url
                    )
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SealPrimary, contentColor = PureBlack),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Download Now", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
