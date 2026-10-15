package com.lin0721.linmusic.core.ui.components

import android.widget.Toast
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.PillRadius
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * 具有长按复制能力的文本组件。
 *
 * 交互设计准则：
 * 1. 当未指定 [onClick] 时（默认态，如作为列表行标题/内容），长按在手指按下位置正上方弹出复制气泡；
 *    常规轻触/短按不消费任何指针事件流，完全透传给外层父级容器（如歌曲行、卡片），保留父级的整行水波纹涟漪。
 * 2. 当传入独立 [onClick] 时（如歌手名单独跳转歌手页），组件作为独立可点击实体，短按响应局部水波纹并执行 [onClick]，长按弹出复制气泡。
 * 3. 气泡采用 [PopupPositionProvider] 安全定位，杜绝屏幕边缘越界与顶部截断。
 */
@Composable
fun CopyableText(
    text: String,
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
    onClick: (() -> Unit)? = null,
    copyContent: String = text
) {
    var showTag by remember { mutableStateOf(false) }
    var pressOffset by remember { mutableStateOf(Offset.Zero) }
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val density = LocalDensity.current

    val coroutineScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }

    val gestureModifier = Modifier
        .then(
            if (onClick != null) {
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .indication(interactionSource, LocalIndication.current)
            } else {
                Modifier
            }
        )
        .pointerInput(copyContent, onClick) {
            val touchSlop = viewConfiguration.touchSlop
            val longPressTimeout = viewConfiguration.longPressTimeoutMillis

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                pressOffset = down.position
                val startPosition = down.position
                var isLongPress = false
                var isDrag = false

                var currentPress: PressInteraction.Press? = null
                if (onClick != null) {
                    val press = PressInteraction.Press(down.position)
                    currentPress = press
                    coroutineScope.launch { interactionSource.emit(press) }
                }

                var upPointer: PointerInputChange? = null
                val finishedBeforeTimeout = withTimeoutOrNull(longPressTimeout) {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pointer = event.changes.firstOrNull { it.id == down.id }

                        // 手指抬起（pressed 变为 false）：属于短按轻触，跳出超时循环
                        if (pointer == null || !pointer.pressed) {
                            upPointer = pointer
                            break
                        }

                        // 位移超过 touchSlop：判定为拖动滚动手势，取消长按
                        val distance = (pointer.position - startPosition).getDistance()
                        if (distance > touchSlop) {
                            isDrag = true
                            break
                        }
                    }
                    true
                }

                // 超时时间内手指保持在原处未抬起且未拖拽 -> 触发长按
                if (finishedBeforeTimeout == null) {
                    isLongPress = true
                }

                if (isLongPress) {
                    currentPress?.let { press ->
                        coroutineScope.launch { interactionSource.emit(PressInteraction.Cancel(press)) }
                    }
                    if (copyContent.isNotBlank()) {
                        showTag = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }

                    // 消费长按过程以及松手时的所有事件，防止抬手时误触父级点击（如切歌）
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        event.changes.forEach { it.consume() }
                        val pointer = event.changes.firstOrNull { it.id == down.id }
                        if (pointer == null || !pointer.pressed) {
                            break
                        }
                    }
                } else if (!isDrag) {
                    // 短按抬起且未发生拖拽滑动
                    if (onClick != null) {
                        upPointer?.consume()
                        currentPress?.let { press ->
                            coroutineScope.launch { interactionSource.emit(PressInteraction.Release(press)) }
                        }
                        onClick.invoke()
                    }
                    // onClick == null 时完全不消费事件，交由外层容器（SongRow / EntityRow 等）触发播放与水波纹
                } else {
                    // 发生了拖拽滑动（如列表上下滚动）
                    currentPress?.let { press ->
                        coroutineScope.launch { interactionSource.emit(PressInteraction.Cancel(press)) }
                    }
                }
            }
        }

    Box(modifier = modifier.then(gestureModifier)) {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            lineHeight = lineHeight,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
            modifier = textModifier
        )

        if (showTag && copyContent.isNotBlank()) {
            val popupPositionProvider = remember(pressOffset, density) {
                CopyPopupPositionProvider(pressOffset, density)
            }

            Popup(
                popupPositionProvider = popupPositionProvider,
                onDismissRequest = { showTag = false },
                properties = PopupProperties(
                    focusable = true,
                    dismissOnClickOutside = true,
                    dismissOnBackPress = true
                )
            ) {
                Surface(
                    shape = RoundedCornerShape(PillRadius),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shadowElevation = 6.dp,
                    tonalElevation = 3.dp,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        clipboardManager.setText(AnnotatedString(copyContent))
                        showTag = false
                        val toastMsg = if (copyContent.length > 25) {
                            "已复制: ${copyContent.take(25)}..."
                        } else {
                            "已复制: $copyContent"
                        }
                        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.xs)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "复制",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "复制",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * 具有边界自适应与防截断机制的复制气泡位置计算器。
 */
private class CopyPopupPositionProvider(
    private val pressOffset: Offset,
    private val density: Density,
    private val marginDp: Dp = 8.dp,
    private val spacingDp: Dp = 8.dp
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val marginPx = with(density) { marginDp.roundToPx() }
        val spacingPx = with(density) { spacingDp.roundToPx() }

        // 1. 水平方向：以触摸点为中心对齐，并强制约束在屏幕左右边界内，杜绝越界截断
        val anchorPressX = anchorBounds.left + pressOffset.x.roundToInt()
        val preferredX = anchorPressX - popupContentSize.width / 2
        val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        val x = preferredX.coerceIn(marginPx, maxX)

        // 2. 垂直方向：默认置于触摸点上方，若贴近屏幕顶部导致空间不足，自动向下翻转
        val anchorPressY = anchorBounds.top + pressOffset.y.roundToInt()
        val preferredYAbove = anchorPressY - popupContentSize.height - spacingPx
        val maxY = (windowSize.height - popupContentSize.height - marginPx).coerceAtLeast(marginPx)
        val y = if (preferredYAbove >= marginPx) {
            preferredYAbove
        } else {
            val preferredYBelow = anchorPressY + spacingPx
            preferredYBelow.coerceIn(marginPx, maxY)
        }

        return IntOffset(x, y)
    }
}
