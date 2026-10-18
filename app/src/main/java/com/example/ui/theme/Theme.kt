package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class OfficeUiStyle(val label: String, val description: String) {
    CLASSIC_OFFICE("Office Cổ điển", "Thanh công cụ phân nhóm Ribbon đầy đủ (Home, Insert, Layout, Review, View)"),
    MODERN("Hiện đại", "Giao diện thẻ nổi thông minh, thanh công cụ ngữ cảnh gọn gàng"),
    MINIMAL("Tối giản", "Tập trung tối đa vào trang soạn thảo, ẩn công cụ phụ")
}

enum class DeviceFormFactorMode(val label: String) {
    AUTO("Tự động theo màn hình"),
    PHONE("Chế độ Điện thoại"),
    TABLET("Chế độ Máy tính bảng (Split/Rail)")
}

enum class ThemeBrightnessMode(val label: String) {
    SYSTEM("Theo hệ thống"),
    LIGHT("Chế độ Sáng"),
    DARK("Chế độ Tối")
}

enum class ToolbarPositionMode(val label: String) {
    TOP("Phía trên màn hình"),
    BOTTOM("Phía dưới (Thuận tay cầm điện thoại)")
}

enum class IconScaleMode(val label: String, val scaleDp: Int) {
    COMPACT("Nhỏ gọn (18dp)", 18),
    STANDARD("Tiêu chuẩn (22dp)", 22),
    LARGE("Lớn dễ chạm (26dp)", 26)
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: ThemeAccentColor = ThemeAccentColor.OFFICE_NAVY,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = accentColor.primaryDark,
            onPrimary = Color(0xFF081226),
            primaryContainer = accentColor.primaryLight.copy(alpha = 0.45f),
            onPrimaryContainer = Color(0xFFE2E8F0),
            secondary = NavySecondaryDark,
            tertiary = NavyTertiaryDark,
            background = SurfaceDark,
            surface = SurfaceCardDark,
            surfaceVariant = Color(0xFF1E293B),
            onBackground = Color(0xFFF1F5F9),
            onSurface = Color(0xFFF1F5F9),
            onSurfaceVariant = Color(0xFF94A3B8)
        )
    } else {
        lightColorScheme(
            primary = accentColor.primaryLight,
            onPrimary = Color.White,
            primaryContainer = accentColor.primaryLight.copy(alpha = 0.12f),
            onPrimaryContainer = accentColor.primaryLight,
            secondary = NavySecondaryLight,
            tertiary = NavyTertiaryLight,
            background = SurfaceLight,
            surface = SurfaceCardLight,
            surfaceVariant = Color(0xFFE2E8F0),
            onBackground = Color(0xFF0F172A),
            onSurface = Color(0xFF0F172A),
            onSurfaceVariant = Color(0xFF475569)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
