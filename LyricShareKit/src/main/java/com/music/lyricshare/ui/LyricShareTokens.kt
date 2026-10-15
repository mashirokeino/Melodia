package com.music.lyricshare.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ============================================================================
 *  视觉 Token —— 本模块所有 UI 尺寸/颜色/字号只允许从这里取
 * ============================================================================
 *  这就是「方便进行视觉统一」的落点：页面里没有任何魔法数字，
 *  想和宿主设计系统对齐，只需要改这一个类（或在调用处传入自定义 tokens）。
 *
 *  默认值刻意与 Melodia 现有设计系统**一一对应**：
 *     spacingXxs/Xs/Sm/Md/Lg/Xl  == core.ui.theme.MelodiaSpacing (2/4/8/16/24/32)
 *     radiusXs/Sm/Md/Lg          == core.ui.theme.MelodiaShapes   (4/8/16/24)
 *     radiusPill                 == core.ui.theme.PillRadius      (20)
 *     sheetRadius                == core.ui.theme.BottomSheetShape 顶部圆角 (24)
 *     textTitle/Body/Label       == Typography / 分享头标题体系 (20/14/12)
 *
 *  接入 Melodia 时有两条路（都零成本）：
 *    A. 直接用默认值 —— 已与 Melodia 对齐，视觉上就是同一套；
 *    B. 想跟着主题走（例如随封面取色变化），在调用处包一层：
 *         LyricShareTheme(tokens = LyricShareTokens.Default.copy(primary = myDynamicColor)) { ... }
 */
@Immutable
data class LyricShareTokens(
    // ---------------------------------------------------------------- 颜色
    /** 页面底色。 */
    val surface: Color = Color(0xFF101014),
    /** 卡片/底部操作区底色。 */
    val surfaceContainer: Color = Color(0xFF1A1A20),
    /** 选中态、输入类控件底色。 */
    val surfaceContainerHigh: Color = Color(0xFF26262E),
    /** 主文字。 */
    val onSurface: Color = Color(0xFFEDEDF2),
    /** 次级文字（皮肤名、说明文案）。 */
    val onSurfaceVariant: Color = Color(0xFF9A9AA6),
    /** 描边 / 分割线（半透明白，压在任何背景上都成立）。 */
    val outline: Color = Color(0x33FFFFFF),
    /** 品牌主色；预览卡片描边、主按钮用它。 */
    val primary: Color = Color(0xFFFF3B4E),
    val onPrimary: Color = Color.White,
    /** 提示/失败态。 */
    val error: Color = Color(0xFFFF6B6B),

    // ---------------------------------------------------------------- 间距
    val spacingXxs: Dp = 2.dp,
    val spacingXs: Dp = 4.dp,
    val spacingSm: Dp = 8.dp,
    val spacingMd: Dp = 16.dp,
    val spacingLg: Dp = 24.dp,
    val spacingXl: Dp = 32.dp,

    // ---------------------------------------------------------------- 圆角
    val radiusXs: Dp = 4.dp,
    val radiusSm: Dp = 8.dp,
    val radiusMd: Dp = 16.dp,
    val radiusLg: Dp = 24.dp,
    /** 胶囊按钮圆角。 */
    val radiusPill: Dp = 20.dp,
    /** BottomSheet 顶部圆角。 */
    val sheetRadius: Dp = 24.dp,

    // ---------------------------------------------------------------- 字号
    val textTitle: TextUnit = 20.sp,
    val textBody: TextUnit = 14.sp,
    val textLabel: TextUnit = 12.sp,

    // ---------------------------------------------------------------- 其它
    /** 顶部栏高度，与 Melodia 的 FullScreenShareHeader 一致（56dp）。 */
    val headerHeight: Dp = 56.dp,
) {
    companion object {
        /** 默认（暗色，与 Melodia 播放页/歌词分享页同一套观感）。 */
        val Default = LyricShareTokens()

        /**
         * 让页面底色**跟随播放页的取色结果**。
         *
         * 宿主把播放页背景色放进 [com.music.lyricshare.LyricShareRequest.baseColor]
         * （Melodia 的 PlayerBackdropPalette 就是这个值），这里把它压暗成页面底色并派生出容器色，
         * 这样「歌词图片」页与歌词页是同一套色感，不会一个深灰一个纯黑。
         * 传 null / 0 → 用默认深色。
         */
        fun forBackdrop(baseColor: Int?): LyricShareTokens {
            if (baseColor == null || baseColor == 0) return Default
            // 取色可能很亮，压暗到约 28% 保证白字对比度
            val surface = lerp(Color(baseColor), Color.Black, 0.72f)
            return Default.copy(
                // 页面层级：底色 → 容器 → 高亮容器
                surface = surface,
                surfaceContainer = lerp(surface, Color.White, 0.06f),
                surfaceContainerHigh = lerp(surface, Color.White, 0.14f),
                // 文字与描边也带一点播放页的色相，整页观感统一（不是纯黑配纯白）
                onSurface = lerp(baseColor.let { Color(it) }, Color.White, 0.92f),
                onSurfaceVariant = lerp(baseColor.let { Color(it) }, Color.White, 0.62f),
                outline = lerp(baseColor.let { Color(it) }, Color.White, 0.30f).copy(alpha = 0.30f),
                // 强调色（选中的皮肤描边等）：把播放页颜色提亮，保证在深底上够醒目
                primary = lerp(baseColor.let { Color(it) }, Color.White, 0.28f),
            )
        }
    }
}

/** 通过 CompositionLocal 往下传，子组件不用层层透传参数。 */
val LocalLyricShareTokens = staticCompositionLocalOf { LyricShareTokens.Default }

/**
 * 页面主题：把 tokens 同时喂给 MaterialTheme（让 Button/Switch 等控件自动跟随）
 * 和 LocalLyricShareTokens（让自定义组件拿到同一套值）。
 */
@Composable
fun LyricShareTheme(
    tokens: LyricShareTokens = LyricShareTokens.Default,
    content: @Composable () -> Unit,
) {
    val colorScheme = darkColorScheme(
        primary = tokens.primary,
        onPrimary = tokens.onPrimary,
        background = tokens.surface,
        onBackground = tokens.onSurface,
        surface = tokens.surfaceContainer,
        onSurface = tokens.onSurface,
        surfaceVariant = tokens.surfaceContainerHigh,
        onSurfaceVariant = tokens.onSurfaceVariant,
        outline = tokens.outline,
        error = tokens.error,
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(tokens.radiusXs),
        small = RoundedCornerShape(tokens.radiusSm),
        medium = RoundedCornerShape(tokens.radiusMd),
        large = RoundedCornerShape(tokens.radiusLg),
        extraLarge = RoundedCornerShape(tokens.sheetRadius),
    )
    CompositionLocalProvider(LocalLyricShareTokens provides tokens) {
        MaterialTheme(colorScheme = colorScheme, shapes = shapes, content = content)
    }
}

/** 常用文字样式，集中定义避免各处重复写 Color/FontWeight。 */
object LyricShareTextStyles {
    @Composable
    fun title(tokens: LyricShareTokens = LocalLyricShareTokens.current) = TextStyle(
        color = tokens.onSurface,
        fontSize = tokens.textTitle,
        fontWeight = FontWeight.Bold,
    )

    @Composable
    fun body(tokens: LyricShareTokens = LocalLyricShareTokens.current) = TextStyle(
        color = tokens.onSurface,
        fontSize = tokens.textBody,
        fontWeight = FontWeight.Medium,
    )

    @Composable
    fun label(tokens: LyricShareTokens = LocalLyricShareTokens.current) = TextStyle(
        color = tokens.onSurfaceVariant,
        fontSize = tokens.textLabel,
        fontWeight = FontWeight.Normal,
    )
}
