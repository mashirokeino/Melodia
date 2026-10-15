package com.music.lyricshare

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import com.music.lyricshare.font.FontProvider
import com.music.lyricshare.render.EditState
import com.music.lyricshare.render.LyricImageRenderer
import com.music.lyricshare.render.RenderOptions
import com.music.lyricshare.share.ImageExporter
import com.music.lyricshare.skin.FontSpec
import com.music.lyricshare.skin.LoadedSkin
import com.music.lyricshare.skin.SkinRepository
import com.music.lyricshare.util.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ============================================================================
 *  宿主可调配置（进程级，建议在 Application#onCreate 里设置一次）
 * ============================================================================
 */
object LyricShareConfig {

    /**
     * 全局默认字体。换品牌字体只要在这里给一个 [FontSpec]：
     *   LyricShareConfig.defaultFont = FontSpec(FontSource.ASSETS, "fonts/your-brand.otf")
     * 皮肤里单独指定的字体仍然生效；用户在页面上选的字体优先级最高。
     */
    var defaultFont: FontSpec? = null

    /** 保存到相册时的子目录名（Pictures/<这个值>）。 */
    var galleryAlbumName: String = "Melodia"

    /** 导出文件名前缀，最终形如 lyric_20260213_101530_123.png。 */
    var filePrefix: String = "lyric"

    /** 系统分享面板标题。 */
    var chooserTitle: String = "分享歌词图片"

    /** 分享时附带的文案（歌名/歌手由调用方决定），返回 null 表示不带文本。 */
    var shareTextProvider: ((LyricShareRequest) -> String?)? = { request ->
        "「${request.title}」— ${request.artists}"
    }

    /**
     * 二维码的默认内容：请求只给了 [LyricShareRequest.songId] 时用它生成。
     * 默认指向这首歌的网页版链接（扫码即可在浏览器里打开，不依赖任何 App 的私有 Scheme）。
     *
     * 想换成自己的分享域，改这一行即可：
     * ```kotlin
     * LyricShareConfig.defaultQrContent = { id -> "https://your.domain/song/$id" }
     * ```
     * 设为 null 则不再自动生成二维码（页面上也不显示二维码开关）。
     */
    var defaultQrContent: ((Long) -> String?)? = { songId ->
        "https://music.163.com/song?id=$songId"
    }

    /**
     * 封面加载钩子：当 [LyricShareRequest.coverUri] 为空、只给了 http(s) 的
     * [LyricShareRequest.coverUrl] 时，模块会调用它去取图。
     *
     * 本模块刻意不内置图片加载库（不跟宿主的 Coil/Glide 抢依赖），
     * Melodia 里用 Coil 实现即可：
     * ```kotlin
     * LyricShareConfig.coverLoader = { context, url ->
     *     context.imageLoader.execute(
     *         ImageRequest.Builder(context).data(url).size(2048).build()
     *     ).image?.toBitmap()
     * }
     * ```
     */
    var coverLoader: (suspend (Context, String) -> Bitmap?)? = null
}

/**
 * ============================================================================
 *  LyricShareKit 门面
 * ============================================================================
 *  最小接入（Melodia 歌词分享页里加一个按钮即可）：
 *
 *  ```kotlin
 *  // 1) 收集已选歌词（复用你现有的 selectedLyricKeys 逻辑）
 *  val lines = selectedLines()                     // List<String>
 *
 *  // 2) 拉起歌词图片页
 *  LyricShareKit.launch(activity, LyricShareRequest(
 *      title = title, artists = artist, album = album,
 *      lyricLines = lines,
 *      coverUri = currentCoverUri,
 *      qrContent = songShareLink(songId),   // 你自己的 scheme / https 分享链接；传 null 就不出二维码
 *  ))
 *  ```
 *
 *  无界面出图（例如你想自己画预览/后台生成）：
 *  ```kotlin
 *  val bitmap = LyricShareKit.render(context, request, skinId = "dark_card", widthPx = 1080)
 *  LyricShareKit.saveToGallery(context, bitmap, ImageExporter.timestampName())
 *  ```
 */
object LyricShareKit {

    private val lock = Any()

    private var appContext: Context? = null
    private var skinRepositoryRef: SkinRepository? = null
    private var fontProviderRef: FontProvider? = null
    private var rendererRef: LyricImageRenderer? = null

    /** 幂等初始化；任何入口调用一次即可。 */
    @JvmStatic
    fun install(context: Context) {
        synchronized(lock) {
            val app = context.applicationContext
            appContext = app
            if (fontProviderRef == null) {
                fontProviderRef = FontProvider(app).also { provider ->
                    LyricShareConfig.defaultFont?.let { spec ->
                        runCatching { provider.defaultTypeface = provider.resolve(spec, null) }
                    }
                }
            }
            if (skinRepositoryRef == null) skinRepositoryRef = SkinRepository(app)
            if (rendererRef == null) rendererRef = LyricImageRenderer(requireNotNull(fontProviderRef))
        }
    }

    private fun requireApp(context: Context): Context {
        install(context)
        return appContext ?: context.applicationContext
    }

    /** 皮肤仓库（页面里列皮肤用）。 */
    fun skins(context: Context): List<LoadedSkin> {
        install(context)
        return skinRepositoryRef?.loadAll().orEmpty()
    }

    /** 安装下载/侧载的皮肤包。 */
    fun installSkin(context: Context, zipFile: java.io.File, expectedMd5: String? = null): Result<LoadedSkin> {
        install(context)
        return skinRepositoryRef?.installFromZip(zipFile, expectedMd5) ?: Result.failure(IllegalStateException("未初始化"))
    }

    /**
     * 卸载一个已导入的皮肤（内置皮肤删不掉，会返回 false）。
     * 页面上长按皮肤缩略图 → 确认删除时调用。
     */
    fun removeSkin(context: Context, skinId: String): Boolean {
        install(context)
        val repo = skinRepositoryRef ?: return false
        if (skinId !in repo.downloadedIds()) return false
        repo.remove(skinId)
        return true
    }

    fun fontProvider(context: Context): FontProvider {
        install(context)
        return requireNotNull(fontProviderRef)
    }

    fun renderer(context: Context): LyricImageRenderer {
        install(context)
        return requireNotNull(rendererRef)
    }

    // ------------------------------------------------------------ 拉起页面

    /** 打开歌词图片生成页。 */
    @JvmStatic
    fun launch(activity: Activity, request: LyricShareRequest) {
        install(activity)
        activity.startActivity(intentFor(activity, request))
    }

    /** 需要自定义转场/forResult 时自行取 Intent。 */
    fun intentFor(context: Context, request: LyricShareRequest): Intent =
        Intent(context, LyricShareImageActivity::class.java).also { request.writeTo(it) }

    // ------------------------------------------------------------ 无界面出图

    /**
     * 直接渲染出一张图（不打开页面）。
     * 全部工作在 [Dispatchers.Default]，可直接在协程里调用。
     */
    suspend fun render(
        context: Context,
        request: LyricShareRequest,
        skinId: String? = null,
        widthPx: Int? = null,
        cover: Bitmap? = null,
        fontOverride: FontSpec? = null,
        showQrCode: Boolean = true,
        /**
         * 元素序号 → 用户调整（换图 / 形状 / 大小 / 位移 / 锚点 / 颜色 / 底色 / 显隐）。
         * 序号 = 皮肤 `elements` 的下标，语义与页面上的编辑面板一致。
         */
        elementEdits: Map<Int, EditState> = emptyMap(),
        /** 画布背景的用户调整（换图 / 纯色 / 缩放 / 平移 / 显隐）。 */
        backgroundEdit: EditState = EditState(),
        /** 文本默认样式的用户调整（字体 / 颜色）。 */
        textEdit: EditState = EditState(),
    ): Bitmap = withContext(Dispatchers.Default) {
        install(context)
        val repository = requireNotNull(skinRepositoryRef)
        val skin = repository.find(skinId ?: request.defaultSkinId)
            ?: repository.loadAll().firstOrNull()
            ?: error("没有任何可用皮肤")
        // 封面源图：序号最小的封面位元素若被换过图，就用它作为"当前封面"
        val coverSlotIndex = skin.spec.elements.indexOfFirst { it.isCoverSlot }
        val coverOverride = if (coverSlotIndex >= 0) elementEdits[coverSlotIndex]?.bitmap else null
        val coverBitmap = resolveCover(context, request, coverOverride ?: cover)
        renderer(context).render(
            skin = skin,
            data = LyricShareData.from(request, coverBitmap),
            options = RenderOptions(
                widthPx = widthPx ?: request.outputWidthPx,
                // 兼容旧参数：等价于 textEdit.font
                textEdit = if (fontOverride != null) textEdit.copy(font = fontOverride) else textEdit,
                showQrCode = showQrCode && !request.resolvedQrContent().isNullOrBlank(),
                showBrandElement = false,           // "来自 XXX" 默认关闭：不画也不占位
                elementEdits = elementEdits,
                backgroundEdit = backgroundEdit,
            ),
        )
    }

    /** 解码封面（带采样 + EXIF 方向矫正）。 */
    suspend fun loadBitmap(context: Context, uri: Uri, maxSize: Int = 2048): Bitmap? =
        withContext(Dispatchers.IO) { BitmapUtils.decodeUri(context, uri, maxSize) }

    /**
     * 统一解析封面：显式传入的 Bitmap > content/file Uri > http(s) URL（交给宿主钩子）。
     * 页面和无界面出图都走这一条路，保证行为一致。
     */
    suspend fun resolveCover(
        context: Context,
        request: LyricShareRequest,
        explicit: Bitmap? = null,
    ): Bitmap? {
        if (explicit != null) return explicit
        request.coverUri?.let { uri -> return loadBitmap(context, uri) }
        val url = request.coverUrl ?: return null
        return LyricShareConfig.coverLoader?.invoke(context, url)
    }

    // ------------------------------------------------------------ 保存 / 分享

    fun saveToGallery(
        context: Context,
        bitmap: Bitmap,
        fileName: String = ImageExporter.timestampName(LyricShareConfig.filePrefix),
    ): Result<Uri> = ImageExporter.saveToGallery(context, bitmap, fileName, LyricShareConfig.galleryAlbumName)

    fun shareViaSystem(
        context: Context,
        bitmap: Bitmap,
        request: LyricShareRequest? = null,
        fileName: String = ImageExporter.timestampName(LyricShareConfig.filePrefix),
    ): Result<Unit> = ImageExporter.shareViaSystem(
        context = context,
        bitmap = bitmap,
        fileName = fileName,
        text = request?.let { LyricShareConfig.shareTextProvider?.invoke(it) },
        chooserTitle = LyricShareConfig.chooserTitle,
    )
}
