package com.music.lyricshare

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri

/**
 * ============================================================================
 *  一次「歌词图片」请求：宿主播放器只需要把这些字段交给 LyricShareKit
 * ============================================================================
 *  Melodia 里已经有多选歌词的状态（FullScreenLyricsView 的 selectedLyricKeys），
 *  选中行取出来转成 [lyricLines] 即可，不需要改你现有的选择/复制逻辑。
 *
 *  [brandText] 默认 null —— 也就是默认**不画**"来自 XXX"，且不会预留任何空间。
 *  皮肤里如果写了 role:"brand" 的元素，也会被渲染器的 showBrandElement=false 过滤掉。
 */
data class LyricShareRequest(
    val title: String,
    val artists: String,
    val album: String? = null,
    /** 选中的歌词行（已按显示顺序排好）。 */
    val lyricLines: List<String>,
    /** 专辑封面；宿主有 Bitmap 时也可以走 LyricShareKit.render(...) 直接传。 */
    val coverUri: Uri? = null,
    /**
     * 专辑封面的 http(s) 地址。
     * 本模块不内置图片加载库（避免和宿主的 Coil/Glide 冲突），
     * 这类地址交给宿主注册的 [LyricShareConfig.coverLoader] 去取（见 README 接入说明）。
     */
    val coverUrl: String? = null,
    /** 二维码内容。null = 用 [LyricShareConfig.defaultQrContent] 按 [songId] 自动生成。 */
    val qrContent: String? = null,
    /** 歌曲 id：二维码默认内容的来源（`https://music.163.com/song?id=<id>`）。 */
    val songId: Long? = null,
    /**
     * 播放页当前的背景色（宿主从封面取色的结果）。
     * 用途有二：① 页面底色跟随播放页；② 背景选「纯色」时的默认色。
     */
    val baseColor: Int? = null,
    /** 打开页面时默认选中的皮肤 id；null = 用第一个。 */
    val defaultSkinId: String? = null,
    /** 导出宽度（像素）。null = 用皮肤定义的参考宽度（默认 1080）。 */
    val outputWidthPx: Int? = null,
    /** 底部来源文案，默认 null（不显示、不占位）。 */
    val brandText: String? = null,
) {

    fun writeTo(intent: Intent): Intent = intent.apply {
        putExtra(EXTRA_TITLE, title)
        putExtra(EXTRA_ARTISTS, artists)
        putExtra(EXTRA_ALBUM, album)
        putStringArrayListExtra(EXTRA_LYRICS, ArrayList(lyricLines))
        putExtra(EXTRA_COVER_URI, coverUri?.toString())
        putExtra(EXTRA_COVER_URL, coverUrl)
        putExtra(EXTRA_QR_CONTENT, qrContent)
        putExtra(EXTRA_SONG_ID, songId ?: 0L)
        putExtra(EXTRA_BASE_COLOR, baseColor ?: 0)
        putExtra(EXTRA_SKIN_ID, defaultSkinId)
        putExtra(EXTRA_BRAND_TEXT, brandText)
        putExtra(EXTRA_OUTPUT_WIDTH, outputWidthPx ?: 0)
    }

    /**
     * 最终用于生成二维码的内容：
     * 显式给了 [qrContent] 就用它；否则用 [LyricShareConfig.defaultQrContent] 按 [songId] 生成
     * （默认是 `https://music.163.com/song?id=<id>`）。两者都没有 → null，不出二维码。
     */
    fun resolvedQrContent(): String? =
        qrContent?.takeIf { it.isNotBlank() }
            ?: songId?.takeIf { it > 0 }?.let { LyricShareConfig.defaultQrContent?.invoke(it) }

    companion object {
        const val EXTRA_TITLE = "lyric_share.title"
        const val EXTRA_ARTISTS = "lyric_share.artists"
        const val EXTRA_ALBUM = "lyric_share.album"
        const val EXTRA_LYRICS = "lyric_share.lyrics"
        const val EXTRA_COVER_URI = "lyric_share.cover_uri"
        const val EXTRA_COVER_URL = "lyric_share.cover_url"
        const val EXTRA_QR_CONTENT = "lyric_share.qr_content"
        const val EXTRA_SONG_ID = "lyric_share.song_id"
        const val EXTRA_BASE_COLOR = "lyric_share.base_color"
        const val EXTRA_SKIN_ID = "lyric_share.skin_id"
        const val EXTRA_BRAND_TEXT = "lyric_share.brand_text"
        const val EXTRA_OUTPUT_WIDTH = "lyric_share.output_width"

        fun from(intent: Intent): LyricShareRequest = LyricShareRequest(
            title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            artists = intent.getStringExtra(EXTRA_ARTISTS).orEmpty(),
            album = intent.getStringExtra(EXTRA_ALBUM),
            lyricLines = intent.getStringArrayListExtra(EXTRA_LYRICS).orEmpty(),
            coverUri = intent.getStringExtra(EXTRA_COVER_URI)?.let { runCatching { Uri.parse(it) }.getOrNull() },
            coverUrl = intent.getStringExtra(EXTRA_COVER_URL),
            qrContent = intent.getStringExtra(EXTRA_QR_CONTENT),
            songId = intent.getLongExtra(EXTRA_SONG_ID, 0L).takeIf { it > 0 },
            baseColor = intent.getIntExtra(EXTRA_BASE_COLOR, 0).takeIf { it != 0 },
            defaultSkinId = intent.getStringExtra(EXTRA_SKIN_ID),
            outputWidthPx = intent.getIntExtra(EXTRA_OUTPUT_WIDTH, 0).takeIf { it > 0 },
            brandText = intent.getStringExtra(EXTRA_BRAND_TEXT),
        )
    }
}

/**
 * 渲染引擎的输入：请求 + 运行时素材。
 * 与 [LyricShareRequest] 分开，是因为「封面」在界面上可能被用户换成别的图，
 * 「背景」也可能被用户从系统相册选一张，这些都不属于原始请求。
 */
data class LyricShareData(
    val title: String,
    val artists: String,
    val album: String? = null,
    val lyricLines: List<String> = emptyList(),
    val cover: Bitmap? = null,
    val qrContent: String? = null,
    val brandText: String? = null,
    /**
     * 数据构造时固定下来的时间戳：文字里的 `YYYY/MM/dd/HH/hh/mm/ss` 等参数由它生成，
     * 保证预览与导出、以及同一次出图里的多处时间参数完全一致。
     */
    val timestampMillis: Long = System.currentTimeMillis(),
) {
    companion object {
        fun from(request: LyricShareRequest, cover: Bitmap? = null) = LyricShareData(
            title = request.title,
            artists = request.artists,
            album = request.album,
            lyricLines = request.lyricLines.filter { it.isNotBlank() },
            cover = cover,
            qrContent = request.resolvedQrContent(),
            brandText = request.brandText,
        )
    }
}
