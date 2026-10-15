package com.music.lyricshare.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.InputStream
import kotlin.math.max

/**
 * 位图工具：解码（含采样与 EXIF 方向矫正）、裁剪、缩放、落盘。
 * 全部为纯 Kotlin/Android 平台 API，不引入额外依赖。
 */
internal object BitmapUtils {

    /**
     * 从 Uri 解码位图，并把最长边限制在 [maxSize] 内（避免用户选了一张 8000x8000 的图直接把内存打爆）。
     * 同时按 EXIF 矫正方向 —— 手机相册里的照片经常带旋转信息，不矫正会出现"躺倒"的封面。
     */
    fun decodeUri(context: Context, uri: Uri, maxSize: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val opts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSize)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        return applyExif(bitmap) { resolver.openInputStream(uri) }
    }

    /** 从文件路径解码（用户导入字体/图片时用不到，但换封面也支持 file:// 场景）。 */
    fun decodeFile(path: String, maxSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSize)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeFile(path, opts) ?: return null
        return applyExif(bitmap) { File(path).inputStream() }
    }

    fun calculateInSampleSize(width: Int, height: Int, maxSize: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (max(w, h) / sample > maxSize) sample *= 2
        return sample.coerceAtLeast(1)
    }

    private fun applyExif(bitmap: Bitmap, reopen: () -> InputStream?): Bitmap {
        val orientation = runCatching {
            reopen()?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                .also { if (it != bitmap) bitmap.recycle() }
        }.getOrDefault(bitmap)
    }

    /** 居中裁剪成 [targetW] x [targetH]，用于封面统一成皮肤要求的比例。 */
    fun centerCrop(source: Bitmap, targetW: Int, targetH: Int): Bitmap {
        if (source.width == targetW && source.height == targetH) return source
        val scale = max(targetW.toFloat() / source.width, targetH.toFloat() / source.height)
        val w = (source.width * scale).toInt().coerceAtLeast(targetW)
        val h = (source.height * scale).toInt().coerceAtLeast(targetH)
        val x = (w - targetW) / 2
        val y = (h - targetH) / 2
        val scaled = Bitmap.createScaledBitmap(source, w, h, true)
        val cropped = Bitmap.createBitmap(scaled, x, y, targetW, targetH)
        if (scaled != cropped && scaled != source) scaled.recycle()
        return cropped
    }

    /** 等比缩放到指定宽度（用于脏数据兜底：封面比例极端时）。 */
    fun scaleToWidth(source: Bitmap, width: Int): Bitmap {
        if (source.width == width) return source
        val h = (source.height.toFloat() * width / source.width).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, h, true)
    }

    fun recycleQuietly(vararg bitmaps: Bitmap?) {
        bitmaps.forEach { bmp -> runCatching { if (bmp != null && !bmp.isRecycled) bmp.recycle() } }
    }
}
