package com.anonrode.downloader.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anonrode.downloader.ui.theme.CardBorder
import com.anonrode.downloader.ui.theme.Radius
import com.anonrode.downloader.ui.theme.Spacing
import com.anonrode.downloader.ui.theme.SurfaceCard
import com.anonrode.downloader.ui.theme.TextPrimary

/**
 * The one card surface for the whole app.
 *
 * HomeScreen, DownloadsScreen and SubtitleHub each hand-rolled a
 * background + border + rounded-corner container with slightly different
 * numbers. That per-screen drift is what made the UI feel like several apps
 * stitched together. Everything routes through this now, so a card is a card.
 */
@Composable
fun AnonCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.md),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(Radius.md)
    var base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(SurfaceCard)
        .border(BorderStroke(1.dp, CardBorder), shape)
    if (onClick != null) base = base.clickable { onClick() }
    Column(modifier = base.padding(contentPadding), content = content)
}

/**
 * A consistent section header (title + optional trailing action/label).
 * Replaces the assorted one-off Row+Text headers, each with its own weight and
 * size, that appeared at the top of every screen section.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}
