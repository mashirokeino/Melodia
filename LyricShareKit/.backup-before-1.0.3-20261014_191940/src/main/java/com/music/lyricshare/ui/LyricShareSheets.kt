package com.music.lyricshare.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.music.lyricshare.render.ImageAdjust
import com.music.lyricshare.skin.EditableSpec
import com.music.lyricshare.skin.FontSpec
import com.music.lyricshare.skin.FontSource
import com.music.lyricshare.skin.ImageShape

/**
 * ============================================================================
 *  底部弹层集合
 * ============================================================================
 *  两个弹层都是"纯展示 + 回调"，不带业务逻辑，方便替换成你们自己的全局 BottomSheet 组件
 *  （例如 Melodia 里已有的 sheet 封装）而不影响生成逻辑。
 */

/**
 * 出图之后给用户两条路：存相册 / 系统分享。
 * 这是需求里"分享时可以选择本地保存或者直接调用 Android 原生分享接口"的落点。
 */
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
            Text("保存或分享", style = LyricShareTextStyles.title(tokens))
            Spacer(Modifier.height(tokens.spacingMd))
            SheetActionRow(
                icon = Icons.Rounded.Download,
                title = "保存到相册",
                description = "导出 PNG 到系统相册（Pictures 目录）",
                onClick = onSave,
            )
            Spacer(Modifier.height(tokens.spacingSm))
            SheetActionRow(
                icon = Icons.Rounded.Share,
                title = "系统分享",
                description = "调用 Android 原生分享面板，发送到任意 App",
                onClick = onShare,
            )
        }
    }
}

/** 弹层里的一行操作：左图标 + 标题/说明 + 整行可点。 */
@Composable
private fun SheetActionRow(
    icon: ImageVector,
    title: String,
    description: String,
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
            Spacer(Modifier.height(tokens.spacingXxs))
            Text(description, style = LyricShareTextStyles.label(tokens))
        }
    }
}

/**
 * 字体选择：系统默认 / 皮肤包内置字体 / 已导入字体 / 从文件选择。
 *
 * 自定义字体的优先级最高（见 RenderOptions.fontOverride），会覆盖皮肤自带字体。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontPickerSheet(
    bundledFonts: List<String>,
    userFonts: List<String>,
    selected: FontSpec?,
    onDismiss: () -> Unit,
    onUseDefault: () -> Unit,
    onUseBundled: (String) -> Unit,
    onUseUserFont: (String) -> Unit,
    onPickFromFile: () -> Unit,
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
            Text("选择字体", style = LyricShareTextStyles.title(tokens))
            Spacer(Modifier.height(tokens.spacingMd))

            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                FontOption(
                    label = "系统默认",
                    selected = selected == null,
                    onClick = onUseDefault,
                )

                if (bundledFonts.isNotEmpty()) {
                    Spacer(Modifier.height(tokens.spacingSm))
                    Text("皮肤内置", style = LyricShareTextStyles.label(tokens))
                    bundledFonts.forEach { path ->
                        FontOption(
                            label = path.substringAfterLast('/'),
                            selected = selected?.let { it.source == FontSource.ASSETS && it.value == path } == true,
                            onClick = { onUseBundled(path) },
                        )
                    }
                }

                if (userFonts.isNotEmpty()) {
                    Spacer(Modifier.height(tokens.spacingSm))
                    Text("已导入", style = LyricShareTextStyles.label(tokens))
                    userFonts.forEach { name ->
                        FontOption(
                            label = name,
                            selected = selected?.let {
                                it.source == FontSource.FILE && it.value?.endsWith(name) == true
                            } == true,
                            onClick = { onUseUserFont(name) },
                        )
                    }
                }

                Spacer(Modifier.height(tokens.spacingSm))
                HorizontalDivider(color = tokens.outline)

                FontOption(
                    label = "从文件选择字体…",
                    selected = false,
                    leadingIcon = Icons.Rounded.FolderOpen,
                    onClick = onPickFromFile,
                )
            }
        }
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

/**
 * ============================================================================
 *  「封面 / 背景」的二级面板
 * ============================================================================
 *  **面板里出现哪些区块，完全由皮肤声明 [spec] 决定**：
 *    replace    → "从相册选择"       visibility → "显示"开关
 *    shape      → 形状（直角/圆角/圆形），枚举来自 spec.shapes
 *    size       → 大小滑块（范围来自 spec.sizeRange）
 *    move       → 水平/垂直位置滑块（范围来自 ±spec.moveRange）
 *  皮肤没声明的区块不会出现；"恢复默认"始终可用（清空用户调整，回到皮肤声明值）。
 *
 *  所有改动通过 [onChange] 回传成 [ImageAdjust]，由渲染引擎应用 —— 皮肤文件本身不会被改写。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageAdjustSheet(
    title: String,
    spec: EditableSpec,
    adjust: ImageAdjust,
    /** true = 背景（不提供形状，大小/位置语义是缩放与平移）。 */
    isBackground: Boolean,
    /** 用户是否已经选过自己的图。 */
    hasCustomImage: Boolean,
    /** 皮肤自身声明的形状，用于把 "默认" 选项显示成当前实际形状。 */
    skinShape: ImageShape = ImageShape.DEFAULT,
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    onChange: (ImageAdjust) -> Unit,
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
                .padding(bottom = tokens.spacingLg)
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(title, style = LyricShareTextStyles.title(tokens))
            Spacer(Modifier.height(tokens.spacingMd))

            // ① 换图
            if (spec.replace) {
                SheetActionRow(
                    icon = Icons.Rounded.Image,
                    title = "从相册选择",
                    description = if (hasCustomImage) "当前使用你选择的图片" else "当前使用默认图（专辑图 / 皮肤自带）",
                    onClick = onPickImage,
                )
                Spacer(Modifier.height(tokens.spacingSm))
            }

            // ② 是否添加（显示开关）—— "可选择是否添加封面"就是这里
            if (spec.visibility) {
                SwitchRow(
                    label = "显示$title",
                    checked = adjust.visible,
                    onCheckedChange = { onChange(adjust.copy(visible = it)) },
                )
            }

            // ③ 形状（仅图片元素）
            if (!isBackground && spec.shape && spec.shapes.isNotEmpty()) {
                Spacer(Modifier.height(tokens.spacingMd))
                Text("形状", style = LyricShareTextStyles.label(tokens))
                Spacer(Modifier.height(tokens.spacingSm))
                val effective = if (adjust.shape == ImageShape.DEFAULT) skinShape else adjust.shape
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacingSm)) {
                    spec.shapes.forEach { shape ->
                        ShapeChip(
                            shape = shape,
                            selected = shape == effective,
                            onClick = { onChange(adjust.copy(shape = shape)) },
                        )
                    }
                }
            }

            // ④ 大小
            if (spec.size) {
                Spacer(Modifier.height(tokens.spacingMd))
                SliderRow(
                    label = if (isBackground) "缩放" else "大小",
                    value = adjust.scale,
                    range = (spec.sizeRange.getOrNull(0) ?: 0.6f)..(spec.sizeRange.getOrNull(1) ?: 1.4f),
                    valueText = "${(adjust.scale * 100).toInt()}%",
                    onValueChange = { onChange(adjust.copy(scale = it)) },
                )
            }

            // ⑤ 位置
            if (spec.move) {
                Spacer(Modifier.height(tokens.spacingSm))
                SliderRow(
                    label = "水平位置",
                    value = adjust.offsetX,
                    range = -spec.moveRange..spec.moveRange,
                    valueText = "${(adjust.offsetX * 100).toInt()}%",
                    onValueChange = { onChange(adjust.copy(offsetX = it)) },
                )
                Spacer(Modifier.height(tokens.spacingSm))
                SliderRow(
                    label = "垂直位置",
                    value = adjust.offsetY,
                    range = -spec.moveRange..spec.moveRange,
                    valueText = "${(adjust.offsetY * 100).toInt()}%",
                    onValueChange = { onChange(adjust.copy(offsetY = it)) },
                )
            }

            // ⑥ 恢复默认（清空所有用户调整，回到皮肤声明值）
            Spacer(Modifier.height(tokens.spacingSm))
            HorizontalDivider(color = tokens.outline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onChange(ImageAdjust()) }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, tint = tokens.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(tokens.spacingSm))
                    Text("恢复默认", color = tokens.onSurfaceVariant, fontSize = tokens.textBody)
                }
            }
        }
    }
}

/** 一行开关（用于"显示封面/背景"）。 */
@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val tokens = LocalLyricShareTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = tokens.onSurface, fontSize = tokens.textBody, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 一行滑块：左标签、右数值、下方滑杆。 */
@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit,
) {
    val tokens = LocalLyricShareTokens.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = tokens.onSurface, fontSize = tokens.textBody, modifier = Modifier.weight(1f))
            Text(valueText, color = tokens.onSurfaceVariant, fontSize = tokens.textLabel)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
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
