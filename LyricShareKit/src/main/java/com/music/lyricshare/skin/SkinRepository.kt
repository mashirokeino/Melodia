package com.music.lyricshare.skin

import android.content.Context
import android.graphics.Color
import com.music.lyricshare.util.ZipUtils
import java.io.File

/**
 * ============================================================================
 *  皮肤仓库：负责「皮肤从哪来」和「怎么装」
 * ============================================================================
 *  来源有两处，渲染时对上层完全透明：
 *   1. 内置：assets/skins/<id>/skin.json —— 随包发布，保底可用；
 *   2. 下载：filesDir/lyric_skins/<id>/skin.json —— 服务端下发（zip 包，见 docs）。
 *
 *  注意：这里**不会**因为 assets 里没放皮肤就崩掉 —— 会退化成 [DefaultSkins.FALLBACK]，
 *  保证接入方第一天就能看到效果。
 */
class SkinRepository(private val context: Context) {

    private val installedDir: File get() = File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    /** 全部可用皮肤：内置在前、下载在后。 */
    fun loadAll(): List<LoadedSkin> = loadBundled() + loadDownloaded()

    /** 按 id 找皮肤；找不到返回 null。 */
    fun find(id: String?): LoadedSkin? =
        if (id == null) null else loadAll().firstOrNull { it.id == id }

    /** 内置皮肤。 */
    fun loadBundled(): List<LoadedSkin> {
        val assets = context.assets
        val ids = runCatching { assets.list(SKIN_DIR)?.toList() }.getOrNull().orEmpty()
        val list = ids.mapNotNull { id -> loadOne(AssetSkinAssets(assets, "$SKIN_DIR/$id")) }
        return list.ifEmpty { listOf(DefaultSkins.FALLBACK) }   // 兜底：永远是"有皮肤"的状态
    }

    /** 已下载/侧载的皮肤。 */
    fun loadDownloaded(): List<LoadedSkin> =
        installedDir.listFiles().orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { loadOne(FileSkinAssets(it)) }

    private fun loadOne(assets: SkinAssetResolver): LoadedSkin? = runCatching {
        val json = assets.open("skin.json")?.use { it.readBytes().decodeToString() } ?: return null
        val skin = SkinParser.parse(json, assets, if (assets is FileSkinAssets) SkinSource.DOWNLOADED else SkinSource.BUNDLED)
        // 格式版本过高 → 忽略，避免老引擎渲染出错图
        if (skin.spec.schemaVersion > SkinParser.ENGINE_SCHEMA_VERSION) return null
        if (skin.id.length !in 1..64) return null
        skin
    }.getOrNull()

    /**
     * 安装一个皮肤 zip（服务端下发 / 用户手动导入）。
     *
     * 包结构（skin.json 必须在根目录，或唯一的一级子目录里）：
     *   skin.zip
     *   ├── skin.json
     *   ├── preview.png     （可选，仅作列表缩略图；引擎也能自己渲染缩略图）
     *   ├── images 目录
     *   └── fonts 目录
     *
     * @param expectedMd5 期望的 MD5，传 null 表示不校验（本地导入场景）。
     */
    fun installFromZip(zipFile: File, expectedMd5: String? = null): Result<LoadedSkin> = runCatching {
        require(zipFile.isFile) { "皮肤包不存在" }
        if (expectedMd5 != null) {
            val actual = zipFile.md5()
            require(actual.equals(expectedMd5, ignoreCase = true)) { "MD5 校验失败" }
        }

        val temp = File(context.cacheDir, "skin_install_${System.currentTimeMillis()}")
        ZipUtils.deleteRecursively(temp)
        val count = ZipUtils.extract(zipFile, temp)
        require(count > 0) { "皮肤包为空或不是合法 zip" }

        // 找到 skin.json：根目录优先，其次唯一的一级子目录
        val root = when {
            File(temp, "skin.json").isFile -> temp
            else -> temp.listFiles().orEmpty().firstOrNull { it.isDirectory && File(it, "skin.json").isFile }
        } ?: run { ZipUtils.deleteRecursively(temp); error("皮肤包缺少 skin.json") }

        val skin = loadOne(FileSkinAssets(root)) ?: run {
            ZipUtils.deleteRecursively(temp); error("skin.json 解析失败或格式版本过高")
        }
        require(skin.id.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "皮肤 id 非法" }

        // 落地到 filesDir/lyric_skins/<id>
        val target = File(installedDir, skin.id)
        ZipUtils.deleteRecursively(target)
        if (!root.renameTo(target)) {
            root.copyRecursively(target, overwrite = true)
        }
        ZipUtils.deleteRecursively(temp)

        skin.copyWithAssets(FileSkinAssets(target), SkinSource.DOWNLOADED)
    }

    /** 卸载皮肤。 */
    fun remove(id: String) = ZipUtils.deleteRecursively(File(installedDir, id))

    /** 已安装的下载皮肤 id 列表。 */
    fun downloadedIds(): List<String> = installedDir.listFiles().orEmpty().filter { it.isDirectory }.map { it.name }

    private fun LoadedSkin.copyWithAssets(assets: SkinAssetResolver, source: SkinSource) =
        LoadedSkin(spec, assets, source)

    private fun File.md5(): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
        inputStream().use { ins ->
            val buf = ByteArray(8192)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val SKIN_DIR = "skins"
        private const val DIR_NAME = "lyric_skins"
    }
}

/**
 * 代码内置的兜底皮肤：只有在 assets/skins 为空时才会用到。
 * 它的存在保证「模块接入后立刻可用」，接入方补齐 assets 后即可删除。
 */
object DefaultSkins {

    val FALLBACK: LoadedSkin = LoadedSkin(
        spec = SkinSpec(
            id = "fallback",
            name = "默认",
            canvas = CanvasSpec(
                width = 1080,
                height = null,
                minHeight = 1440,
                padding = Insets(72, 96, 72, 64),
                background = BackgroundSpec(type = BgType.COLOR, color = Color.WHITE),
            ),
            defaults = ElementDefaults(
                color = Color.parseColor("#FF1A1A1A"),
                size = 46f,
                lineSpacing = 26f,
            ),
            elements = listOf(
                SkinElement(
                    type = ElementType.IMAGE,
                    role = "cover",
                    useCover = true,
                    width = 936,
                    height = 936,
                    cornerRadius = 20,
                    x = PosX(HAlign.CENTER, 0),
                ),
                SkinElement(
                    type = ElementType.TEXT,
                    text = "{title} • {artists}",
                    size = 48f,
                    color = Color.parseColor("#FF111111"),
                    maxLines = 1,
                    margin = Insets(top = 40, bottom = 36),
                ),
                SkinElement(
                    type = ElementType.LYRIC,
                    size = 44f,
                    color = Color.parseColor("#FF333333"),
                    lineSpacing = 28f,
                    maxLines = 16,
                    y = PosY(VMode.FLOW, 0),
                ),
                SkinElement(
                    type = ElementType.QRCODE,
                    width = 160,
                    height = 160,
                    x = PosX(HAlign.CENTER, 0),
                    margin = Insets(top = 48),
                    qr = QrSpec(quietZone = 0, errorCorrection = "M"),
                ),
            ),
        ),
        assets = EmptySkinAssets,
        source = SkinSource.BUNDLED,
    )
}
