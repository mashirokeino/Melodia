package com.music.lyricshare.skin

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.io.InputStream

/**
 * 皮肤资源解析器：把皮肤内部的相对路径（"images/bg.png" / "fonts/serif.otf"）
 * 解析成真正的字节流。
 *
 * 内置皮肤 → [AssetSkinAssets]（assets/skins/<id>/…）
 * 下载皮肤 → [FileSkinAssets]（filesDir/lyric_skins/<id>/…）
 * 渲染引擎只依赖这个接口，因此两种来源的皮肤行为完全一致。
 */
interface SkinAssetResolver {

    /** 打开皮肤包内的文件；不存在返回 null。 */
    fun open(path: String): InputStream?

    /** 解码皮肤包内的图片；失败返回 null。 */
    fun bitmap(path: String): Bitmap?

    /** 仅用于日志/调试。 */
    val debugName: String
}

/** assets 里的皮肤目录。 */
class AssetSkinAssets(
    private val assets: AssetManager,
    /** 皮肤根目录，例如 "skins/default"；空串表示 assets 根。 */
    private val root: String,
) : SkinAssetResolver {

    override val debugName: String get() = "assets://$root"

    /** 预览会随参数变化反复重渲染，同皮肤内的图片不该反复解码。 */
    private val bitmapCache = LruCache<String, Bitmap>(8)

    private fun full(path: String) = if (root.isEmpty()) path else "$root/$path"

    override fun open(path: String): InputStream? =
        runCatching { assets.open(full(path)) }.getOrNull()

    override fun bitmap(path: String): Bitmap? {
        bitmapCache.get(path)?.let { return it }
        val decoded = open(path)?.use { BitmapFactory.decodeStream(it) } ?: return null
        bitmapCache.put(path, decoded)
        return decoded
    }
}

/** 本地目录里的皮肤（下载/侧载）。 */
class FileSkinAssets(private val dir: File) : SkinAssetResolver {

    override val debugName: String get() = dir.absolutePath

    private val bitmapCache = LruCache<String, Bitmap>(8)

    override fun open(path: String): InputStream? {
        val f = File(dir, path)
        if (!f.isFile || !f.canonicalPath.startsWith(dir.canonicalPath)) return null // 防目录穿越
        return runCatching { f.inputStream() }.getOrNull()
    }

    override fun bitmap(path: String): Bitmap? {
        bitmapCache.get(path)?.let { return it }
        val decoded = open(path)?.use { BitmapFactory.decodeStream(it) } ?: return null
        bitmapCache.put(path, decoded)
        return decoded
    }
}

/** 空实现：皮肤不含任何外部资源时使用。 */
object EmptySkinAssets : SkinAssetResolver {
    override val debugName: String get() = "empty"
    override fun open(path: String): InputStream? = null
    override fun bitmap(path: String): Bitmap? = null
}
