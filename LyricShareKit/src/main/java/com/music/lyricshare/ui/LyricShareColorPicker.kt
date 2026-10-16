package com.music.lyricshare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * ============================================================================
 *  自选色（HSL）
 * ============================================================================
 *  色卡行最右边那个"彩虹轮"就是入口，皮肤在颜色控件上写 `"allowCustom": true` 才会出现。
 *  面板：色相 / 饱和度 / 明度 / 不透明度 + 实时预览 + 十六进制值，确定后写回同一个颜色属性
 *  （`color` 或 `background`），与预设色卡完全同路，所以也会被持久化。
 */

/** 自选色入口：彩虹色轮。 */
@Composable
internal fun CustomColorSwatch(
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalLyricShareTokens.current
    // 13 个色相点组成一圈彩虹（首尾同色，接缝不会跳）
    val hues = remember { (0..12).map { Color.hsl(it * 30f, 1f, 0.5f) } }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Brush.sweepGradient(hues))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) tokens.primary else tokens.outline,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 中间留个浅色小孔，看起来像取色盘
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(tokens.surfaceContainerHigh),
        )
    }
}

/**
 * 自选色面板。
 *
 * @param initial 当前颜色（null = 还没设过，用白色起步）
 */
@Composable
internal fun ColorPickerDialog(
    title: String,
    initial: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    /** 额外提供一个"跟随播放页"快捷色（宿主传了取色时才有）。 */
    playerColor: Int? = null,
) {
    val tokens = LocalLyricShareTokens.current
    val start = remember(initial) { rgbToHsl(initial ?: 0xFFFFFFFF.toInt()) }
    var hue by remember { mutableStateOf(start[0]) }
    var saturation by remember { mutableStateOf(start[1]) }
    var lightness by remember { mutableStateOf(start[2]) }
    var alpha by remember { mutableStateOf(start[3]) }
    val picked = Color.hsl(hue, saturation, lightness, alpha)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacingMd)
                .clip(RoundedCornerShape(tokens.sheetRadius))
                .background(tokens.surfaceContainer)
                .padding(tokens.spacingLg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = LyricShareTextStyles.title(tokens), modifier = Modifier.weight(1f))
                // 快捷：回到"跟随播放页"（宿主传了取色才有）
                playerColor?.let { pc ->
                    TextButton(onClick = { onConfirm(pc) }) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = tokens.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(tokens.spacingXs))
                        Text("播放页", color = tokens.primary, fontSize = tokens.textBody)
                    }
                }
            }
            Spacer(Modifier.height(tokens.spacingMd))

            // 预览 + 十六进制
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(tokens.radiusSm))
                        .background(picked, RoundedCornerShape(tokens.radiusSm))
                        .border(1.dp, tokens.outline, RoundedCornerShape(tokens.radiusSm)),
                )
                Spacer(Modifier.width(tokens.spacingMd))
                Text(
                    text = hexOf(picked.toArgb()),
                    color = tokens.onSurface,
                    fontSize = tokens.textBody,
                )
            }
            Spacer(Modifier.height(tokens.spacingMd))

            // ---- 色相（0~360）
            LabeledSlider(
                label = "色相",
                valueText = "${hue.toInt()}°",
                value = hue,
                range = 0f..360f,
                onValueChange = { hue = it },
                gradient = Brush.horizontalGradient((0..12).map { Color.hsl(it * 30f, 1f, 0.5f) }),
            )
            // ---- 饱和度（0~1）
            LabeledSlider(
                label = "饱和度",
                valueText = "${(saturation * 100).toInt()}%",
                value = saturation,
                range = 0f..1f,
                onValueChange = { saturation = it },
                gradient = Brush.horizontalGradient(
                    listOf(Color.hsl(hue, 0f, lightness), Color.hsl(hue, 1f, lightness)),
                ),
            )
            // ---- 明度（0~1）
            LabeledSlider(
                label = "明度",
                valueText = "${(lightness * 100).toInt()}%",
                value = lightness,
                range = 0f..1f,
                onValueChange = { lightness = it },
                gradient = Brush.horizontalGradient(
                    listOf(Color.Black, Color.hsl(hue, saturation, 0.5f), Color.White),
                ),
            )
            // ---- 不透明度（0~1）
            LabeledSlider(
                label = "不透明度",
                valueText = "${(alpha * 100).toInt()}%",
                value = alpha,
                range = 0f..1f,
                onValueChange = { alpha = it },
                gradient = Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.hsl(hue, saturation, lightness)),
                ),
            )

            Spacer(Modifier.height(tokens.spacingSm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = tokens.onSurfaceVariant)
                }
                Spacer(Modifier.width(tokens.spacingSm))
                TextButton(onClick = { onConfirm(picked.toArgb()) }) {
                    Text("确定", color = tokens.primary)
                }
            }
        }
    }
}

/** 带渐变色条 + 数值的滑杆。 */
@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    gradient: Brush,
) {
    val tokens = LocalLyricShareTokens.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = tokens.onSurface, fontSize = tokens.textBody, modifier = Modifier.weight(1f))
            Text(valueText, color = tokens.onSurfaceVariant, fontSize = tokens.textLabel)
        }
        // 渐变色条（示意取值范围）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(gradient),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = androidx.compose.material3.SliderDefaults.colors(
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

// ---------------------------------------------------------------- 颜色换算

/**
 * ARGB → [色相(0~360), 饱和度(0~1), 明度(0~1), 不透明度(0~1)]。
 * Compose 没有现成的 toHsl，这里按标准公式算。
 */
internal fun rgbToHsl(argb: Int): FloatArray {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val a = ((argb ushr 24) and 0xFF) / 255f
    val maxV = max(r, max(g, b))
    val minV = min(r, min(g, b))
    val l = (maxV + minV) / 2f
    if (maxV == minV) return floatArrayOf(0f, 0f, l, a)
    val d = maxV - minV
    val s = if (l > 0.5f) d / (2f - maxV - minV) else d / (maxV + minV)
    var h = when (maxV) {
        r -> (g - b) / d + (if (g < b) 6f else 0f)
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    } * 60f
    if (h < 0f) h += 360f
    return floatArrayOf(h, s, l, a)
}

/** ARGB → `#RRGGBB`（不透明度不足 100% 时附注）。 */
internal fun hexOf(argb: Int): String {
    val rgb = String.format("#%06X", argb and 0xFFFFFF)
    val a = (argb ushr 24) and 0xFF
    return if (a == 0xFF) rgb else "$rgb · ${(a * 100 / 255)}%"
}

/** 两个颜色在 UI 上是否"看起来一样"（用于色卡的选中态）。 */
internal fun sameColor(a: Int?, b: Int?): Boolean {
    if (a == null || b == null) return false
    if (a == b) return true
    // 允许 ±1 的舍入误差（HSL↔RGB 往返可能差 1）
    val da = abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) +
        abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) +
        abs((a and 0xFF) - (b and 0xFF))
    return da <= 3 && ((a ushr 24) and 0xFF) == ((b ushr 24) and 0xFF)
}
