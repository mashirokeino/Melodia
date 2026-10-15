package com.music.lyricshare.util

import android.graphics.Bitmap

/**
 * ============================================================================
 *  高斯模糊（近似）
 * ============================================================================
 *  为什么不用 RenderScript / RenderEffect：
 *   · RenderScript 已废弃，且在部分国产 ROM 上不可用；
 *   · RenderEffect 需要 API 31+，而本模块 minSdk 26。
 *  这里用「三次可分离盒式模糊」逼近高斯：O(n) 复杂度、纯 Kotlin、任何设备结果一致，
 *  并且绘制目标是软件 Canvas，不涉及硬件加速限制。
 */
internal object BlurUtils {

    /**
     * @param radius 模糊半径（像素）。建议先缩放到目标尺寸再调用，半径别超过短边 1/4。
     * @return 新的 Bitmap；[radius] <= 0 时原样返回。
     */
    fun blur(source: Bitmap, radius: Int): Bitmap {
        if (radius <= 0 || source.width < 2 || source.height < 2) return source
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        // 三次盒式模糊 ≈ 高斯，视觉上足够，且比一次大半径盒式糊更自然
        repeat(3) { boxBlur(pixels, w, h, radius.coerceAtMost(minOf(w, h) / 2)) }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /** 分离式盒式模糊：先横向再纵向，每行/列用滑动窗口求和，O(w*h)。 */
    private fun boxBlur(pixels: IntArray, w: Int, h: Int, radius: Int) {
        if (radius <= 0) return
        val div = radius * 2 + 1
        val tmp = IntArray(pixels.size)

        // ---- 横向 ----
        for (y in 0 until h) {
            val row = y * w
            var a = 0; var r = 0; var g = 0; var b = 0
            for (i in -radius..radius) {
                val p = pixels[row + i.coerceIn(0, w - 1)]
                a += p ushr 24 and 0xFF; r += p ushr 16 and 0xFF
                g += p ushr 8 and 0xFF; b += p and 0xFF
            }
            for (x in 0 until w) {
                tmp[row + x] = ((a / div) shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
                val out = pixels[row + (x - radius).coerceIn(0, w - 1)]
                val inn = pixels[row + (x + radius + 1).coerceIn(0, w - 1)]
                a += (inn ushr 24 and 0xFF) - (out ushr 24 and 0xFF)
                r += (inn ushr 16 and 0xFF) - (out ushr 16 and 0xFF)
                g += (inn ushr 8 and 0xFF) - (out ushr 8 and 0xFF)
                b += (inn and 0xFF) - (out and 0xFF)
            }
        }

        // ---- 纵向（tmp -> pixels）----
        for (x in 0 until w) {
            var a = 0; var r = 0; var g = 0; var b = 0
            for (i in -radius..radius) {
                val p = tmp[i.coerceIn(0, h - 1) * w + x]
                a += p ushr 24 and 0xFF; r += p ushr 16 and 0xFF
                g += p ushr 8 and 0xFF; b += p and 0xFF
            }
            for (y in 0 until h) {
                pixels[y * w + x] = ((a / div) shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
                val out = tmp[(y - radius).coerceIn(0, h - 1) * w + x]
                val inn = tmp[(y + radius + 1).coerceIn(0, h - 1) * w + x]
                a += (inn ushr 24 and 0xFF) - (out ushr 24 and 0xFF)
                r += (inn ushr 16 and 0xFF) - (out ushr 16 and 0xFF)
                g += (inn ushr 8 and 0xFF) - (out ushr 8 and 0xFF)
                b += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }
}
