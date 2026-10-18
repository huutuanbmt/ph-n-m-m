package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Brand & Office Module Colors
val WordBlue = Color(0xFF185ABD)
val WordBlueLight = Color(0xFFDbeafe)
val ExcelGreen = Color(0xFF107C41)
val ExcelGreenLight = Color(0xFFD1FAE5)
val PowerPointOrange = Color(0xFFC43E1C)
val PowerPointOrangeLight = Color(0xFFFFEDD5)
val PdfCrimson = Color(0xFFB30B00)
val PdfCrimsonLight = Color(0xFFFEE2E2)

// Theme Accent Palettes
val NavyPrimaryLight = Color(0xFF1A365D)
val NavySecondaryLight = Color(0xFF2B6CB0)
val NavyTertiaryLight = Color(0xFFD97706)
val SurfaceLight = Color(0xFFF8FAFC)
val SurfaceCardLight = Color(0xFFFFFFFF)

val NavyPrimaryDark = Color(0xFF93C5FD)
val NavySecondaryDark = Color(0xFF60A5FA)
val NavyTertiaryDark = Color(0xFFFBBF24)
val SurfaceDark = Color(0xFF0B132B)
val SurfaceCardDark = Color(0xFF162238)

// Accent Preset Colors
enum class ThemeAccentColor(val label: String, val primaryLight: Color, val primaryDark: Color) {
    OFFICE_NAVY("Xanh Navy Văn Phòng", Color(0xFF1A365D), Color(0xFF93C5FD)),
    WORD_SAPPHIRE("Xanh Word Sapphire", Color(0xFF185ABD), Color(0xFF60A5FA)),
    EXCEL_EMERALD("Xanh Lá Excel", Color(0xFF107C41), Color(0xFF34D399)),
    PPT_AMBER("Cam PowerPoint", Color(0xFFC43E1C), Color(0xFFFBBF24)),
    PDF_CRIMSON("Đỏ Ngọc PDF", Color(0xFFB30B00), Color(0xFFF87171))
}
