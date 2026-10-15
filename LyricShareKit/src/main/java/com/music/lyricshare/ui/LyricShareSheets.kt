package com.music.lyricshare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import com.music.lyricshare.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.music.lyricshare.render.EditState
import com.music.lyricshare.render.ElementPlacement
import com.music.lyricshare.render.QrPlacement
import com.music.lyricshare.skin.ControlKind
import com.music.lyricshare.skin.EditableOwner
import com.music.lyricshare.skin.EditProperty
import com.music.lyricshare.skin.ElementType
import com.music.lyricshare.skin.FontSpec
import com.music.lyricshare.skin.FontSource
import com.music.lyricshare.skin.ImageShape
import com.music.lyricshare.skin.SkinEditable
import kotlin.math.roundToInt

/**
 * ============================================================================
 *  底部弹层
 * ============================================================================
 *  · [ShareActionSheet]：出图后的"保存到相册 / 系统分享"
 *  · [EditSheet]：**唯一的编辑面板**，由皮肤的 `editable` 声明驱动 ——
 *    皮肤声明了哪些可调项（换图 / 显隐 / 形状 / 大小 / 位置 / 颜色 / 底色 / 字体），
 *    面板里就出现哪些区块；模块里没有"封面面板""二维码面板"之类的硬编码分支。
 */

/** 出图之后给用户两条路：存相册 / 系统分享。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareActionSheet(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    val tokens = LocalLyricShareTokens.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = tokens.surfaceContainer,
        shape = RoundedCornerShape(topStart = tokens.sheetRadius, topEnd = tokens.sheetRadius),
        dragHandle = { BottomSheetDefaults.DragHandle(color = tokens.outline) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacingLg)
                .padding(bottom = tokens.spacingLg),
        ) {
            Text("分享", style = LyricShareTextStyles.title(tokens))
            Spacer(Modifier.height(tokens.spacingMd))
            SheetActionRow(
                icon = Icons.Rounded.Download,
                title = "保存到相册",
                onClick = onSave,
            )
            Spacer(Modifier.height(tokens.spacingSm))
            SheetActionRow(
                icon = Icons.Rounded.Share,
                title = "系统分享",
                onClick = onShare,
            )
        }
    }
}

/**
 * 通用编辑面板 —— 内容完全由 [entry] 的声明决定。
 *
 * @param state 该项当前的用户设置
 * @param bundledFonts / userFonts 字体列表，仅当声明打开 `font` 时用到
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSheet(
    entry: SkinEditable,
    state: EditState,
    bundledFonts: List<String> = emptyList(),
    userFonts: List<String> = emptyList(),
    /** 播放页取色：作为底色色卡里的第一张（上方用水滴图标标识，不写文字）。 */
    playerColor: Int? = null,
    onDismiss: () -> Unit,
    /** 面板里的「从相册选择」被点。 */
    onPickImage: () -> Unit,
    /** 「从文件选择字体…」被点。 */
    onPickFontFile: () -> Unit,
    onChange: (EditState) -> Unit,
) {
    val tokens = LocalLyricShareTokens.current
    val spec = entry.spec
    val owner = entry.owner

    // 元素类型只用来问"能力注册表"要默认控件；界面本身不做类型分支
    val type = when (owner) {
        is EditableOwner.Element -> owner.element.type
        EditableOwner.Background -> ElementType.IMAGE
        EditableOwner.TextDefaults -> ElementType.TEXT
    }
    /** 皮肤声明的控件表；没写就由注册表把旧式开关展开。 */
    val controls = remember(spec, type) { spec.controlsFor(type) }
    /** 二维码的"位置"这几个选项要用短名。 */
    val qrLike = type == ElementType.QRCODE || type == ElementType.GROUP

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = tokens.surfaceContainer,
        shape = RoundedCornerShape(topStart = tokens.sheetRadius, topEnd = tokens.sheetRadius),
        dragHandle = { BottomSheetDefaults.DragHandle(color = tokens.outline) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacingLg)
                .padding(bottom = tokens.spacingLg)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // 标题行：右侧常驻「重置」（用户动过才出现）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.label, style = LyricShareTextStyles.title(tokens), modifier = Modifier.weight(1f))
                if (!state.isDefault) {
                    TextButton(onClick = { onChange(EditState()) }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = tokens.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(tokens.spacingXs))
                        Text("重置", color = tokens.primary, fontSize = tokens.textBody)
                    }
                }
            }
            Spacer(Modifier.height(tokens.spacingMd))

            // 一条声明 = 一个控件。控件种类决定组件，属性决定改哪一项 —— 这里没有任何元素类型判断。
            controls.forEach { control ->
                when (control.kind) {
                    ControlKind.SWITCH -> SwitchRow(
                        label = control.label ?: "显示${entry.label}",
                        checked = state.visible,
                        onCheckedChange = { onChange(state.copy(visible = it)) },
                    )

                    ControlKind.IMAGE -> {
                        SheetActionRow(
                            icon = Icons.Rounded.Image,
                            title = control.label ?: "从相册选择",
                            description = if (state.bitmap != null) "当前使用你选择的图片" else "当前使用默认图",
                            onClick = onPickImage,
                        )
                        Spacer(Modifier.height(tokens.spacingSm))
                    }

                    ControlKind.SHAPE -> {
                        SectionLabel(control.label ?: "形状")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacingSm),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            // 形状可选项也由皮肤在控件里声明；没写给三种常规值
                            val shapes = control.options
                                .map { ImageShape.from(it) }
                                .filter { it != ImageShape.DEFAULT }
                                .ifEmpty { listOf(ImageShape.ROUNDED, ImageShape.RECT, ImageShape.CIRCLE) }
                            // 用户没选过时，把"皮肤声明的形状"显示成选中项（否则圆形封面看起来一个都没选）
                            val declared = declaredShape(owner)
                            val effective = if (state.shape != ImageShape.DEFAULT) state.shape else declared
                            shapes.forEach { shape ->
                                ShapeChip(
                                    shape = shape,
                                    selected = effective == shape,
                                    onClick = { onChange(state.copy(shape = shape)) },
                                )
                            }
                        }
                    }

                    ControlKind.SLIDER -> {
                        // 兜底区间按属性给：位置是正负对称的，尺寸倍数必须为正
                        val range = control.range.takeIf { it.size >= 2 } ?: defaultRangeOf(control.property)
                        // 颗粒度：皮肤声明优先（0 = 连续），否则按 1%
                        val step = control.step ?: SLIDER_STEP
                        val value = when (control.property) {
                            EditProperty.OFFSET_X -> state.offsetX
                            EditProperty.OFFSET_Y -> state.offsetY
                            else -> state.scale
                        }
                        SliderRow(
                            label = control.label ?: "大小",
                            value = value,
                            range = range[0]..range[1],
                            step = step,
                            valueText = "${(value * 100).toInt()}%",
                            onValueChange = { picked ->
                                onChange(
                                    when (control.property) {
                                        EditProperty.OFFSET_X -> state.copy(offsetX = picked)
                                        EditProperty.OFFSET_Y -> state.copy(offsetY = picked)
                                        else -> state.copy(scale = picked)
                                    }
                                )
                            },
                        )
                    }

                    ControlKind.ENUM -> {
                        SectionLabel(control.label ?: "位置")
                        val options = control.options.ifEmpty { listOf("skin", "left", "center", "right") }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacingSm),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            options.forEach { option ->
                                val value = ElementPlacement.from(option)
                                Chip(
                                    label = placementLabel(option, qrLike),
                                    selected = state.placement == value,
                                    onClick = { onChange(state.copy(placement = value)) },
                                )
                            }
                        }
                    }

                    ControlKind.COLOR -> {
                        SectionLabel(control.label ?: if (control.property == EditProperty.BACKGROUND) "底色" else "颜色")
                        // 底色可以额外给一个"透明"档
                        val transparent = control.allowTransparent &&
                            state.solidColor == android.graphics.Color.TRANSPARENT
                        if (control.allowTransparent) {
                            SwitchRow(
                                label = "透明",
                                checked = transparent,
                                onCheckedChange = { on ->
                                    onChange(
                                        state.copy(
                                            solidColor = if (on) android.graphics.Color.TRANSPARENT else null,
                                        )
                                    )
                                },
                            )
                        }
                        if (!transparent) {
                            SwatchRow(
                                colors = if (control.property == EditProperty.BACKGROUND) {
                                    BackgroundSwatches
                                } else {
                                    FontColorSwatches
                                },
                                selected = if (control.property == EditProperty.BACKGROUND) {
                                    state.solidColor
                                } else {
                                    state.color
                                },
                                withLeader = control.allowNone,
                                // 「跟随播放页」只给背景/颜色类属性（宿主传了取色才有）
                                playerColor = playerColor,
                                onSelect = { picked ->
                                    onChange(
                                        if (control.property == EditProperty.BACKGROUND) {
                                            state.copy(solidColor = picked)
                                        } else {
                                            state.copy(color = picked)
                                        }
                                    )
                                },
                            )
                        }
                    }

                    ControlKind.TEXT -> {
                        // 标题带一个"文字输入"图标（Icons.Rounded.Input，来自 material-icons-extended）
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Input,
                                contentDescription = null,
                                tint = tokens.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(tokens.spacingXs))
                            Text(
                                control.label ?: "文字",
                                style = LyricShareTextStyles.label(tokens),
                            )
                        }
                        Spacer(Modifier.height(tokens.spacingXs))
                        var value by remember(state.text) { mutableStateOf(state.text.orEmpty()) }
                        OutlinedTextField(
                            value = value,
                            onValueChange = { input ->
                                value = input
                                // 空串 = 回到皮肤声明的文字
                                onChange(state.copy(text = input.ifBlank { null }))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            // 输入框大小由皮肤声明（minLines / maxLines）
                            minLines = control.minLines.coerceIn(1, 20),
                            maxLines = maxOf(control.maxLines, control.minLines).coerceIn(1, 30),
                            textStyle = LyricShareTextStyles.body(tokens),
                            placeholder = {
                                Text(
                                    control.hint ?: "可用：{title} {artists} {album} {lines}",
                                    style = LyricShareTextStyles.label(tokens),
                                )
                            },
                        )
                        if (!control.hint.isNullOrBlank()) {
                            Spacer(Modifier.height(tokens.spacingXs))
                            Text(control.hint, style = LyricShareTextStyles.label(tokens))
                        }
                    }

                    ControlKind.FONT -> {
                        Spacer(Modifier.height(tokens.spacingMd))
                        HorizontalDivider(color = tokens.outline)
                        Spacer(Modifier.height(tokens.spacingSm))
                        SectionLabel(control.label ?: "字体")
                        Column(modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                            FontOption(
                                label = "系统默认",
                                selected = state.font == null,
                                onClick = { onChange(state.copy(font = null)) },
                            )
                            if (bundledFonts.isNotEmpty()) {
                                Spacer(Modifier.height(tokens.spacingSm))
                                Text("皮肤内置", style = LyricShareTextStyles.label(tokens))
                                bundledFonts.forEach { path ->
                                    FontOption(
                                        label = path.substringAfterLast('/'),
                                        selected = state.font?.let { it.source == FontSource.ASSETS && it.value == path } == true,
                                        onClick = { onChange(state.copy(font = FontSpec(FontSource.ASSETS, path))) },
                                    )
                                }
                            }
                            if (userFonts.isNotEmpty()) {
                                Spacer(Modifier.height(tokens.spacingSm))
                                Text("已导入", style = LyricShareTextStyles.label(tokens))
                                userFonts.forEach { name ->
                                    FontOption(
                                        label = name,
                                        selected = state.font?.let {
                                            it.source == FontSource.FILE && it.value?.endsWith(name) == true
                                        } == true,
                                        onClick = { onChange(state.copy(font = FontSpec(FontSource.FILE, name))) },
                                    )
                                }
                            }
                            Spacer(Modifier.height(tokens.spacingSm))
                            HorizontalDivider(color = tokens.outline)
                            FontOption(
                                label = "从文件选择字体…",
                                selected = false,
                                leadingIcon = Icons.Rounded.FolderOpen,
                                onClick = onPickFontFile,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 小节标题。 */
@Composable
private fun SectionLabel(text: String) {
    val tokens = LocalLyricShareTokens.current
    Spacer(Modifier.height(tokens.spacingMd))
    Text(text, style = LyricShareTextStyles.label(tokens))
    Spacer(Modifier.height(tokens.spacingSm))
}

/** 元素在皮肤里声明的形状（用户没覆盖时用它显示选中态）。 */
private fun declaredShape(owner: EditableOwner): ImageShape = when (owner) {
    is EditableOwner.Element -> when {
        owner.element.circle -> ImageShape.CIRCLE
        owner.element.cornerRadius > 0 -> ImageShape.ROUNDED
        else -> ImageShape.RECT
    }
    else -> ImageShape.DEFAULT
}

/**
 * 滑杆没有声明 `range` 时的兜底区间。
 * 位置类**必须**是正负对称的（0 在中间），否则圆点会贴左端、拖动值也不对。
 */
private fun defaultRangeOf(property: EditProperty): List<Float> = when (property) {
    EditProperty.OFFSET_X, EditProperty.OFFSET_Y -> listOf(-0.25f, 0.25f)
    else -> listOf(0.6f, 1.4f)
}

/** 锚点选项的显示名。 */
private fun placementLabel(option: String, qrLike: Boolean): String = when (option.trim().lowercase()) {
    "skin", "auto", "flow" -> if (qrLike) "跟随皮肤" else "跟随皮肤"
    "left", "bottom_left" -> "左下"
    "bottom", "bottom_center" -> "下方"
    "right", "bottom_right" -> "右下"
    "top_left" -> "左上"
    "top", "top_center" -> "上方"
    "top_right" -> "右上"
    "center" -> "居中"
    else -> option
}

// ============================================================================
//  通用小组件（与皮肤无关，只负责把声明渲染成控件）
// ============================================================================

/** 弹层里的一行操作：左图标 + 标题（可选说明）+ 整行可点。 */
@Composable
private fun SheetActionRow(
    icon: ImageVector,
    title: String,
    description: String? = null,
    onClick: () -> Unit,
) {
    val tokens = LocalLyricShareTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(tokens.surfaceContainerHigh, RoundedCornerShape(tokens.radiusMd))
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacingMd, vertical = tokens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(tokens.surface, RoundedCornerShape(tokens.radiusSm)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tokens.onSurface, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(tokens.spacingMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = LyricShareTextStyles.body(tokens))
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(tokens.spacingXxs))
                Text(description, style = LyricShareTextStyles.label(tokens))
            }
        }
    }
}

/** 一行开关。 */
@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val tokens = LocalLyricShareTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = tokens.onSurface, fontSize = tokens.textBody, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * 一行滑块：左标签、右数值、下方滑杆。
 *
 * **颗粒度固定 1%**（0.01）：值会被吸附到 0.01 的整数倍，并把这个步数交给 Slider，
 * 所以拖动时是"一格一格"跳的，不会出现 63.7% 这种没意义的精度。
 */
@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit,
    /** 每一格的值（由皮肤声明）；0 或负数 = 连续，不吸附。 */
    step: Float = SLIDER_STEP,
) {
    val tokens = LocalLyricShareTokens.current
    val haptics = LocalHapticFeedback.current
    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
    // 档数：按皮肤声明的颗粒度算；0/负数 = 连续（不吸附、不画档位点）
    val stepCount = if (step > 0f) (span / step).roundToInt().coerceIn(1, 400) else 0
    val snapped = if (stepCount > 0) snapToStep(value, range.start, span, stepCount) else value
    // 记录"上一格"，每跨过一格震一下（同一格内连续拖动不重复震）
    var lastTick by remember { mutableStateOf(snapped) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = tokens.onSurface, fontSize = tokens.textBody, modifier = Modifier.weight(1f))
            Text(valueText, color = tokens.onSurfaceVariant, fontSize = tokens.textLabel)
        }
        Slider(
            value = snapped,
            onValueChange = { raw ->
                val next = if (stepCount > 0) snapToStep(raw, range.start, span, stepCount) else raw
                if (next != lastTick) {
                    lastTick = next
                    // 一格一震：用系统触觉反馈（无需 VIBRATE 权限）
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onValueChange(next)
            },
            valueRange = range,
            steps = (stepCount - 1).coerceAtLeast(0),
            // 档位点画成透明：几十上百个点太密，影响观感（吸附与步进照旧）
            colors = SliderDefaults.colors(
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

/** 把值吸附到"1% 一档"的最近一档。 */
private fun snapToStep(value: Float, start: Float, span: Float, stepCount: Int): Float {
    val clamped = value.coerceIn(start, start + span)
    val ticks = ((clamped - start) / span * stepCount).roundToInt().coerceIn(0, stepCount)
    return start + span * ticks / stepCount
}

/**
 * 一排色卡。
 * @param withLeader 是否在最前面放一张"斜杠 = 跟随皮肤"
 */
@Composable
private fun SwatchRow(
    colors: List<Int>,
    selected: Int?,
    withLeader: Boolean,
    onSelect: (Int?) -> Unit,
    /** 播放页取色：作为第一张色卡（"跟随播放页"，圆环+水滴）。 */
    playerColor: Int? = null,
) {
    val tokens = LocalLyricShareTokens.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(tokens.spacingSm),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        if (withLeader) {
            NoneSwatch(selected = selected == null, onClick = { onSelect(null) })
        }
        // 「跟随播放页」：色块用播放页取色，水滴图标**压在色块里面**
        playerColor?.let { color ->
            ColorSwatch(
                color = color,
                selected = selected == color,
                icon = painterResource(R.drawable.ls_ic_droplet),
                onClick = { onSelect(color) },
            )
        }
        colors.forEach { color ->
            ColorSwatch(
                color = color,
                selected = selected == color,
                onClick = { onSelect(color) },
            )
        }
    }
}

/**
 * 色卡：一个纯色圆。
 * [icon] 会**画在色块内部**（居中），并按底色明暗自动选黑/白 —— 「跟随播放页」就是用它压一个水滴。
 */
@Composable
private fun ColorSwatch(
    color: Int,
    selected: Boolean,
    onClick: () -> Unit,
    label: String? = null,
    icon: Painter? = null,
) {
    val tokens = LocalLyricShareTokens.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(color))
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) tokens.primary else tokens.outline,
                    shape = CircleShape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    // 深色底用白图标、浅色底用黑图标
                    tint = if (Color(color).luminance() > 0.5f) Color.Black else Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (label != null) {
            Spacer(Modifier.height(tokens.spacingXxs))
            Text(label, color = tokens.onSurfaceVariant, fontSize = tokens.textLabel)
        }
    }
}

/** "不用这个颜色"色卡（斜杠）—— 点了就回到皮肤声明的值。 */
@Composable
private fun NoneSwatch(selected: Boolean, onClick: () -> Unit) {
    val tokens = LocalLyricShareTokens.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(tokens.surface)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) tokens.primary else tokens.outline,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Block,
            contentDescription = "跟随皮肤",
            tint = tokens.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 形状选项胶囊（直角 / 圆角 / 圆形）。 */
@Composable
private fun ShapeChip(
    shape: ImageShape,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalLyricShareTokens.current
    val label = when (shape) {
        ImageShape.RECT -> "直角"
        ImageShape.CIRCLE -> "圆形"
        else -> "圆角"
    }
    val icon = when (shape) {
        ImageShape.CIRCLE -> Icons.Rounded.RadioButtonUnchecked
        ImageShape.RECT -> Icons.Rounded.CropSquare
        else -> Icons.Rounded.Image
    }
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .background(
                if (selected) tokens.surfaceContainerHigh else tokens.surface,
                RoundedCornerShape(tokens.radiusPill),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) tokens.primary else tokens.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(tokens.spacingXs))
        Text(
            text = label,
            color = if (selected) tokens.primary else tokens.onSurface,
            fontSize = tokens.textLabel,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/** 通用选项胶囊（锚点位置等枚举属性用）。 */
@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalLyricShareTokens.current
    val isSelected = selected
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .background(
                if (isSelected) tokens.surfaceContainerHigh else tokens.surface,
                RoundedCornerShape(tokens.radiusPill),
            )
            .border(
                1.dp,
                if (isSelected) tokens.primary else tokens.outline,
                RoundedCornerShape(tokens.radiusPill),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacingMd),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (isSelected) tokens.primary else tokens.onSurface,
            fontSize = tokens.textLabel,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/** 字体列表里的一项：选中时右侧打勾。 */
@Composable
private fun FontOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIcon: ImageVector = Icons.Rounded.TextFields,
) {
    val tokens = LocalLyricShareTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(leadingIcon, contentDescription = null, tint = tokens.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(tokens.spacingMd))
        Text(
            text = label,
            color = if (selected) tokens.primary else tokens.onSurface,
            fontSize = tokens.textBody,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = "已选中", tint = tokens.primary, modifier = Modifier.size(18.dp))
        }
    }
}

/** 滑杆颗粒度：1%（0.01）。 */
private const val SLIDER_STEP = 0.01f

/** 背景底色的预设色卡。 */
private val BackgroundSwatches = listOf(
    0xFFFFFFFF.toInt(),   // 白
    0xFF000000.toInt(),   // 黑
    0xFF101014.toInt(),   // 默认深
    0xFF1F1F24.toInt(),   // 深灰
    0xFF3A3A42.toInt(),   // 中灰
    0xFF2E4756.toInt(),   // 冷蓝
    0xFF4A2C3A.toInt(),   // 酒红
    0xFF2F4030.toInt(),   // 墨绿
)

/** 颜色（前景/字体）的预设色卡。 */
private val FontColorSwatches = listOf(
    0xFFFFFFFF.toInt(),  // 白
    0xFFE6E6E6.toInt(),  // 浅灰
    0xFFB8B8B8.toInt(),  // 中灰
    0xFF666666.toInt(),  // 深灰
    0xFF000000.toInt(),  // 黑
    0xFFFF3B4E.toInt(),  // 品牌红
    0xFFF5EFE6.toInt(),  // 米白
    0xFF7FB3D5.toInt(),  // 天蓝
)
