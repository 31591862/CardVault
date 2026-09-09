package com.example.cardvault.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 全局配色：以强调蓝 AccentBlue 为 primary 的浅色方案。
 *
 * Material 3 默认主题的 primary 是紫色（#6750A4），按钮、开关、光标、选中描边、
 * 对话框确认键等全都会带紫。这里整体替换成设置页定下的蓝色系，
 * 所有引用 colorScheme.primary 的地方一次换干净，不要再在页面里散落硬编码紫色。
 */
private val CardVaultColorScheme = lightColorScheme(
    // 主色：强调蓝
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),   // 浅蓝容器（筛选 chip 等）
    onPrimaryContainer = Color(0xFF0B3FA0), // 深蓝文字/图标
    // 次色：蓝灰，避免默认的紫灰
    secondary = Color(0xFF565E71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBE2F9),
    onSecondaryContainer = Color(0xFF141B2C),
    // 三色：青蓝，避免默认的紫红 tertiary
    tertiary = Color(0xFF00696C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB4EBEC),
    onTertiaryContainer = Color(0xFF002021),
    // 界面底色：页面浅灰蓝，组件表面白色
    background = PageBg,
    onBackground = Color(0xFF1B1F27),
    surface = Color.White,
    onSurface = Color(0xFF1B1F27),
    surfaceVariant = Color(0xFFE3E8F1),     // 输入框等容器的中性浅灰蓝
    onSurfaceVariant = Color(0xFF6B7280),
    // surfaceContainer 系列：下拉菜单、AlertDialog 弹窗、底部弹层的底色。
    // M3 默认值带紫调（如 #F3EDF7），必须一并覆写成中性浅灰，否则弹层和页面色系脱节
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F9FC),
    surfaceContainer = Color(0xFFF2F5F9),
    surfaceContainerHigh = Color(0xFFECF0F6), // AlertDialog 默认取这档
    surfaceContainerHighest = Color(0xFFE6EAF1),
    outline = Color(0xFF8A9099),
    outlineVariant = Color(0xFFD5DAE3),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun CardVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CardVaultColorScheme,
        content = content
    )
}
