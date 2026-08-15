package com.anonrode.downloader.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The single source of truth for spacing and corner radii.
 *
 * Before this existed the six screens hardcoded 13 different padding values and
 * 9 different corner radii, which is what made the app read as inconsistent /
 * "AI-generated." Everything now snaps to these scales:
 *
 *  - Spacing is a 4pt grid (4, 8, 12, 16, 24, 32).
 *  - Radius has three real steps plus a pill.
 */
object Spacing {
    val xs = 4.dp     // tight gaps inside a chip / between icon and label
    val sm = 8.dp     // default gap between stacked elements
    val md = 12.dp    // inner card padding, list item spacing
    val lg = 16.dp    // screen edge padding, card-to-card
    val xl = 24.dp    // section separation
    val xxl = 32.dp   // large empty-state / header breathing room
}

object Radius {
    val sm = 8.dp     // chips, small buttons, input fields
    val md = 12.dp    // cards, sheets, most surfaces
    val lg = 16.dp    // large cards, posters, modals
    val pill = 999.dp // fully rounded (filter pills, FAB, avatars)
}
