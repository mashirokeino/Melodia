package com.music.lyricshare.font

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.util.LruCache
import com.music.lyricshare.skin.FontSource
import com.music.lyricshare.skin.FontSpec
import com.music.lyricshare.skin.SkinAssetResolver
import java.io.File
import java.security.MessageDigest

/**
 * ============================================================================
 *  字体解析：把皮肤里的 [FontSpec] 或用户在 UI 上选的字体，变成可绘制的 Typeface
 * ============================================================================
 *  三种来源最终都归一成「一个本地字体文件」再 createFromFile：
 *   · ASSETS（皮肤内自带字体）→ 读出字节、按内容哈希缓存到 cacheDir 再加载，
 *     这样皮肤来自 assets 还是下载目录都走同一条路，不需要 AssetManager 的绝对路径；
 *   · FILE（用户从系统文件选择器挑的字体）→ 直接加载；
 *   · SYSTEM（系统字体族）→ Typeface.create(family, style)。
 *
 *  加粗/斜体统一用 Typeface.create(base, style) 派生，字体本身没有对应字重时由系统合成。
 */
class FontProvider(private val context: Context) {

    private val typefaceCache = LruCache<String, Typeface>(24)

    /** 引擎默认字体：宿主可以通过 [LyricShareKitConfig.defaultFont] 换成自己的品牌字体。 */
    var defaultTypeface: Typeface = Typeface.DEFAULT

    private val fontCacheDir: File
        get() = File(context.cacheDir, "lyric_share_fonts").apply { if (!exists()) mkdirs() }

    /** 用户自己导入的字体目录（持久保存，重启后仍可选）。 */
    private val userFontDir: File
        get() = File(context.filesDir, "lyric_share_fonts").apply { if (!exists()) mkdirs() }

    /** 解析字体；解析失败一律回落到默认字体，绝不让渲染因为字体挂掉。 */
    fun resolve(spec: FontSpec?, skinAssets: SkinAssetResolver?): Typeface {
        if (spec == null) return defaultTypeface
        val key = "${spec.source}|${spec.value}|${spec.bold}|${spec.italic}|${skinAssets?.debugName}"
        typefaceCache.get(key)?.let { return it }

        val base: Typeface? = when (spec.source) {
            FontSource.DEFAULT -> defaultTypeface
            FontSource.SYSTEM -> runCatching {
                Typeface.create(spec.value ?: "sans-serif", Typeface.NORMAL)
            }.getOrNull()
            FontSource.FILE -> spec.value?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() }
            FontSource.ASSETS -> spec.value?.let { loadFromSkin(skinAssets, it) }
        }

        val styled = when {
            base == null -> defaultTypeface
            spec.bold && spec.italic -> Typeface.create(base, Typeface.BOLD_ITALIC)
            spec.bold -> Typeface.create(base, Typeface.BOLD)
            spec.italic -> Typeface.create(base, Typeface.ITALIC)
            else -> base
        }
        typefaceCache.put(key, styled)
        return styled
    }

    private fun loadFromSkin(assets: SkinAssetResolver?, path: String): Typeface? {
        if (assets == null) return null
        val bytes = assets.open(path)?.use { it.readBytes() } ?: return null
        // 按内容哈希缓存成文件：皮肤更新后哈希变化，不会命中旧字体
        val sha = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
        val ext = path.substringAfterLast('.', "ttf")
        val file = File(fontCacheDir, "$sha.$ext")
        if (!file.isFile) runCatching { file.writeBytes(bytes) }
        return runCatching { Typeface.createFromFile(file) }.getOrNull()
    }

    // ------------------------------------------------------------ 字体选择 UI

    /** 皮肤包里可选的字体（assets/fonts 下），返回相对路径。 */
    fun bundledFonts(): List<String> {
        val fonts = runCatching { context.assets.list("fonts")?.toList() }.getOrNull().orEmpty()
        return fonts.filter { it.isFontFile() }.sorted()
    }

    /** 用户已导入的字体文件。 */
    fun userFonts(): List<File> = userFontDir.listFiles().orEmpty().filter { it.isFontFile() }.sortedBy { it.name }
    /** 从系统文件选择器返回的 Uri 导入字体，成功后返回可直接使用的 [FontSpec]。 */
    fun importUserFont(uri: Uri): Result<FontSpec> = runCatching {
        val name = (queryDisplayName(uri) ?: "user_font.ttf").substringAfterLast('/')
        val target = File(userFontDir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: error("无法读取字体文件")
        // 真校验一次能否加载，避免存进来一个坏文件后在渲染时才报错
        Typeface.createFromFile(target)
        FontSpec(source = FontSource.FILE, value = target.absolutePath)
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()

    private fun String.isFontFile() = endsWith(".ttf", true) || endsWith(".otf", true) || endsWith(".ttc", true)

    private fun File.isFontFile() = name.isFontFile()
}
