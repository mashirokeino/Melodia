package com.music.lyricshare.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.music.lyricshare.LyricShareData
import com.music.lyricshare.font.FontProvider
import com.music.lyricshare.qr.QrCodeGenerator
import com.music.lyricshare.skin.BgType
import com.music.lyricshare.skin.ElementDefaults
import com.music.lyricshare.skin.ElementType
import com.music.lyricshare.skin.FontSpec
import com.music.lyricshare.skin.HAlign
import com.music.lyricshare.skin.ImageShape
import com.music.lyricshare.skin.LoadedSkin
import com.music.lyricshare.skin.ScaleType
import com.music.lyricshare.skin.GroupOrientation
import com.music.lyricshare.skin.ShadowSpec
import com.music.lyricshare.skin.SkinElement
import com.music.lyricshare.skin.SongInfoMode
import com.music.lyricshare.skin.VMode
import com.music.lyricshare.util.BitmapUtils
import com.music.lyricshare.util.BlurUtils
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 渲染开关。全部有默认值，宿主一般只需要传 [fontOverride] 和 [backgroundOverride]。
 */
/**
 * 用户在界面上对某张图做的调整（**不改皮肤文件**，只影响本次渲染）。
 *
 * 界面上的「封面 / 背景」二级面板最终就是产出这个对象：
 *   · 换图 → [bitmap]； · 显示开关 → [visible]； · 形状 → [shape]；
 *   · 大小 → [scale]； · 位置 → [offsetX] / [offsetY]。
 *
 * 对图片元素，[scale] 改变**占位尺寸**（后续文字会跟着重排）；
 * 对背景，[scale] 是画面缩放，[offsetX]/[offsetY] 是平移。
 */
data class EditState(
    /** 替换用的图片；null = 用皮肤/运行时默认图。 */
    val bitmap: Bitmap? = null,
    /** 纯色（背景专用）：非 null 时**优先于图片**，直接铺这个颜色。 */
    val solidColor: Int? = null,
    /** false = 这一项不绘制（"是否添加封面"），且不占任何空间。 */
    val visible: Boolean = true,
    val shape: ImageShape = ImageShape.DEFAULT,
    /** 缩放系数（1 = 皮肤声明值）。 */
    val scale: Float = 1f,
    /** 水平偏移，相对画布宽的比例（-0.25 = 左移 1/4 画布）。 */
    val offsetX: Float = 0f,
    /** 垂直偏移，相对画布高的比例。 */
    val offsetY: Float = 0f,
    /** 颜色：文本=字体颜色；二维码=码点前景色。null = 皮肤声明。 */
    val color: Int? = null,
    /** 字体（文本类）。null = 皮肤声明。 */
    val font: FontSpec? = null,
    /** 二维码放置位置（仅二维码）。 */
    val placement: QrPlacement = QrPlacement.SKIN,
) {
    val isDefault: Boolean
        get() = bitmap == null && solidColor == null && color == null && font == null &&
            visible && shape == ImageShape.DEFAULT && scale == 1f && offsetX == 0f && offsetY == 0f &&
            placement == QrPlacement.SKIN

    companion object {
        val NONE = EditState()
    }
}

/**
 * 元素的锚点位置 —— **对任意元素通用**：选了就吸附到画布对应角/边，并脱离流式排版。
 * [SKIN] = 按皮肤声明的 x/y 走（默认，不干预）。
 */
enum class ElementPlacement {
    SKIN,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT,
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER;

    companion object {
        /** 解析控件里的选项值。`left/right/bottom/top/center` 是简写（都落在下半/上半区）。 */
        fun from(raw: String?): ElementPlacement = when (raw?.trim()?.lowercase()?.replace("-", "_")) {
            null, "", "skin", "auto", "flow" -> SKIN
            "left", "bottom_left", "bl" -> BOTTOM_LEFT
            "bottom", "bottom_center", "bc", "center_bottom" -> BOTTOM_CENTER
            "right", "bottom_right", "br" -> BOTTOM_RIGHT
            "top_left", "tl" -> TOP_LEFT
            "top", "top_center", "tc", "center_top" -> TOP_CENTER
            "top_right", "tr" -> TOP_RIGHT
            "center", "middle", "c" -> CENTER
            else -> SKIN
        }
    }
}

/** 兼容旧名。 */
typealias QrPlacement = ElementPlacement

data class RenderOptions(
    /** 输出宽度（像素）；null = 用皮肤参考宽度。预览传小值即可，几何关系与导出完全一致。 */
    val widthPx: Int? = null,
    /** 用户在界面上选的字体，优先级最高（覆盖皮肤自带字体）。 */
    val fontOverride: FontSpec? = null,
    /** 是否绘制二维码。false 时**不进布局、不占空间**。 */
    val showQrCode: Boolean = true,
    /** 是否绘制 role=="brand" 的元素（"来自 XXX"）。默认 false：不画也不预留。 */
    val showBrandElement: Boolean = false,
    /**
     * **元素序号 → 用户调整**。
     *
     * 引擎不知道"这是封面还是二维码"：它只按 [EditState] 里被设过的属性通用地作用到该元素上
     * （COLOR / BACKGROUND / SCALE / SHAPE / SOURCE / 位移 / 锚点 / 显隐）。
     * 序号 = 皮肤 `elements` 的下标；上层把皮肤声明的可编辑项 id 映射成序号。
     */
    val elementEdits: Map<Int, EditState> = emptyMap(),
    /** 画布背景的用户调整（换图 / 纯色 / 缩放 / 平移 / 显隐）。 */
    val backgroundEdit: EditState = EditState(),
    /** 文本默认样式的用户调整（字体 / 颜色）—— 元素自身没设时生效。 */
    val textEdit: EditState = EditState(),
)

/** 兼容旧名（1.0.6 之前叫 ImageAdjust）。 */
typealias ImageAdjust = EditState

/** 参考像素 → 输出像素。所有尺寸都走这里，保证「同一份几何关系、不同输出分辨率」。 */
private fun Int.px(scale: Float): Int = (this * scale).roundToInt()
private fun Float.px(scale: Float): Int = (this * scale).roundToInt()

/**
 * ============================================================================
 *  歌词图片渲染引擎
 * ============================================================================
 *  和「模板 XML inflate 后 View.draw() 截图」那套做法的差别：
 *   · 截图方式依赖屏幕密度，预览和成图分辨率绑定，长歌词还受 ScrollView 可见区域影响；
 *   · 这里是**直接往 Bitmap 上画**：坐标按皮肤参考宽度等比缩放，
 *     输出尺寸只由皮肤决定，与设备无关。
 *
 *  预览与导出使用**同一个渲染宽度、同一台引擎**（页面只做高清缩放显示），
 *  所以"所见即所得"，且预览位图可以直接复用于导出。
 *
 *  渲染流程（两趟）：
 *    ① 度量：流式元素依次往下排，同时得到浮层元素的位置与画布总高；
 *    ② 绘制：先铺背景（纯色/渐变/图片+模糊+压暗），再按顺序画每个元素。
 *
 *  线程安全：无实例可变状态（缩放系数按调用传参），可并发调用，
 *  适合放到 Dispatchers.Default 上跑（页面预览与皮肤缩略图会同时渲染）。
 */
class LyricImageRenderer(private val fontProvider: FontProvider) {

    /** 度量结果：元素 + 它在输出画布上的真实像素尺寸。 */
    private class Measured(
        val el: SkinElement,
        val edit: EditState,
        val width: Int,
        val height: Int,
        val layout: StaticLayout?,
        /** 组合元素的子元素（已按未缩放尺寸量好并算好相对偏移）。 */
        val children: List<Placed> = emptyList(),
        /** 组合内容未缩放时的尺寸（缩放由 canvas.scale 完成）。 */
        val innerWidth: Int = width,
        val innerHeight: Int = height,
    )

    /** 组合里已经摆好位置的子元素。 */
    private class Placed(val measured: Measured, val dx: Int, val dy: Int)

    fun render(
        skin: LoadedSkin,
        data: LyricShareData,
        options: RenderOptions = RenderOptions(),
    ): Bitmap {
        val spec = skin.spec
        val canvasSpec = spec.canvas
        val defaults = spec.defaults
        val outputWidth = options.widthPx ?: canvasSpec.width
        val scale = outputWidth.toFloat() / canvasSpec.width.toFloat()

        // 用户调整：按元素序号取（引擎不关心元素"是什么"，只按属性通用应用）
        val backgroundEdit = options.backgroundEdit
        val textEdit = options.textEdit
        fun editOf(index: Int): EditState = options.elementEdits[index] ?: EditState.NONE
        // 封面位换过图就用用户选的，否则用调用方给的（专辑图）
        val coverSlotIndex = spec.elements.indexOfFirst { it.isCoverSlot }
        val effectiveCover = if (coverSlotIndex >= 0) editOf(coverSlotIndex).bitmap ?: data.cover else data.cover

        // 样式二的判定：需要二维码真的会画出来，且皮肤里声明了 role:"title"
        val qrWillRender = spec.elements.any { it.type == ElementType.QRCODE } &&
            options.showQrCode && !data.qrContent.isNullOrBlank()
        val titleElement = spec.elements.firstOrNull { it.role.equals("title", ignoreCase = true) }
        val artistsElement = spec.elements.firstOrNull { it.role.equals("artists", ignoreCase = true) }
        // 版式由皮肤决定：canvas.songInfo.mode == "besideQr" 即样式二
        val songInfoMode = canvasSpec.songInfo.mode == SongInfoMode.BESIDE_QR &&
            qrWillRender && titleElement != null

        // ---------------- 0) 元素过滤：被关掉的东西直接不进布局，也就不占空间 ----------------
        val visibleIndexed = spec.elements.withIndex().filter { (index, el) ->
            val edit = editOf(index)
            when {
                !edit.visible -> false                                   // 用户显式关掉
                el.isBrand && !options.showBrandElement -> false
                // 样式二：歌名/艺人改由底部信息带绘制，从流式里摘掉（不留空洞）
                songInfoMode && (el === titleElement || el === artistsElement) -> false
                el.type == ElementType.QRCODE && (!options.showQrCode || data.qrContent.isNullOrBlank()) -> false
                el.type == ElementType.IMAGE && el.useCover && effectiveCover == null -> false
                else -> true
            }
        }

        val padLeft = canvasSpec.padding.left.px(scale)
        val padTop = canvasSpec.padding.top.px(scale)
        val padRight = canvasSpec.padding.right.px(scale)
        val padBottom = canvasSpec.padding.bottom.px(scale)
        val contentWidth = (outputWidth - padLeft - padRight).coerceAtLeast(1)

        // ---------------- 1) 度量 + 流式排版 ----------------
        val measured = visibleIndexed.mapNotNull { (index, el) ->
            measure(el, editOf(index), contentWidth, defaults, skin, options, data, scale)
        }

        val flowY = HashMap<Int, Int>(measured.size)
        var cursor = padTop
        measured.forEachIndexed { index, m ->
            val el = m.el
            // 注意：被锚定的元素**仍然保留它在流式里的占位**（只是绘制位置被吸附到画布角）。
            // 这样"最后一个元素锚到右下"既不会在原位留洞、也不会压住上面的歌词；
            // 皮肤把需要锚定的元素放在元素列表末尾即可。
            if (el.y.mode == VMode.FLOW) {
                val y = cursor + el.margin.top.px(scale)
                flowY[index] = y
                cursor = y + m.height + el.margin.bottom.px(scale)
            }
        }
        // 样式二：先把「歌名+艺人」块按信息带宽度量出来；比二维码高就撑高画布，避免压到歌词
        val qrMeasured = measured.firstOrNull { it.el.type == ElementType.QRCODE }
        val songInfoLayouts = if (songInfoMode && qrMeasured != null) {
            val gap = 32.px(scale)
            val bandWidth = (outputWidth - padLeft - padRight - qrMeasured.width - gap).coerceAtLeast(1)
            buildSongInfoLayouts(titleElement, artistsElement, bandWidth, defaults, skin, options, data, scale)
        } else {
            null
        }
        val contentBottom = cursor + padBottom +
            (songInfoLayouts?.let { (it.height - (qrMeasured?.height ?: 0)).coerceAtLeast(0) } ?: 0)
        val canvasHeight = canvasSpec.height?.px(scale)
            ?: max(canvasSpec.minHeight.px(scale), contentBottom)

        // ---------------- 2) 绘制 ----------------
        val output = Bitmap.createBitmap(outputWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val temporaries = ArrayList<Bitmap>(4)

        drawBackground(canvas, outputWidth, canvasHeight, skin, data, backgroundEdit, temporaries)

        measured.forEachIndexed { index, m ->
            // 样式二：二维码由底部信息带统一绘制（这里跳过，避免同一个码画两遍）
            if (songInfoMode && m.el.type == ElementType.QRCODE) return@forEachIndexed
            val baseX = resolveX(m, padLeft, padRight, outputWidth, scale)
            val baseY = resolveY(m, index, flowY, padTop, padBottom, canvasHeight, scale)
            // 通用位移：相对画布宽/高的比例，对任意元素都成立
            val x = baseX + (outputWidth * m.edit.offsetX).roundToInt()
            val y = baseY + (canvasHeight * m.edit.offsetY).roundToInt()
            drawElement(
                canvas, m, x, y, defaults, skin, options, data, temporaries, scale,
                effectiveCover, outputWidth, canvasHeight,
            )
        }

        // ---------------- 3) 歌曲信息样式二：底部一行（二维码 + 歌名/艺人） ----------------
        if (songInfoLayouts != null && titleElement != null) {
            drawSongInfoBand(
                canvas = canvas,
                skin = skin,
                data = data,
                options = options,
                titleEl = titleElement,
                artistsEl = artistsElement,
                qrMeasured = qrMeasured,
                layouts = songInfoLayouts,
                padLeft = padLeft,
                padRight = padRight,
                padBottom = padBottom,
                canvasWidth = outputWidth,
                canvasHeight = canvasHeight,
                scale = scale,
                temporaries = temporaries,
                effectiveCover = effectiveCover,
            )
        }

        // 引擎自己产生的临时位图（模糊图/二维码）用完即回收，输入位图一律不碰
        temporaries.forEach { runCatching { it.recycle() } }
        return output
    }

    // ================================================================ 度量

    /**
     * 度量一个元素。
     *
     * **通用语义**（引擎不关心元素"是什么类型"，只按 [EditState] 里设过的属性作用）：
     *  · [EditState.scale] → 乘到该元素的"生效尺寸"上：文字=字号、图片=占位、二维码=码块、
     *    分割线=粗细、组合=整体
     *  · 显隐 / 位移 / 锚点在 [render] 与 [resolveX]/[resolveY] 里统一处理
     */
    private fun measure(
        el: SkinElement,
        edit: EditState,
        contentWidth: Int,
        defaults: ElementDefaults,
        skin: LoadedSkin,
        options: RenderOptions,
        data: LyricShareData,
        scale: Float,
    ): Measured? {
        // 左对齐时元素有 x 偏移，它的可用宽度要相应减小，否则会越过右内边距（胶片黑的标题就是这样溢出的）
        val leftInset = if (el.x.align == HAlign.LEFT) el.x.offset.px(scale) + el.margin.left.px(scale) else 0
        val width = el.width?.px(scale)?.coerceAtLeast(1)
            ?: (contentWidth - leftInset).coerceAtLeast(1)
        val factor = edit.scale.coerceIn(0.05f, 6f)
        return when (el.type) {
            ElementType.TEXT, ElementType.LYRIC -> {
                val text = resolveText(el, data)
                if (text.isBlank()) return null                       // 空文本 = 这个元素不存在（不预留）
                val paint = buildTextPaint(el, defaults, skin, options, scale, edit)
                val layout = buildLayout(text, paint, width, el, defaults, scale)
                Measured(el, edit, width, layout.height, layout)
            }

            ElementType.IMAGE -> {
                val declaredWidth = el.width?.px(scale)
                val declaredHeight = el.height?.px(scale)
                val w = ((declaredWidth ?: contentWidth) * factor).roundToInt().coerceAtLeast(1)
                val h = ((declaredHeight ?: declaredWidth ?: contentWidth) * factor).roundToInt().coerceAtLeast(1)
                Measured(el, edit, w, h, null)
            }

            ElementType.QRCODE -> {
                val base = el.width?.px(scale) ?: el.height?.px(scale) ?: 240.px(scale)
                val side = (base * factor).roundToInt().coerceAtLeast(1)
                Measured(el, edit, side, side, null)
            }

            ElementType.DIVIDER -> Measured(
                el, edit, width,
                ((el.height?.px(scale) ?: max(1, scale.roundToInt())) * factor).roundToInt().coerceAtLeast(1),
                null,
            )

            ElementType.SPACER -> Measured(el, edit, width, ((el.height?.px(scale) ?: 0) * factor).roundToInt(), null)

            ElementType.GROUP -> measureGroup(el, edit, contentWidth, defaults, skin, options, data, scale)
        }
    }

    /**
     * 组合元素（皮肤自己"拼"出来的新类型）：先量子元素，再按 [SkinElement.orientation] 算整体尺寸。
     *
     * ROW 采用简化弹性布局：**写了 width 的子元素**（图片/二维码按其内容尺寸）先占位，
     * 剩下的宽度平分给"弹性"子元素 —— 这样「文字 + 二维码」这类信息条能自然排开。
     * 子元素不参与外层流式排版（x/y 被忽略），也不单独开放可编辑项（组合整体才有）。
     */
    private fun measureGroup(
        el: SkinElement,
        edit: EditState,
        contentWidth: Int,
        defaults: ElementDefaults,
        skin: LoadedSkin,
        options: RenderOptions,
        data: LyricShareData,
        scale: Float,
    ): Measured? {
        val kids = el.children
        if (kids.isEmpty()) return null
        val gap = el.gap.px(scale)
        val n = kids.size
        val totalGap = gap * (n - 1).coerceAtLeast(0)

        // 1) ROW 的宽度分配
        val fixed = IntArray(n) { -1 }
        var fixedSum = 0
        var flexCount = 0
        kids.forEachIndexed { i, child ->
            val explicit = child.width?.px(scale) ?: when (child.type) {
                ElementType.QRCODE -> child.height?.px(scale) ?: 240.px(scale)
                ElementType.IMAGE -> child.height?.px(scale)
                else -> null
            }
            if (explicit != null && explicit > 0) {
                fixed[i] = explicit
                fixedSum += explicit
            } else {
                flexCount++
            }
        }
        // 只有 ROW 才需要"把剩余宽度分给弹性子元素"；纵向/叠放时弹性子元素直接占满可用宽度
        val perFlex = when {
            flexCount == 0 -> contentWidth
            el.orientation == GroupOrientation.ROW ->
                ((contentWidth - fixedSum - totalGap) / flexCount).coerceAtLeast(1)
            else -> contentWidth
        }

        // 2) 量子元素
        val measuredKids = kids.mapIndexed { i, child ->
            val childWidth = if (fixed[i] > 0) fixed[i] else perFlex
            measure(child, EditState.NONE, childWidth, defaults, skin, options, data, scale)
        }.filterNotNull()
        if (measuredKids.isEmpty()) return null

        // 3) 摆位
        val cross = el.crossAlign ?: HAlign.CENTER
        val placed = ArrayList<Placed>(measuredKids.size)
        var innerW: Int
        var innerH: Int
        when (el.orientation) {
            GroupOrientation.ROW -> {
                val rowH = measuredKids.maxOf { it.height }
                var cx = 0
                measuredKids.forEach { k ->
                    placed += Placed(k, cx, crossOffset(k.height, rowH, cross))
                    cx += k.width + gap
                }
                innerW = (cx - gap).coerceAtLeast(1)
                innerH = rowH
            }
            GroupOrientation.COLUMN -> {
                val colW = measuredKids.maxOf { it.width }
                var cy = 0
                measuredKids.forEach { k ->
                    placed += Placed(k, crossOffset(k.width, colW, cross), cy)
                    cy += k.height + gap
                }
                innerW = colW
                innerH = (cy - gap).coerceAtLeast(1)
            }
            GroupOrientation.STACK -> {
                innerW = measuredKids.maxOf { it.width }
                innerH = measuredKids.maxOf { it.height }
                measuredKids.forEach { k ->
                    placed += Placed(k, crossOffset(k.width, innerW, cross), crossOffset(k.height, innerH, cross))
                }
            }
        }
        val factor = edit.scale.coerceIn(0.05f, 6f)
        return Measured(
            el = el,
            edit = edit,
            width = (innerW * factor).roundToInt().coerceAtLeast(1),
            height = (innerH * factor).roundToInt().coerceAtLeast(1),
            layout = null,
            children = placed,
            innerWidth = innerW,
            innerHeight = innerH,
        )
    }

    private fun crossOffset(size: Int, container: Int, align: HAlign): Int = when (align) {
        HAlign.LEFT -> 0
        HAlign.CENTER -> (container - size) / 2
        HAlign.RIGHT -> container - size
    }

    private fun resolveX(
        m: Measured,
        padLeft: Int,
        padRight: Int,
        canvasWidth: Int,
        scale: Float,
    ): Int {
        val el = m.el
        // 用户锚定：水平吸附到左 / 中 / 右
        if (m.edit.placement != ElementPlacement.SKIN) {
            return when (m.edit.placement) {
                ElementPlacement.BOTTOM_LEFT, ElementPlacement.TOP_LEFT ->
                    padLeft + el.margin.left.px(scale)
                ElementPlacement.BOTTOM_RIGHT, ElementPlacement.TOP_RIGHT ->
                    canvasWidth - padRight - m.width - el.margin.right.px(scale)
                else -> ((canvasWidth - m.width) / 2f).roundToInt()
            }
        }
        return when (el.x.align) {
            HAlign.LEFT -> padLeft + el.x.offset.px(scale) + el.margin.left.px(scale)
            HAlign.CENTER -> ((canvasWidth - m.width) / 2f).roundToInt() + el.x.offset.px(scale)
            HAlign.RIGHT -> canvasWidth - padRight - m.width + el.x.offset.px(scale) - el.margin.right.px(scale)
        }
    }

    private fun resolveY(
        m: Measured,
        index: Int,
        flowY: Map<Int, Int>,
        padTop: Int,
        padBottom: Int,
        canvasHeight: Int,
        scale: Float,
    ): Int {
        val el = m.el
        // 用户锚定：垂直吸附到上 / 下 / 居中（占位仍按流式保留，见 render 里的说明）
        if (m.edit.placement != ElementPlacement.SKIN) {
            return when (m.edit.placement) {
                ElementPlacement.TOP_LEFT, ElementPlacement.TOP_CENTER, ElementPlacement.TOP_RIGHT ->
                    padTop + el.margin.top.px(scale)
                ElementPlacement.CENTER -> ((canvasHeight - m.height) / 2f).roundToInt()
                else -> canvasHeight - padBottom - m.height - el.margin.bottom.px(scale)
            }
        }
        return when (el.y.mode) {
            VMode.FLOW -> flowY[index] ?: padTop
            VMode.TOP -> el.y.offset.px(scale) + el.margin.top.px(scale)
            VMode.CENTER -> ((canvasHeight - m.height) / 2f).roundToInt()
            VMode.BOTTOM -> canvasHeight - padBottom - m.height - el.margin.bottom.px(scale)
        }
    }

    // ================================================================ 元素绘制

    /**
     * 画一个元素。**通用属性**在这里落地（引擎对"类型"的唯一知识，就是每种类型如何响应这些属性）：
     *  · [EditState.color]      → 文本字色 / 二维码码点 / 分割线颜色 / 图片着色
     *  · [EditState.solidColor] → 元素背后的底色（文本高亮、图片底板、二维码静默区）
     *  · [EditState.shape]      → 图片与二维码的圆角 / 圆形
     *  · [EditState.bitmap]     → 元素的"源图"（图片内容 / 二维码中心 logo）
     *  · [EditState.scale]      → 已在 [measure] 里乘到生效尺寸（文字字号 / 图片框 / 组合整体）
     *  · 显隐 / 位移 / 锚点 → 在 [render] 与 [resolveX]/[resolveY] 里统一处理
     */
    private fun drawElement(
        canvas: Canvas,
        m: Measured,
        x: Int,
        y: Int,
        defaults: ElementDefaults,
        skin: LoadedSkin,
        options: RenderOptions,
        data: LyricShareData,
        temporaries: MutableList<Bitmap>,
        scale: Float,
        effectiveCover: Bitmap?,
        canvasWidth: Int,
        canvasHeight: Int,
    ) {
        val el = m.el
        val edit = m.edit

        // 元素底板（BACKGROUND）：二维码由生成器自己铺底，这里跳过
        if (edit.solidColor != null && el.type != ElementType.QRCODE) {
            val circle = (if (edit.shape != ImageShape.DEFAULT) edit.shape else null) == ImageShape.CIRCLE || el.circle
            val r = when {
                circle -> min(m.width, m.height) / 2f
                edit.shape == ImageShape.ROUNDED -> 24.px(scale).toFloat()
                else -> el.cornerRadius.px(scale).toFloat()
            }
            canvas.drawRoundRect(
                RectF(x.toFloat(), y.toFloat(), (x + m.width).toFloat(), (y + m.height).toFloat()),
                r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = edit.solidColor },
            )
        }

        when (el.type) {
            ElementType.TEXT, ElementType.LYRIC -> {
                val layout = m.layout ?: return
                canvas.save()
                canvas.translate(x.toFloat(), y.toFloat())
                layout.draw(canvas)
                canvas.restore()
            }

            ElementType.IMAGE -> {
                // 源图：用户换的 > 封面位用专辑图 > 皮肤自带图
                val src = edit.bitmap ?: when {
                    el.isCoverSlot -> effectiveCover
                    el.useCover -> effectiveCover
                    el.src != null -> skin.assets.bitmap(el.src)
                    else -> null
                } ?: return
                val dst = RectF(x.toFloat(), y.toFloat(), (x + m.width).toFloat(), (y + m.height).toFloat())
                val shape = if (edit.shape != ImageShape.DEFAULT) edit.shape else ImageShape.DEFAULT
                val circle = if (shape != ImageShape.DEFAULT) shape == ImageShape.CIRCLE else el.circle
                val radiusPx = when {
                    shape == ImageShape.ROUNDED -> 24.px(scale).toFloat()
                    shape == ImageShape.RECT || shape == ImageShape.CIRCLE -> 0f
                    else -> el.cornerRadius.px(scale).toFloat()
                }
                if (el.blur > 0) {
                    val blurred = BlurUtils.blur(src, el.blur.px(scale))
                    temporaries += blurred
                    drawBitmapRounded(canvas, blurred, dst, circle, radiusPx, el.scaleType, el.shadow, scale, edit.color)
                } else {
                    drawBitmapRounded(canvas, src, dst, circle, radiusPx, el.scaleType, el.shadow, scale, edit.color)
                }
                el.scrim?.let { scrim ->
                    if (Color.alpha(scrim) > 0) {
                        val r = if (circle) min(dst.width(), dst.height()) / 2f else radiusPx
                        canvas.drawRoundRect(dst, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = scrim })
                    }
                }
            }

            ElementType.QRCODE -> drawQr(canvas, m, x, y, skin, data, options, temporaries, effectiveCover)

            ElementType.DIVIDER -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = edit.color ?: el.color ?: defaults.color }
                canvas.drawRect(
                    x.toFloat(), y.toFloat(), (x + m.width).toFloat(), (y + m.height).toFloat(), paint
                )
            }

            ElementType.SPACER -> Unit // 只是留白

            ElementType.GROUP -> {
                // 组合：整体缩放由 canvas.scale 完成，子元素按量好的相对偏移绘制（可嵌套）
                val factor = edit.scale.coerceIn(0.05f, 6f)
                canvas.save()
                canvas.translate(x.toFloat(), y.toFloat())
                if (factor != 1f) canvas.scale(factor, factor)
                m.children.forEach { child ->
                    val childM = child.measured
                    drawElement(
                        canvas, childM, child.dx, child.dy, defaults, skin, options, data,
                        temporaries, scale, effectiveCover, canvasWidth, canvasHeight,
                    )
                }
                canvas.restore()
            }
        }
    }

    /** 生成并绘制二维码（位置由调用方给出，便于样式二把它摆到信息带里）。 */
    private fun drawQr(
        canvas: Canvas,
        m: Measured,
        x: Int,
        y: Int,
        skin: LoadedSkin,
        data: LyricShareData,
        options: RenderOptions,
        temporaries: MutableList<Bitmap>,
        effectiveCover: Bitmap?,
    ) {
        val el = m.el
        val content = data.qrContent ?: return
        val qr = el.qr
        val logo = when {
            qr.logoUseCover -> effectiveCover
            qr.logo != null -> skin.assets.bitmap(qr.logo)
            else -> null
        }
        val qrBitmap = QrCodeGenerator.generate(
            content = content,
            sizePx = m.width,
            // 通用属性：COLOR = 码点颜色；BACKGROUND = 静默区底色（可透明）
            foreground = m.edit.color ?: el.color ?: Color.BLACK,
            background = m.edit.solidColor ?: qr.backgroundColor,
            errorCorrection = qr.errorCorrection,
            quietZone = qr.quietZone,
            logo = logo,
            logoScale = qr.logoScale,
        ) ?: return
        temporaries += qrBitmap
        canvas.drawBitmap(
            qrBitmap, null,
            RectF(x.toFloat(), y.toFloat(), (x + m.width).toFloat(), (y + m.height).toFloat()),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
    }

    /** 样式二里「歌名 + 艺人」两段文本的布局结果。 */
    private class SongInfoLayout(
        val title: StaticLayout,
        val artists: StaticLayout?,
        val gap: Int,
    ) {
        val height: Int get() = title.height + (artists?.let { gap + it.height } ?: 0)
    }

    /** 按信息带宽度排版歌名/艺人（宽度和流式排版时不同，必须重新排一次）。 */
    private fun buildSongInfoLayouts(
        titleEl: SkinElement?,
        artistsEl: SkinElement?,
        width: Int,
        defaults: ElementDefaults,
        skin: LoadedSkin,
        options: RenderOptions,
        data: LyricShareData,
        scale: Float,
    ): SongInfoLayout? {
        if (titleEl == null) return null
        val titleText = resolveText(titleEl, data).ifBlank { return null }
        val titleLayout = buildLayout(
            titleText,
            buildTextPaint(titleEl, defaults, skin, options, scale),
            width, titleEl, defaults, scale,
        )
        val artistsLayout = artistsEl?.let { el ->
            val text = resolveText(el, data)
            if (text.isBlank()) null
            else buildLayout(text, buildTextPaint(el, defaults, skin, options, scale), width, el, defaults, scale)
        }
        return SongInfoLayout(titleLayout, artistsLayout, 10.px(scale))
    }

    /**
     * 歌曲信息样式二：画布底部一行 —— 二维码在一侧，「歌名 + 艺人」在另一侧，纵向居中。
     * 二维码靠左（BOTTOM_LEFT）时文字在右，其余情况二维码靠右、文字在左。
     */
    private fun drawSongInfoBand(
        canvas: Canvas,
        skin: LoadedSkin,
        data: LyricShareData,
        options: RenderOptions,
        titleEl: SkinElement,
        artistsEl: SkinElement?,
        qrMeasured: Measured?,
        layouts: SongInfoLayout,
        padLeft: Int,
        padRight: Int,
        padBottom: Int,
        canvasWidth: Int,
        canvasHeight: Int,
        scale: Float,
        temporaries: MutableList<Bitmap>,
        effectiveCover: Bitmap?,
    ) {
        val gap = 32.px(scale)
        val qrW = qrMeasured?.width ?: 0
        val qrH = qrMeasured?.height ?: 0
        // 二维码自己的锚点（通用属性 PLACEMENT）决定它在这一行的左侧还是右侧
        val qrAtLeft = (qrMeasured?.edit?.placement ?: ElementPlacement.SKIN) == ElementPlacement.BOTTOM_LEFT
        val bandHeight = max(qrH, layouts.height)
        val bandTop = canvasHeight - padBottom - bandHeight

        qrMeasured?.let { qr ->
            val qx = if (qrAtLeft) padLeft else canvasWidth - padRight - qrW
            val qy = bandTop + (bandHeight - qrH) / 2
            drawQr(canvas, qr, qx, qy, skin, data, options, temporaries, effectiveCover)
        }

        val textX = if (qrAtLeft) padLeft + qrW + gap else padLeft
        val textY = bandTop + (bandHeight - layouts.height) / 2
        canvas.save()
        canvas.translate(textX.toFloat(), textY.toFloat())
        layouts.title.draw(canvas)
        layouts.artists?.let {
            canvas.translate(0f, (layouts.title.height + layouts.gap).toFloat())
            it.draw(canvas)
        }
        canvas.restore()
    }

    /** 画圆角/圆形图片：用 BitmapShader 才能既裁剪出圆角、又保持 centerCrop/fitCenter 语义。 */
    private fun drawBitmapRounded(
        canvas: Canvas,
        source: Bitmap,
        dst: RectF,
        circle: Boolean,
        radiusPx: Float,
        scaleType: ScaleType,
        shadow: ShadowSpec?,
        scale: Float,
        tint: Int? = null,
    ) {
        if (dst.width() <= 0f || dst.height() <= 0f) return
        val radius = if (circle) min(dst.width(), dst.height()) / 2f else radiusPx

        // 阴影：先画一层带 shadowLayer 的实心块，图片随后盖上去
        shadow?.takeIf { it.enabled }?.let { sh ->
            canvas.drawRoundRect(
                dst, radius, radius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    setShadowLayer(
                        sh.radius.px(scale).toFloat(),
                        sh.dx.px(scale).toFloat(),
                        sh.dy.px(scale).toFloat(),
                        sh.color,
                    )
                }
            )
        }

        val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val matrix = Matrix()
        val sx = dst.width() / source.width
        val sy = dst.height() / source.height
        val s = if (scaleType == ScaleType.CENTER_CROP) max(sx, sy) else min(sx, sy)
        matrix.setScale(s, s)
        matrix.postTranslate((dst.width() - source.width * s) / 2f, (dst.height() - source.height * s) / 2f)
        shader.setLocalMatrix(matrix)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            isFilterBitmap = true
            // 通用属性 COLOR：图片"着色"（用 SRC_IN 给位图整体上色）
            if (tint != null) colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
        }
        canvas.save()
        canvas.translate(dst.left, dst.top)
        canvas.drawRoundRect(RectF(0f, 0f, dst.width(), dst.height()), radius, radius, paint)
        canvas.restore()
    }

    // ================================================================ 背景

    private fun drawBackground(
        canvas: Canvas,
        width: Int,
        height: Int,
        skin: LoadedSkin,
        data: LyricShareData,
        adjust: EditState,
        temporaries: MutableList<Bitmap>,
    ) {
        val bg = skin.spec.canvas.background

        // 用户选了「纯色」→ 直接铺色（比图片优先）。
        // 注意：visible=false 表示"不用背景图"，此时连纯色一起跳过，回落到皮肤声明的底色/渐变。
        if (adjust.visible) {
            adjust.solidColor?.let { solid ->
                canvas.drawColor(solid)
                return
            }
        }

        // 背景图优先级：用户在界面上选的图 > 皮肤声明的封面 > 皮肤自带图
        val image = when {
            !adjust.visible -> null
            adjust.bitmap != null -> adjust.bitmap
            bg.useCover -> data.cover
            else -> bg.src?.let { skin.assets.bitmap(it) }
        }

        if (image != null) {
            // 先缩到小图再模糊：半径同比缩小，既快又更平滑。
            // 这里**不预先裁剪**，交给后面的 shader 做缩放/平移，用户才能拖动画面。
            val smallWidth = min(width, 720)
            val small = BitmapUtils.scaleToWidth(image, smallWidth)
            if (small !== image) temporaries += small
            val prepared = if (bg.blur > 0) {
                val r = max(1, (bg.blur * smallWidth / width.toFloat()).roundToInt())
                BlurUtils.blur(small, r).also { if (it !== small) temporaries += it }
            } else {
                small
            }

            // 铺满画布 + 用户缩放 + 用户平移，全部通过 shader 矩阵实现
            val shader = BitmapShader(prepared, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val matrix = Matrix()
            val base = max(width / prepared.width.toFloat(), height / prepared.height.toFloat())
            val s = base * adjust.scale.coerceIn(0.2f, 5f)
            matrix.setScale(s, s)
            matrix.postTranslate(
                (width - prepared.width * s) / 2f + width * adjust.offsetX,
                (height - prepared.height * s) / 2f + height * adjust.offsetY,
            )
            shader.setLocalMatrix(matrix)
            canvas.drawRect(
                0f, 0f, width.toFloat(), height.toFloat(),
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.shader = shader
                    isFilterBitmap = true
                }
            )
            bg.scrim?.let { scrim ->
                if (Color.alpha(scrim) > 0) canvas.drawColor(scrim)
            }
            return
        }

        when {
            bg.type == BgType.GRADIENT && bg.colors.size >= 2 -> {
                val rad = Math.toRadians(bg.angle.toDouble())
                val cx = width / 2f
                val cy = height / 2f
                val length = (abs(width * cos(rad)) + abs(height * sin(rad))) / 2f
                val dx = (sin(rad) * length).toFloat()
                val dy = (cos(rad) * length).toFloat()
                val paint = Paint().apply {
                    shader = LinearGradient(
                        cx - dx, cy - dy, cx + dx, cy + dy,
                        bg.colors.toIntArray(), null, Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }

            else -> canvas.drawColor(bg.color)
        }
    }

    // ================================================================ 文本

    private fun buildTextPaint(
        el: SkinElement,
        defaults: ElementDefaults,
        skin: LoadedSkin,
        options: RenderOptions,
        scale: Float,
        edit: EditState = EditState.NONE,
    ): TextPaint {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        // 通用属性 SCALE：对文本就是字号倍数
        paint.textSize = (el.size ?: defaults.size) * scale * edit.scale.coerceIn(0.05f, 6f)
        // 通用属性 COLOR：元素自己的设置 > 文本默认样式的设置 > 皮肤声明
        paint.color = edit.color ?: options.textEdit.color ?: el.color ?: defaults.color
        // 通用属性 FONT：解析失败会自动回落默认字体
        paint.typeface = fontProvider.resolve(
            edit.font ?: options.textEdit.font ?: el.font ?: defaults.font,
            skin.assets,
        )
        paint.isAntiAlias = true
        paint.isSubpixelText = true
        // Paint.letterSpacing 的单位是 em，皮肤里写的是参考像素，这里换算
        paint.letterSpacing = ((el.letterSpacing ?: defaults.letterSpacing) * scale) / paint.textSize
        el.shadow?.takeIf { it.enabled }?.let {
            paint.setShadowLayer(
                it.radius.px(scale).toFloat(),
                it.dx.px(scale).toFloat(),
                it.dy.px(scale).toFloat(),
                it.color,
            )
        }
        return paint
    }

    private fun buildLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        el: SkinElement,
        defaults: ElementDefaults,
        scale: Float,
    ): StaticLayout {
        val alignment = when (el.align ?: defaults.align) {
            HAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
            HAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
            HAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }
        val builder = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            .setAlignment(alignment)
            .setLineSpacing((el.lineSpacing ?: defaults.lineSpacing) * scale, 1f)
            .setIncludePad(false)
        if (el.ellipsize) builder.setEllipsize(TextUtils.TruncateAt.END)
        el.maxLines?.takeIf { it > 0 }?.let { builder.setMaxLines(it) }
        return builder.build()
    }

    /** 展开文本占位符；LYRIC 元素没写 text 时直接用选中的歌词行。 */
    private fun resolveText(el: SkinElement, data: LyricShareData): String {
        val template = el.text
        if (template == null) {
            return if (el.type == ElementType.LYRIC) data.lyricLines.joinToString("\n") else ""
        }
        return template
            .replace("{title}", data.title)
            .replace("{artists}", data.artists)
            .replace("{album}", data.album.orEmpty())
            .replace("{lines}", data.lyricLines.joinToString("\n"))
            .replace("{firstLine}", data.lyricLines.firstOrNull().orEmpty())
            .replace("{count}", data.lyricLines.size.toString())
            .replace("{brand}", data.brandText.orEmpty())
    }
}
