package com.example.cardvault.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * 银行品牌色。用于首页卡片墙的卡面底色，让你一眼分辨是哪家的卡。
 *
 * 匹配规则：按关键词包含匹配，越具体的排越前面（"建设"必须排在"中国"前面，
 * 否则"中国建设银行"会被误判成中国银行）。
 * 没匹配到的银行按名字哈希生成一个固定色，保证同一家银行每次打开颜色一致。
 */
data class BankPalette(val base: Color, val deep: Color)

private val KNOWN_BANKS: List<Pair<String, Long>> = listOf(
    "建设" to 0xFF003B8D,
    "工商" to 0xFFC8102E,
    "农业" to 0xFF00843D,
    "农商" to 0xFF00843D,
    "农村" to 0xFF00843D,
    "信用" to 0xFF00843D,
    "中国" to 0xFFA71E32,
    "交通" to 0xFF0F4C9E,
    "招商" to 0xFFC7000B,
    "邮储" to 0xFF007A33,
    "邮政" to 0xFF007A33,
    "浦发" to 0xFF00447C,
    "中信" to 0xFFC8102E,
    "民生" to 0xFF005BAC,
    "光大" to 0xFF6A1B7A,
    "平安" to 0xFFE2620B,
    "兴业" to 0xFF003A70,
    "华夏" to 0xFFB01F24,
    "广发" to 0xFFE2231A,
    "浙商" to 0xFFC8102E,
    "恒丰" to 0xFFC8102E,
    "渤海" to 0xFF005BAC,
    "北京" to 0xFFC8102E,
    "上海" to 0xFF00447C,
    "江苏" to 0xFFC8102E,
    "宁波" to 0xFF005BAC,
    "南京" to 0xFFE4002B,
    "杭州" to 0xFF005BAC,
    "花旗" to 0xFF056DAE,
    "汇丰" to 0xFFDB0011,
    "渣打" to 0xFF047DB2,
    "星展" to 0xFFEE3E34
)

fun paletteFor(bankName: String): BankPalette {
    val name = bankName.trim()
    for ((keyword, color) in KNOWN_BANKS) {
        if (name.contains(keyword)) {
            val base = Color(color)
            return BankPalette(base = base, deep = base.shade(0.35f))
        }
    }
    // 未知银行：用名字哈希出一个稳定的色相，避免每次打开颜色乱跳
    val hue = (abs(name.hashCode()) % 360).toFloat()
    return BankPalette(
        base = Color.hsv(hue, 0.52f, 0.78f),
        deep = Color.hsv(hue, 0.66f, 0.48f)
    )
}

/** 压暗：把 RGB 各通道按比例往黑拉，用来做卡面渐变的下半段 */
private fun Color.shade(amount: Float): Color = copy(
    red = red * (1f - amount),
    green = green * (1f - amount),
    blue = blue * (1f - amount),
    alpha = 1f
)
