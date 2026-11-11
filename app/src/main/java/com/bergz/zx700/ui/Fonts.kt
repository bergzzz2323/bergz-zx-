package com.bergz.zx700.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.bergz.zx700.R

/** Panel fonts (SIL OFL), fetched at build time. See app/build.gradle.kts. */
object PanelFonts {
    /** Screen-printed labels and titles. */
    val Condensed = FontFamily(
        Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
        Font(R.font.barlow_condensed_bold, FontWeight.Bold),
        Font(R.font.barlow_condensed_extrabold_italic, FontWeight.ExtraBold, FontStyle.Italic),
    )

    /** The amber display. */
    val Display = FontFamily(Font(R.font.vt323_regular))
}
