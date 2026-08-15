package com.anonrode.downloader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anonrode.downloader.ui.theme.*
import com.anonrode.downloader.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var url by remember { mutableStateOf(viewModel.apiClient.serverUrl) }
    var key by remember { mutableStateOf(viewModel.apiClient.apiKey) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var concurrent by remember { mutableStateOf(viewModel.engine.maxConcurrentDownloads) }
    var sockets by remember { mutableStateOf(viewModel.engine.parallelSocketsPerFile) }
    var minSplitSizeMb by remember { mutableStateOf(viewModel.engine.minSplitSizeMb) }
    var diskCacheMb by remember { mutableStateOf(viewModel.engine.diskCacheMb) }
    var fileAllocation by remember { mutableStateOf(viewModel.engine.fileAllocation) }
    var defaultQuality by remember { mutableStateOf(viewModel.engine.defaultQuality) }
    var autoOrganize by remember { mutableStateOf(viewModel.engine.autoOrganizeByShow) }
    var instantSocial by remember { mutableStateOf(viewModel.engine.instantSocialDownload) }
    var hapticFeedback by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkBackground,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(OverlayLight)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Power Settings",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ─────────────────────────────────────────────────────────────
            // 1. Network & Server Relay
            // ─────────────────────────────────────────────────────────────
            SettingsGroupCard(title = "Network & Server Relay", icon = Icons.Rounded.CloudQueue) {
                Text("VPS Server URL", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text("http://your-vps-ip:8000", color = TextMuted, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SealPrimary,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(Radius.md)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Master API Key", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    placeholder = { Text("Enter your API Key...", color = TextMuted, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                            Icon(
                                if (isKeyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Toggle Key Visibility",
                                tint = TextMuted
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SealPrimary,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(Radius.md)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Ping Status Badge & Test Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(if (uiState.isVpsOnline) EmeraldSuccess.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (uiState.isVpsOnline) "● Connected (${uiState.vpsLatencyMs}ms)" else "○ Server Offline",
                            color = if (uiState.isVpsOnline) EmeraldSuccess else ErrorRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = { viewModel.checkServerPing() },
                        colors = ButtonDefaults.buttonColors(containerColor = OverlayLighter, contentColor = SealPrimary),
                        shape = RoundedCornerShape(Radius.sm),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Test", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ─────────────────────────────────────────────────────────────
            // 2. Engine & Performance (Aria2c Power)
            // ─────────────────────────────────────────────────────────────
            SettingsGroupCard(title = "Engine & Performance (Aria2c)", icon = Icons.Rounded.Speed) {
                // Sockets per file
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Aria2c Sockets per File", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    Text("$sockets Sockets", color = SealPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Text("16 sockets matches Termux aria2c performance", color = TextMuted, fontSize = 11.sp)
                Slider(
                    value = sockets.toFloat(),
                    onValueChange = { sockets = it.toInt() },
                    valueRange = 1f..32f,
                    steps = 30,
                    colors = SliderDefaults.colors(thumbColor = SealPrimary, activeTrackColor = SealPrimary)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Max Concurrent Downloads
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Max Concurrent Downloads", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    Text("$concurrent Active", color = SealPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Slider(
                    value = concurrent.toFloat(),
                    onValueChange = { concurrent = it.toInt() },
                    valueRange = 1f..5f,
                    steps = 3,
                    colors = SliderDefaults.colors(thumbColor = SealPrimary, activeTrackColor = SealPrimary)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // RAM Disk Cache & Min Split Size
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("RAM Disk Cache", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        DropdownPill(
                            current = "${diskCacheMb} MB",
                            options = listOf("16 MB", "32 MB", "64 MB", "128 MB"),
                            onSelected = { diskCacheMb = it.replace(" MB", "").toInt() }
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Min Split Size", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        DropdownPill(
                            current = "${minSplitSizeMb} MB",
                            options = listOf("1 MB", "2 MB", "5 MB", "10 MB"),
                            onSelected = { minSplitSizeMb = it.replace(" MB", "").toInt() }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ─────────────────────────────────────────────────────────────
            // 3. Storage & File Organization
            // ─────────────────────────────────────────────────────────────
            SettingsGroupCard(title = "Storage & Organization", icon = Icons.Rounded.Folder) {
                Text("Download Directory", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(OverlayLighter)
                        .padding(10.dp)
                ) {
                    Text("/storage/emulated/0/Download/Anon/", color = Color.White, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-organize by Show Name", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Creates /Anon/{Show_Name}/ folder per series", color = TextMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = autoOrganize,
                        onCheckedChange = { autoOrganize = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = PureBlack, checkedTrackColor = SealPrimary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ─────────────────────────────────────────────────────────────
            // 4. Video & Automation
            // ─────────────────────────────────────────────────────────────
            SettingsGroupCard(title = "Video & Automation", icon = Icons.Rounded.AutoFixHigh) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Instant Social Download", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Skip preview and download immediately on link paste", color = TextMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = instantSocial,
                        onCheckedChange = { instantSocial = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = PureBlack, checkedTrackColor = SealPrimary)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text("Default Video Quality", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                DropdownPill(
                    current = when (defaultQuality) {
                        "best" -> "Best Available (1080p)"
                        "720p" -> "720p HD (Recommended)"
                        "480p" -> "480p (Data Saver)"
                        "audio" -> "Audio Only (MP3)"
                        else -> "720p HD (Recommended)"
                    },
                    options = listOf("Best Available (1080p)", "720p HD (Recommended)", "480p (Data Saver)", "Audio Only (MP3)"),
                    onSelected = {
                        defaultQuality = when {
                            it.contains("Best") -> "best"
                            it.contains("720p") -> "720p"
                            it.contains("480p") -> "480p"
                            else -> "audio"
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Save & Apply Button
            Button(
                onClick = {
                    viewModel.saveFullSettings(
                        url = url,
                        key = key,
                        concurrent = concurrent,
                        sockets = sockets,
                        minSplit = minSplitSizeMb,
                        diskCache = diskCacheMb,
                        fileAlloc = fileAllocation,
                        quality = defaultQuality,
                        autoOrg = autoOrganize,
                        instantSoc = instantSocial
                    )
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SealPrimary, contentColor = PureBlack),
                shape = RoundedCornerShape(Radius.lg)
            ) {
                Text("Save & Apply", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
fun SettingsGroupCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(SurfaceDark)
            .border(1.dp, CardBorder, RoundedCornerShape(Radius.lg))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = SealPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
fun DropdownPill(current: String, options: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(SurfaceHighlight)
            .clickable { expanded = true }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(current, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = TextMuted)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(SurfaceHighlight)
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt, color = Color.White, fontSize = 12.sp) },
                    onClick = {
                        onSelected(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}
