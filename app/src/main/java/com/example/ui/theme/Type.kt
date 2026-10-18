package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val BeVietnamProFontFamily = FontFamily(
    Font(R.font.be_vietnam_pro, FontWeight.Normal),
    Font(R.font.be_vietnam_pro, FontWeight.Medium),
    Font(R.font.be_vietnam_pro, FontWeight.Bold)
)

val TinosFontFamily = FontFamily(
    Font(R.font.tinos, FontWeight.Normal),
    Font(R.font.tinos, FontWeight.Bold)
)

val ArimoFontFamily = FontFamily(
    Font(R.font.arimo, FontWeight.Normal),
    Font(R.font.arimo, FontWeight.Bold)
)

val CarlitoFontFamily = FontFamily(
    Font(R.font.carlito, FontWeight.Normal),
    Font(R.font.carlito, FontWeight.Bold)
)

val NotoSerifFontFamily = FontFamily(
    Font(R.font.noto_serif, FontWeight.Normal),
    Font(R.font.noto_serif, FontWeight.Bold)
)

val RobotoMonoFontFamily = FontFamily(
    Font(R.font.roboto_mono, FontWeight.Normal),
    Font(R.font.roboto_mono, FontWeight.Medium)
)

fun resolveComposeFontFamily(fontName: String): FontFamily {
    val normalized = fontName.trim().lowercase()
    return when {
        normalized.contains("times") || normalized.contains("tinos") -> TinosFontFamily
        normalized.contains("arial") || normalized.contains("arimo") -> ArimoFontFamily
        normalized.contains("calibri") || normalized.contains("carlito") || normalized.contains("aptos") -> CarlitoFontFamily
        normalized.contains("cambria") || normalized.contains("noto serif") || normalized.contains("serif") -> NotoSerifFontFamily
        normalized.contains("mono") || normalized.contains("consolas") || normalized.contains("courier") -> RobotoMonoFontFamily
        else -> BeVietnamProFontFamily
    }
}

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = BeVietnamProFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = BeVietnamProFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = BeVietnamProFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = BeVietnamProFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = BeVietnamProFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = BeVietnamProFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = BeVietnamProFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = BeVietnamProFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
)
