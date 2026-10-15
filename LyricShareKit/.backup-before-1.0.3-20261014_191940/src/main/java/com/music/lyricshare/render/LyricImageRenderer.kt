package com.music.lyricshare.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
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
import com.music.lyricshare.skin.ShadowSpec
import com.music.lyricshare.skin.SkinElement
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
data class ImageAdjust(
    /** 替换用的图片；null = 用皮肤/运行时默认图。 */
    val bitmap: Bitmap? = null,
    /** false = 这一项不绘制（"是否添加封面"），且不占任何空间。 */
    val visible: Boolean = true,
    val shape: ImageShape = ImageShape.DEFAULT,
    /** 缩放系数（1 = 皮肤声明值）。 */
    val scale: Float = 1f,
    /** 水平偏移，相对画布宽的比例（-0.25 = 左移 1/4 画布）。 */
    val offsetX: Float = 0f,
    /** 垂直偏移，相对画布高的比例。 */
    val offsetY: Float = 0f,
) {
    val isDefault: Boolean
        get() = bitmap == null && visible && shape == ImageShape.DEFAULT &&
            scale == 1f && offsetX == 0f && offsetY == 0f

    companion object {
        val NONE = ImageAdjust()
    }
}

data class RenderOptions(
    /** 输出宽度（像素）；null = 用皮肤参考宽度。预览传小值即可，几何关系与导出完全一致。 */
    val widthPx: Int? = null,
    /** 用户在界面上选的字体，优先级最高（覆盖皮肤自带字体）。 */
    val fontOverride: FontSpec? = null,
    /** 是否绘制二维码。false 时**不进布局、不占空间**。 */
    val showQrCode: Boolean = true,
    /** 是否绘制 role=="brand" 的元素（"来自 XXX"）。默认 false：不画也不预留。 */
    val showBrandElement: Boolean = false,
    /** 封面的用户调整（换图/形状/大小/位置/显隐）。 */
    val coverAdjust: ImageAdjust? = null,
    /** 背景的用户调整（换图/缩放/平移/显隐）。 */
    val backgroundAdjust: ImageAdjust? = null,
)

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
        val width: Int,
        val height: Int,
        val layout: StaticLayout?,
    )

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

        // 用户在界面上对封面/背景的调整（换图、显隐、形状、大小、位置）
        val coverAdjust = options.coverAdjust ?: ImageAdjust.NONE
        val backgroundAdjust = options.backgroundAdjust ?: ImageAdjust.NONE
        // 换过图就用用户选的，否则用调用方给的（专辑图）
        val effectiveCover = coverAdjust.bitmap ?: data.cover

        // ---------------- 0) 元素过滤：被关掉的东西直接不进布局，也就不占空间 ----------------
        val visible = spec.elements.filter { el ->
            when {
                el.isBrand && !options.showBrandElement -> false
                el.type == ElementType.QRCODE && (!options.showQrCode || data.qrContent.isNullOrBlank()) -> false
                // 封面位：用户关掉"显示封面"、或根本没有封面图 → 整条不参与布局，不留空
                el.isCoverSlot && (!coverAdjust.visible || effectiveCover == null) -> false
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
        val measured = visible.mapNotNull { el ->
            measure(el, contentWidth, defaults, skin, options, data, scale, coverAdjust)
        }

        val flowY = HashMap<Int, Int>(measured.size)
        var cursor = padTop
        measured.forEachIndexed { index, m ->
            val el = m.el
            if (el.y.mode == VMode.FLOW) {
                val y = cursor + el.margin.top.px(scale)
                flowY[index] = y
                cursor = y + m.height + el.margin.bottom.px(scale)
            }
        }
        val contentBottom = cursor + padBottom
        val canvasHeight = canvasSpec.height?.px(scale)
            ?: max(canvasSpec.minHeight.px(scale), contentBottom)

        // ---------------- 2) 绘制 ----------------
        val output = Bitmap.createBitmap(outputWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val temporaries = ArrayList<Bitmap>(4)

        drawBackground(canvas, outputWidth, canvasHeight, skin, data, backgroundAdjust, temporaries)

        measured.forEachIndexed { index, m ->
            val x = resolveX(m, padLeft, padRight, outputWidth, scale)
            val y = resolveY(m, index, flowY, padTop, padBottom, canvasHeight, scale)
            drawElement(
                canvas, m, x, y, defaults, skin, options, data, temporaries, scale,
                effectiveCover, coverAdjust, outputWidth, canvasHeight,
            )
        }

        // 引擎自己产生的临时位图（模糊图/二维码）用完即回收，输入位图一律不碰
        temporaries.forEach { runCatching { it.recycle() } }
        return output
    }

    // ================================================================ 度量

    private fun measure(
        el: SkinElement,
        contentWidth: Int,
        defaults: ElementDefaults,
        skin: LoadedSkin,
        options: RenderOptions,
        data: LyricShareData,
        scale: Float,
        coverAdjust: ImageAdjust,
    ): Measured? {
        // 封面位的大小受用户调整影响：scale 直接作用于占位尺寸，后面的文字会跟着重排
        val sizeFactor = if (el.isCoverSlot) coverAdjust.scale.coerceIn(0.1f, 3f) else 1f
        val width = (el.width?.px(scale)?.coerceAtLeast(1) ?: contentWidth)
        return when (el.type) {
            ElementType.TEXT, ElementType.LYRIC -> {
                val text = resolveText(el, data)
                if (text.isBlank()) return null                       // 空文本 = 这个元素不存在（不预留）
                val paint = buildTextPaint(el, defaults, skin, options, scale)
                val layout = buildLayout(text, paint, width, el, defaults, scale)
                Measured(el, width, layout.height, layout)
            }

            ElementType.IMAGE -> {
                val declaredWidth = el.width?.px(scale)
                val declaredHeight = el.height?.px(scale)
                val w = ((declaredWidth ?: contentWidth) * sizeFactor).roundToInt().coerceAtLeast(1)
                val h = ((declaredHeight ?: declaredWidth ?: contentWidth) * sizeFactor).roundToInt().coerceAtLeast(1)
                Measured(el, if (el.isCoverSlot) w else width, h, null)
            }

            ElementType.QRCODE -> {
                val side = el.width?.px(scale) ?: el.height?.px(scale) ?: 240.px(scale)
                Measured(el, side, el.height?.px(scale) ?: side, null)
            }

            ElementType.DIVIDER -> Measured(el, width, el.height?.px(scale) ?: max(1, scale.roundToInt()), null)

            ElementType.SPACER -> Measured(el, width, el.height?.px(scale) ?: 0, null)
        }
    }

    private fun resolveX(m: Measured, padLeft: Int, padRight: Int, canvasWidth: Int, scale: Float): Int {
        val el = m.el
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
        return when (el.y.mode) {
            VMode.FLOW -> flowY[index] ?: padTop
            VMode.TOP -> el.y.offset.px(scale) + el.margin.top.px(scale)
            VMode.CENTER -> ((canvasHeight - m.height) / 2f).roundToInt()
            VMode.BOTTOM -> canvasHeight - padBottom - m.height - el.margin.bottom.px(scale)
        }
    }

    // ================================================================ 元素绘制

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
        coverAdjust: ImageAdjust,
        canvasWidth: Int,
        canvasHeight: Int,
    ) {
        val el = m.el
        when (el.type) {
            ElementType.TEXT, ElementType.LYRIC -> {
                val layout = m.layout ?: return
                canvas.save()
                canvas.translate(x.toFloat(), y.toFloat())
                layout.draw(canvas)
                canvas.restore()
            }

            ElementType.IMAGE -> {
                val isCover = el.isCoverSlot
                val src = when {
                    isCover -> effectiveCover
                    el.src != null -> skin.assets.bitmap(el.src)
                    else -> null
                } ?: return
                val dst = RectF(x.toFloat(), y.toFloat(), (x + m.width).toFloat(), (y + m.height).toFloat())
                // 用户在二级面板里挪的位置：相对画布宽/高的比例
                if (isCover && (coverAdjust.offsetX != 0f || coverAdjust.offsetY != 0f)) {
                    dst.offset(canvasWidth * coverAdjust.offsetX, canvasHeight * coverAdjust.offsetY)
                }
                // 形状：用户在面板里选的优先于皮肤声明
                val circle = if (isCover && coverAdjust.shape != ImageShape.DEFAULT) {
                    coverAdjust.shape == ImageShape.CIRCLE
                } else {
                    el.circle
                }
                val radiusPx = when {
                    !isCover || coverAdjust.shape == ImageShape.DEFAULT -> el.cornerRadius.px(scale).toFloat()
                    coverAdjust.shape == ImageShape.ROUNDED -> 24.px(scale).toFloat()
                    else -> 0f
                }
                if (el.blur > 0) {
                    val blurred = BlurUtils.blur(src, el.blur.px(scale))
                    temporaries += blurred
                    drawBitmapRounded(canvas, blurred, dst, circle, radiusPx, el.scaleType, el.shadow, scale)
                } else {
                    drawBitmapRounded(canvas, src, dst, circle, radiusPx, el.scaleType, el.shadow, scale)
                }
                el.scrim?.let { scrim ->
                    if (Color.alpha(scrim) > 0) {
                        val r = if (circle) min(dst.width(), dst.height()) / 2f else radiusPx
                        canvas.drawRoundRect(dst, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = scrim })
                    }
                }
            }

            ElementType.QRCODE -> {
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
                    foreground = el.color ?: Color.BLACK,
                    background = qr.backgroundColor,
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

            ElementType.DIVIDER -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = el.color ?: defaults.color }
                canvas.drawRect(
                    x.toFloat(), y.toFloat(), (x + m.width).toFloat(), (y + m.height).toFloat(), paint
                )
            }

            ElementType.SPACER -> Unit // 只是留白
        }
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
        adjust: ImageAdjust,
        temporaries: MutableList<Bitmap>,
    ) {
        val bg = skin.spec.canvas.background
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
    ): TextPaint {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = (el.size ?: defaults.size) * scale
        paint.color = el.color ?: defaults.color
        // 用户选的字体优先于皮肤字体；解析失败会自动回落默认字体
        paint.typeface = fontProvider.resolve(options.fontOverride ?: el.font ?: defaults.font, skin.assets)
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
