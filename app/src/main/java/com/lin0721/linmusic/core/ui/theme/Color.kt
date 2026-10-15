package com.lin0721.linmusic.core.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// 平滑抗色阶断层的垂直渐变
// 采用余弦缓动曲线采样，首尾斜率平滑过渡；透明渐变保留原色相避免发灰
fun Brush.Companion.smoothVerticalGradient(
    from: Color,
    to: Color,
    startY: Float = 0f,
    endY: Float = Float.POSITIVE_INFINITY,
    fadeEndFraction: Float = 1f,
    steps: Int = 24
): Brush {
    val effectiveTo = if (to == Color.Transparent) from.copy(alpha = 0f) else to
    val effectiveFrom = if (from == Color.Transparent) effectiveTo.copy(alpha = 0f) else from
    val clampedFadeEnd = fadeEndFraction.coerceIn(0f, 1f)

    val stops = Array(if (clampedFadeEnd < 1f) steps + 1 else steps) { i ->
        if (i < steps) {
            val t = i.toFloat() / (steps - 1)
            val factor = ((1.0 - cos(t * PI)) / 2.0).toFloat()
            val color = lerp(effectiveFrom, effectiveTo, factor)
            (t * clampedFadeEnd) to color
        } else {
            1f to effectiveTo
        }
    }

    return verticalGradient(
        colorStops = stops,
        startY = startY,
        endY = endY
    )
}

// HSV 明度按比例偏移，供取色/背景渲染各处按需现算深浅变体，不落地成持久字段。
// 用比例缩放而不是加减法再 coerceIn 夹断：暗色/灰阶封面明度本就 ≤ 0.35，
// 加减法一夹断就是绝对纯黑，色相全丢；比例缩放无论原明度多低都只是"更暗"，不会砸成死黑
fun Color.lighten(amount: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(), hsv)
    hsv[2] = (hsv[2] + (1f - hsv[2]) * amount).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

fun Color.darken(amount: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(), hsv)
    hsv[2] = (hsv[2] * (1f - amount)).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}



private const val RAD_TO_DEG = (180.0 / PI).toFloat()
private const val DEG_TO_RAD = (PI / 180.0).toFloat()

private fun cbrt(x: Float): Float =
    if (x < 0f) -(-x).pow(1f / 3f) else x.pow(1f / 3f)

private fun srgbToLinear(c: Float): Float =
    if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

private fun linearToSrgb(c: Float): Float =
    if (c <= 0.0031308f) 12.92f * c else 1.055f * c.pow(1f / 2.4f) - 0.055f

/** Color -> [L (0..1), C (>=0), H (0..360)] */
fun Color.toOklch(): FloatArray {
    val r = srgbToLinear(red)
    val g = srgbToLinear(green)
    val b = srgbToLinear(blue)

    val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
    val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
    val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)

    val L = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s
    val a = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
    val bb = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s

    val c = sqrt(a * a + bb * bb)
    val h = ((atan2(bb, a) * RAD_TO_DEG) % 360f + 360f) % 360f
    return floatArrayOf(L, c, h)
}

/** OKLCH -> 线性 sRGB（可能超出 0..1） */
private fun oklchToLinearRgb(L: Float, C: Float, H: Float): FloatArray {
    val hr = H * DEG_TO_RAD
    val a = C * cos(hr)
    val b = C * sin(hr)

    val l = (L + 0.3963377774f * a + 0.2158037573f * b).let { it * it * it }
    val m = (L - 0.1055613458f * a - 0.0638541728f * b).let { it * it * it }
    val s = (L - 0.0894841775f * a - 1.2914855480f * b).let { it * it * it }

    return floatArrayOf(
        +4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s,
        -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s,
        -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s
    )
}

private fun FloatArray.inGamut(eps: Float = 1e-4f) =
    all { it >= -eps && it <= 1f + eps }

/** 从 OKLCH 构建 Color；超出色域时保持 L、H，只收缩 C（而不是裁剪通道） */
fun Color.Companion.fromOklch(L: Float, C: Float, H: Float, alpha: Float = 1f): Color {
    val l = L.coerceIn(0f, 1f)
    val c0 = C.coerceAtLeast(0f)
    var rgb = oklchToLinearRgb(l, c0, H)

    if (!rgb.inGamut()) {
        var lo = 0f
        var hi = c0
        repeat(20) {
            val mid = (lo + hi) / 2f
            if (oklchToLinearRgb(l, mid, H).inGamut()) lo = mid else hi = mid
        }
        rgb = oklchToLinearRgb(l, lo, H)
    }

    return Color(
        red = linearToSrgb(rgb[0].coerceIn(0f, 1f)),
        green = linearToSrgb(rgb[1].coerceIn(0f, 1f)),
        blue = linearToSrgb(rgb[2].coerceIn(0f, 1f)),
        alpha = alpha.coerceIn(0f, 1f)
    )
}

/**
 * 基于 OKLCH 感知均匀色彩空间的干净减暗。
 * 相比传统的 HSV 明度直接乘以系数，OKLCH 保证在降低感知明度 L 的同时，
 * 色相 H 不发生偏移，并支持保持或补偿色度 C，避免暗部发灰、发脏。
 *
 * @param amount 0..1，越大越暗
 * @param chromaKeep amount = 1 时的色度倍率（1 = 保持原色度，>1 = 略微增饱和补偿暗处视觉衰减）
 * @param hueShift amount = 1 时的色相偏移（度），传 0 关闭
 */
fun Color.darkenOklch(
    amount: Float,
    chromaKeep: Float = 1.0f,
    hueShift: Float = 0f
): Color {
    val t = amount.coerceIn(0f, 1f)
    if (t == 0f) return this

    val lch = toOklch()
    val chromaScale = 1f + (chromaKeep - 1f) * t
    return Color.fromOklch(
        L = lch[0] * (1f - t),
        C = lch[1] * chromaScale.coerceAtLeast(0f),
        H = lch[2] + hueShift * t,
        alpha = alpha
    )
}

// HSV 饱和度按比例往纯色方向推；原色饱和度为 0（灰阶/黑白）时色相无意义，直接跳过，
// 否则会凭空编出一个色相方向的颜色
fun Color.saturate(amount: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(), hsv)
    if (hsv[1] <= 0f) return this
    hsv[1] = (hsv[1] + (1f - hsv[1]) * amount).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

// 只给有色 base 提饱和度：中性 base 只带 NEUTRAL_BASE_CHROMA 的轻微色调，提饱和度会放大成明显偏色；
// 两个色度档位之间线性过渡，切歌渐变的中间色不跳变
fun Color.saturateIfChromatic(amount: Float): Color {
    val chroma = maxOf(red, green, blue) - minOf(red, green, blue)
    val weight = ((chroma - NEUTRAL_BASE_CHROMA) / (MIN_BASE_CHROMA - NEUTRAL_BASE_CHROMA)).coerceIn(0f, 1f)
    return saturate(amount * weight)
}

val BackgroundDark = Color(0xFF121212)
val SurfaceDark = Color(0xFF282828)
val SurfaceLight = Color(0xFF3E3E3E)
val TextGray = Color(0xFFB3B3B3)

val NeteaseRed = Color(0xFFC20C0C)
val GradientStart = Color(0xFF3a1515)
val BackgroundBlack = Color(0xFF0a0a0a)

// 底栏选中项的药丸指示器底色
val NavPillSelected = Color(0xFF383A4A)

// 筛选药丸未选中态底色。取白 10% 叠在顶栏渐变上的等效不透明色
val FilterPillInactive = Color(0xFF332B2B)

// 全局 Toast 底色
val ToastBackground = Color(0xFF2E2E2E)

// 网页登录容器底色，与网页自身背景一致以避免键盘弹出时闪屏
val WebLoginBackground = Color(0xFFF5F5F7)

// 封面取色未命中缓存时的兜底背景色，迷你播放器与全屏播放器共用以保证配色一致
val FallbackBase = Color(0xFF333333)

// 歌单/歌手页在取色完成前的初始底色
val CoverPlaceholderDark = Color(0xFF2C2C2C)

// 定时关闭中的剩余时间告警色
val TimerWarningRed = Color(0xFFFF5252)

// 音乐库卡片的装饰渐变：紫罗兰 → 紫 → 粉
val LibraryVioletGradient = listOf(
    Color(0xFF6366F1),
    Color(0xFFA855F7),
    Color(0xFFEC4899)
)

// 音乐库卡片的装饰渐变：蓝 → 翠绿
val LibraryBlueGreenGradient = listOf(
    Color(0xFF3B82F6),
    Color(0xFF10B981)
)

// 已下载标识翠绿色
val DownloadedGreen = Color(0xFF10B981)

// 下载失败标识红色，比品牌红更亮，深色背景上作文字仍可读
val DownloadFailedRed = Color(0xFFFF6B6B)

// SVIP 标识金色
val SvipGold = Color(0xFFD4A017)

// 首页功能入口的策展渐变。心动模式、音乐漫游这类功能本就没有对应封面，
// 用固定色块表达
val EntryHotGradient = listOf(Color(0xFFF08C1A), Color(0xFF9C4A05))
val EntryHeartGradient = listOf(Color(0xFFA33FC4), Color(0xFF4B1268))
val EntryRadarGradient = listOf(Color(0xFF2F7FD6), Color(0xFF123F70))
val EntryRoamingGradient = listOf(Color(0xFF3FAE5C), Color(0xFF175C2C))
