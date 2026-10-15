package com.music.lyricshare.share

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ============================================================================
 *  出图之后的两条路：存相册 / 调系统分享
 * ============================================================================
 *  刻意不接管任何社交 SDK —— 直接走 Android 原生分享（ACTION_SEND + FileProvider），
 *  微信/QQ/相册/隔空投送等由系统自己列出来，接入方零成本。
 */
object ImageExporter {

    /** 分享时授予临时读权限的 authority，与 AndroidManifest 里的 provider 一致。 */
    fun fileProviderAuthority(context: Context): String = "${context.packageName}.lyricshare.fileprovider"

    /** 生成带时间戳的文件名，避免连续分享互相覆盖。 */
    fun timestampName(prefix: String = "lyric", suffix: String = "png"): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        return "${prefix}_$stamp.$suffix"
    }

    // ---------------------------------------------------------------- 存相册

    /**
     * 保存到系统相册。
     * · API 29+ 走 MediaStore + RELATIVE_PATH，不需要任何权限；
     * · API 26~28 需要 WRITE_EXTERNAL_STORAGE（已在模块 Manifest 里声明 maxSdkVersion=28），
     *   调用方需先申请权限，否则会返回失败。
     */
    fun saveToGallery(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        albumName: String = "Melodia",
        quality: Int = 100,
    ): Result<Uri> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, bitmap, fileName, albumName, quality)
        } else {
            saveViaLegacyPath(context, bitmap, fileName, albumName, quality)
        }
    }

    private fun saveViaMediaStore(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        albumName: String,
        quality: Int,
    ): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$albumName")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore 插入失败")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, quality, out)) error("图片写入失败")
            } ?: error("无法打开输出流")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
        return uri
    }

    @Suppress("DEPRECATION")
    private fun saveViaLegacyPath(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        albumName: String,
        quality: Int,
    ): Uri {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), albumName)
        if (!dir.exists() && !dir.mkdirs()) error("无法创建相册目录")
        val file = File(dir, fileName)
        file.outputStream().use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, quality, out)) error("图片写入失败")
        }
        // 通知系统媒体库刷新，否则相册 App 里看不到
        android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
        return Uri.fromFile(file)
    }

    // ------------------------------------------------------------ 系统分享

    /**
     * 调用 Android 原生分享面板。
     * 先把 Bitmap 写到 cacheDir/lyric_share/，再用 FileProvider 授权；
     * 这里不做任何"指定微信"之类的定向逻辑，交给系统。
     */
    fun shareViaSystem(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        text: String? = null,
        chooserTitle: String? = null,
    ): Result<Unit> = runCatching {
        val file = writeToShareCache(context, bitmap, fileName)
        val uri = FileProvider.getUriForFile(context, fileProviderAuthority(context), file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (!text.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, text)
        }
        // 部分 ROM 只认 ClipData 里的授权
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("lyric_share_image", uri)
        }
        // 从非 Activity Context 启动时必须带 NEW_TASK
        if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /** 写分享缓存文件；同时顺手清理 3 天前的旧图，避免 cacheDir 越积越多。 */
    fun writeToShareCache(context: Context, bitmap: Bitmap, fileName: String): File {
        val dir = File(context.cacheDir, "lyric_share").apply { if (!exists()) mkdirs() }
        val file = File(dir, fileName)
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val expireAt = System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000
        dir.listFiles()?.forEach { f -> if (f.lastModified() < expireAt) runCatching { f.delete() } }
        return file
    }
}
