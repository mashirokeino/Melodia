package com.music.lyricshare.qr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * ============================================================================
 *  二维码生成
 * ============================================================================
 *  思路是「本地生成指向歌曲/分享页的二维码 + 可选中心 logo」，
 *  用 ZXing 实现，参数全部来自皮肤：
 *   · 纠错级别默认 M；**一旦带中心 logo 自动抬到 H**（否则遮住的部分识别不出来）；
 *   · 静默区(quietZone) 可配 0，让皮肤自己控制四周留白；
 *   · 模块对齐整数像素 + 关闭双线性插值，保证小尺寸下也不糊。
 *
 *  说明：本模块**不绑定任何第三方 App 的 URL Scheme**，[content] 完全由调用方决定
 *  （建议用你自己的 scheme 或 https 分享链接）。
 */
object QrCodeGenerator {

    /**
     * @param content 二维码内容，由调用方决定（你自己的 scheme、https 分享链接等）
     * @param sizePx  生成的正方形边长（含静默区），单位像素
     * @param foreground 前景色
     * @param background 背景色
     * @param errorCorrection "L"/"M"/"Q"/"H"
     * @param quietZone 静默区模块数
     * @param logo 中心 logo（可空）
     * @param logoScale logo 占边长比例，建议 ≤ 0.22
     */
    fun generate(
        content: String,
        sizePx: Int,
        foreground: Int = Color.BLACK,
        background: Int = Color.WHITE,
        errorCorrection: String = "M",
        quietZone: Int = 1,
        logo: Bitmap? = null,
        logoScale: Float = 0.2f,
    ): Bitmap? {
        if (content.isBlank() || sizePx <= 0) return null

        // 带 logo 时必须用 H 级纠错，否则中心被遮住会识别失败
        val level = when {
            logo != null -> ErrorCorrectionLevel.H
            else -> when (errorCorrection.uppercase()) {
                "L" -> ErrorCorrectionLevel.L
                "Q" -> ErrorCorrectionLevel.Q
                "H" -> ErrorCorrectionLevel.H
                else -> ErrorCorrectionLevel.M
            }
        }

        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to level,
            EncodeHintType.MARGIN to quietZone.coerceAtLeast(0),
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )

        val matrix = runCatching {
            QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        }.getOrNull() ?: return null

        // 逐像素手绘而不是 createScaledBitmap：保证每个模块是整数像素方块，边缘锐利
        val out = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        val fg = foreground
        val pixels = IntArray(matrix.width * matrix.height)
        var i = 0
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                pixels[i++] = if (matrix.get(x, y)) fg else background
            }
        }
        out.setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)

        if (logo == null) return out

        // 中心贴 logo：先铺一层背景色底（提升容错余量），再居中画 logo
        val result = out.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val side = result.width
        val logoSide = (side * logoScale).toInt().coerceAtLeast(1)
        val left = (side - logoSide) / 2f
        val top = (side - logoSide) / 2f
        val pad = logoSide * 0.08f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = background
        canvas.drawRoundRect(
            RectF(left - pad, top - pad, left + logoSide + pad, top + logoSide + pad),
            logoSide * 0.18f, logoSide * 0.18f, paint
        )
        paint.isFilterBitmap = true
        canvas.drawBitmap(logo, null, RectF(left, top, left + logoSide, top + logoSide), paint)
        out.recycle()
        return result
    }
}
