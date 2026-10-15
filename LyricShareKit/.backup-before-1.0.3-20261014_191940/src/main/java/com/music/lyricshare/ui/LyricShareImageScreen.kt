package com.music.lyricshare.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.lyricshare.LegacyStoragePermission
import com.music.lyricshare.LyricShareData
import com.music.lyricshare.LyricShareKit
import com.music.lyricshare.LyricShareRequest
import com.music.lyricshare.render.ImageAdjust
import com.music.lyricshare.render.RenderOptions
import com.music.lyricshare.skin.FontSource
import com.music.lyricshare.skin.FontSpec
import com.music.lyricshare.skin.ImageShape
import com.music.lyricshare.skin.LoadedSkin
import com.music.lyricshare.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 皮肤缩略图宽度。
 *
 * 注意：**预览没有独立分辨率** —— 预览和导出用同一个渲染宽度（皮肤参考宽度，默认 1080），
 * 界面只负责把它缩放显示。好处有三个：
 *   1. 预览是清晰的（不再是"渲染 720 再拉大"的糊图）；
 *   2. 和出图逐像素同源，样式零偏差；
 *   3. 预览位图可以直接复用于导出，点「保存/分享」是瞬时的。
 */
private const val THUMB_WIDTH = 220

/** 可编辑的对象（皮肤声明了哪一项，界面上就出现哪一项）。 */
private enum class EditTarget { COVER, BACKGROUND }

/** 操作行里的一项：目标 + 显示名 + 图标。 */
private data class EditEntry(
    val target: EditTarget,
    val label: String,
    val icon: ImageVector,
)

/**
 * 与 [runCatching] 的唯一区别：**不吞协程取消**。
 *
 * 为什么必须有它：Compose 在 `LaunchedEffect` 的 key 变化、或页面离开组合时，
 * 会用 `CancellationException`（消息就是 "The coroutine scope left the composition"）
 * 取消正在跑的协程 —— 这是**正常控制流**。而 `runCatching` 捕获的是 `Throwable`，
 * 会把取消也当成"失败"，于是"换个皮肤/换张图/退出页面"都会误弹「生成失败」。
 *
 * 所以凡是包着挂起调用的地方，一律用这个函数而不是 `runCatching`。
 */
private suspend fun <T> runCatchingNoCancel(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    Result.failure(throwable)
}

/**
 * ============================================================================
 *  歌词图片生成页
 * ============================================================================
 *  职责划分（改视觉时按这个结构找位置）：
 *    ScreenHeader      —— 顶部栏：返回 + 标题 + 分享
 *    PreviewArea       —— 中间：预览（按出图分辨率渲染、界面缩放显示）+ 右下角「换图」按钮
 *    ActionRow         —— 底部第一行：换封面 / 换背景 / 字体（都走系统选择器）
 *    SkinRow           —— 底部第二行：皮肤横向列表（缩略图由引擎实时渲染）
 *    QrToggleRow       —— 底部第三行：二维码开关
 *    PrimaryAction     —— 底部主按钮：生成并分享（弹出"存相册 / 系统分享"）
 *
 *  所有尺寸/颜色/字号都来自 [LyricShareTokens]，页面里没有魔法数字。
 */
@Composable
fun LyricShareImageScreen(
    request: LyricShareRequest,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalLyricShareTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 渲染依赖（进程级单例，install 幂等）
    val renderer = remember(context) { LyricShareKit.renderer(context) }
    val fontProvider = remember(context) { LyricShareKit.fontProvider(context) }
    val skins = remember(context) { LyricShareKit.skins(context) }

    // ---------------------------------------------------------------- 状态
    var selectedSkinId by remember { mutableStateOf(request.defaultSkinId ?: skins.firstOrNull()?.id) }
    var albumCover by remember { mutableStateOf<Bitmap?>(null) }   // 专辑封面（来自播放器）
    // 用户在二级面板里对封面/背景做的调整：换图、显隐、形状、大小、位置
    var coverAdjust by remember { mutableStateOf(ImageAdjust()) }
    var backgroundAdjust by remember { mutableStateOf(ImageAdjust()) }
    var fontOverride by remember { mutableStateOf<FontSpec?>(null) }   // 用户选的字体（优先级最高）
    var showQrCode by remember { mutableStateOf(!request.qrContent.isNullOrBlank()) }
    var editing by remember { mutableStateOf<EditTarget?>(null) }      // 当前打开哪个二级面板

    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var previewInputs by remember { mutableStateOf<List<Any?>>(emptyList()) } // preview 对应的输入快照
    var rendering by remember { mutableStateOf(true) }
    // 封面是否已经"有结论"（取到了 / 确认没有）。请求里没给封面时一开始就是 true。
    var coverResolved by remember { mutableStateOf(request.coverUri == null && request.coverUrl == null) }
    var busy by remember { mutableStateOf(false) }                 // 导出中（生成大图 + 存/分享）
    var skinPreviews by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showFontSheet by remember { mutableStateOf(false) }

    /** 用户换过图的封面优先；否则用播放器给的专辑图。 */
    val effectiveCover = coverAdjust.bitmap ?: albumCover
    val selectedSkin = remember(skins, selectedSkinId) {
        skins.firstOrNull { it.id == selectedSkinId } ?: skins.firstOrNull()
    }
    val data = remember(request, effectiveCover) { LyricShareData.from(request, effectiveCover) }

    // ---------------- 皮肤包声明：界面上出现哪些可编辑入口、面板里有哪些选项，全由它决定 ----------------
    val coverEditable = remember(selectedSkin) {
        selectedSkin?.spec?.elements?.firstOrNull { it.isCoverSlot }?.editable
    }
    val backgroundEditable = remember(selectedSkin) { selectedSkin?.spec?.canvas?.background?.editable }

    /** 皮肤自身给封面声明的形状，用于在形状选项里把"默认"显示成实际形状。 */
    val coverSkinShape = remember(selectedSkin) {
        val slot = selectedSkin?.spec?.elements?.firstOrNull { it.isCoverSlot }
        when {
            slot == null -> ImageShape.DEFAULT
            slot.circle -> ImageShape.CIRCLE
            slot.cornerRadius > 0 -> ImageShape.ROUNDED
            else -> ImageShape.RECT
        }
    }

    /**
     * 可声明式入口列表：皮肤声明了封面/背景的 editable 才会出现对应入口。
     * 皮肤列表（二级列表）一换，这里立刻跟着新皮肤的声明变。
     */
    val editEntries: List<EditEntry> = remember(coverEditable, backgroundEditable) {
        buildList {
            coverEditable?.let { add(EditEntry(EditTarget.COVER, it.label ?: "封面", Icons.Rounded.Image)) }
            backgroundEditable?.let { add(EditEntry(EditTarget.BACKGROUND, it.label ?: "背景", Icons.Rounded.Wallpaper)) }
        }
    }

    /**
     * 渲染宽度 = 出图宽度 = 请求指定 > 皮肤参考宽度。
     * 预览、导出、以及用户调完封面/背景后重渲染全都用它，所以三者永远对得上。
     */
    val exportWidth = request.outputWidthPx ?: selectedSkin?.spec?.canvas?.width ?: 1080

    /**
     * 影响出图的全部输入。预览渲染完成后连同结果一起记下来；
     * 导出时若输入没变，就直接复用预览位图 —— 因为预览就是这个分辨率、逐像素同源。
     * 用"输入快照比对"而不是"渲染中"标志，可以避免"状态刚改、重渲染还没开始"时误用旧图。
     */
    val renderInputs = listOf<Any?>(
        selectedSkin?.id, exportWidth, effectiveCover, coverAdjust, backgroundAdjust,
        fontOverride, showQrCode, coverResolved,
    )

    /**
     * 封面还在路上（请求给了封面地址，但解码/下载没结束）。这期间先不渲染：
     * 否则会白渲染一张没有封面的图，等封面到位再被取消 —— 既浪费一次渲染，
     * 又会让"取消"和"参数变化"交织在一起，徒增困惑。
     */
    val coverPending = !coverResolved && coverAdjust.bitmap == null

    // 构造渲染参数：集中一处，预览和导出保证用同一套开关
    fun optionsFor(width: Int) = RenderOptions(
        widthPx = width,
        fontOverride = fontOverride,
        showQrCode = showQrCode && !request.qrContent.isNullOrBlank(),
        showBrandElement = false, // 「来自 XXX」默认关闭：既不绘制，也不预留高度
        coverAdjust = coverAdjust,
        backgroundAdjust = backgroundAdjust,
    )

    // ---------------------------------------------------------------- 数据准备
    // 1) 解码播放器给的专辑封面（content Uri 直接解码；http 地址交给宿主注册的 coverLoader）
    LaunchedEffect(request.coverUri, request.coverUrl) {
        albumCover = LyricShareKit.resolveCover(context, request)
        coverResolved = true      // 无论取到没有都要落地，否则会一直停在"封面加载中"
    }

    // 2) 渲染：预览和出图共用同一个分辨率（= exportWidth），改任一参数就重渲染
    LaunchedEffect(selectedSkin?.id, exportWidth, effectiveCover, coverAdjust, backgroundAdjust, fontOverride, showQrCode, coverResolved) {
        val skin = selectedSkin ?: return@LaunchedEffect
        rendering = true
        if (coverPending) return@LaunchedEffect        // 等封面，先不渲染（rendering 保持 true 显示进度）
        val result = runCatchingNoCancel {
            withContext(Dispatchers.Default) { renderer.render(skin, data, optionsFor(exportWidth)) }
        }
        result.onSuccess {
            preview = it
            previewInputs = renderInputs
        }.onFailure { toast(context, "生成预览失败：${it.message ?: "未知错误"}") }
        rendering = false
    }

    // 3) 皮肤缩略图：用真实歌曲数据渲染（用户看到的是自己的歌），算一次缓存住
    LaunchedEffect(skins, data) {
        if (skins.isEmpty()) return@LaunchedEffect
        val thumbs = withContext(Dispatchers.Default) {
            skins.associate { skin ->
                // 单个皮肤渲染失败不影响其它皮肤；但协程取消必须抛出，否则会留下"半套缩略图"
                skin.id to runCatchingNoCancel { renderer.render(skin, data, optionsFor(THUMB_WIDTH)) }.getOrNull()
            }.filterValues { it != null }.mapValues { it.value!! }
        }
        skinPreviews = thumbs
    }

    // ---------------------------------------------------------------- 系统选择器
    // 封面/背景都走系统图片选择器（Android 13+ 是系统相册 Photo Picker，低版本自动回落到文档选择器）
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            LyricShareKit.loadBitmap(context, uri)?.let {
                coverAdjust = coverAdjust.copy(bitmap = it, visible = true)
            }
        }
    }
    val pickBackground = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            LyricShareKit.loadBitmap(context, uri)?.let {
                backgroundAdjust = backgroundAdjust.copy(bitmap = it, visible = true)
            }
        }
    }
    // 字体：系统文件选择器拿不到可靠的 font/* MIME（不同 ROM 报 application/octet-stream），
    // 所以放开类型、由 FontProvider 真正加载一次来校验，坏文件直接提示失败。
    val pickFontFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { fontProvider.importUserFont(uri) }
            result.onSuccess { spec ->
                fontOverride = spec
                toast(context, "已应用字体：${uri.lastPathSegment?.substringAfterLast('/') ?: "自定义字体"}")
            }.onFailure { toast(context, "字体文件无法解析，已回退默认字体") }
        }
    }

    /** 打开系统图片选择器（二级面板里的"换图"和入口都用它）。 */
    fun launchImagePicker(target: EditTarget) {
        val launcher = if (target == EditTarget.COVER) pickCover else pickBackground
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // ---------------------------------------------------------------- 导出动作
    /**
     * 生成出图用的位图。
     * 输入没变时直接复用预览结果（预览就是这个分辨率、逐像素同源），否则重新渲染一次。
     */
    suspend fun renderFullSize(): Bitmap? {
        val skin = selectedSkin ?: return null
        preview?.let { cached -> if (previewInputs == renderInputs) return cached }
        busy = true
        // try/finally：即使导出中途被取消（例如用户直接返回），也要把 busy 收回来，不留假进度
        val bitmap = try {
            runCatchingNoCancel {
                withContext(Dispatchers.Default) { renderer.render(skin, data, optionsFor(exportWidth)) }
            }.onFailure { toast(context, "生成失败：${it.message ?: "未知错误"}") }.getOrNull()
        } finally {
            busy = false
        }
        return bitmap
    }

    fun performSave() {
        scope.launch {
            val bitmap = renderFullSize() ?: return@launch
            LyricShareKit.saveToGallery(context, bitmap)
                .onSuccess { toast(context, "已保存到相册") }
                .onFailure { toast(context, "保存失败：${it.message ?: "未知错误"}") }
        }
    }

    // API 28 及以下存相册需要写外部存储权限：没有就先申请，授权回调里再继续保存
    val savePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) performSave() else toast(context, "没有存储权限，无法保存到相册")
    }

    fun onSaveClick() {
        if (!LegacyStoragePermission.granted(context)) {
            savePermission.launch(LegacyStoragePermission.PERMISSION)
        } else {
            performSave()
        }
    }

    fun onShareClick() {
        scope.launch {
            val bitmap = renderFullSize() ?: return@launch
            LyricShareKit.shareViaSystem(context, bitmap, request)
                .onFailure { toast(context, "没有可用的分享目标") }
        }
    }

    // ---------------------------------------------------------------- 布局
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.surface)
    ) {
        ScreenHeader(
            title = "歌词图片",
            onBack = onBack,
            onShare = { showShareSheet = true },
        )

        PreviewArea(
            preview = preview,
            rendering = rendering,
            busy = busy,
            emptyHint = if (request.lyricLines.isEmpty()) "没有可用的歌词内容" else null,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )

        BottomPanel {
            // ① 可声明式操作行：
            //    「封面」「背景」只在皮肤声明了 editable 时才出现（皮肤列表一换就跟着变），
            //    点开是二级面板（换图 / 显示 / 形状 / 大小 / 位置 / 恢复默认），选项同样由声明裁剪。
            //    「字体」不属于皮肤声明，是固定项。
            EditChipRow(
                entries = editEntries,
                onEdit = { editing = it },
                onPickFont = { showFontSheet = true },
            )

            Spacer(Modifier.height(tokens.spacingMd))

            // ② 皮肤
            SectionLabel("皮肤")
            Spacer(Modifier.height(tokens.spacingSm))
            SkinRow(
                skins = skins,
                previews = skinPreviews,
                selectedId = selectedSkin?.id,
                onSelect = { selectedSkinId = it },
            )

            // ③ 二维码（只有请求里带了内容才显示这一行）
            if (!request.qrContent.isNullOrBlank()) {
                Spacer(Modifier.height(tokens.spacingMd))
                QrToggleRow(checked = showQrCode, onCheckedChange = { showQrCode = it })
            }

            Spacer(Modifier.height(tokens.spacingMd))

            // ④ 主按钮
            Button(
                onClick = { showShareSheet = true },
                enabled = !busy && !rendering && preview != null,
                shape = RoundedCornerShape(tokens.radiusPill),
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.primary,
                    contentColor = tokens.onPrimary,
                ),
                contentPadding = PaddingValues(vertical = tokens.spacingMd),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(tokens.spacingSm))
                Text("生成并分享", fontWeight = FontWeight.Medium)
            }
        }
    }

    // ---------------------------------------------------------------- 弹层
    if (showShareSheet) {
        ShareActionSheet(
            onDismiss = { showShareSheet = false },
            onSave = {
                showShareSheet = false
                onSaveClick()
            },
            onShare = {
                showShareSheet = false
                onShareClick()
            },
        )
    }

    if (showFontSheet) {
        FontPickerSheet(
            bundledFonts = remember { fontProvider.bundledFonts() },
            userFonts = remember { fontProvider.userFonts().map { it.name } },
            selected = fontOverride,
            onDismiss = { showFontSheet = false },
            onUseDefault = {
                fontOverride = null
                showFontSheet = false
            },
            onUseBundled = { path ->
                fontOverride = FontSpec(FontSource.ASSETS, path)
                showFontSheet = false
            },
            onUseUserFont = { name ->
                val file = fontProvider.userFonts().firstOrNull { it.name == name }
                if (file != null) fontOverride = FontSpec(FontSource.FILE, file.absolutePath)
                showFontSheet = false
            },
            onPickFromFile = {
                showFontSheet = false
                pickFontFile.launch(arrayOf("*/*"))
            },
        )
    }

    // 「封面 / 背景」二级面板：内容按皮肤声明裁剪
    val editTarget = editing
    if (editTarget != null) {
        val spec = if (editTarget == EditTarget.COVER) coverEditable else backgroundEditable
        // 皮肤没声明（例如面板开着时切了皮肤）→ 本轮不渲染；用户再点一次入口即可，不在组合期改状态
        if (spec != null) {
            ImageAdjustSheet(
                title = spec.label ?: if (editTarget == EditTarget.COVER) "封面" else "背景",
                spec = spec,
                adjust = if (editTarget == EditTarget.COVER) coverAdjust else backgroundAdjust,
                isBackground = editTarget == EditTarget.BACKGROUND,
                hasCustomImage = (if (editTarget == EditTarget.COVER) coverAdjust else backgroundAdjust).bitmap != null,
                skinShape = coverSkinShape,
                onDismiss = { editing = null },
                onPickImage = { launchImagePicker(editTarget) },
                onChange = { updated ->
                    if (editTarget == EditTarget.COVER) coverAdjust = updated else backgroundAdjust = updated
                },
            )
        }
    }
}

// ============================================================================
//  以下是页面内的独立视觉单元，每个都只干一件事，方便单独调整风格
// ============================================================================

/** 顶部栏：返回 + 标题 + 分享入口。高度与 Melodia 的 FullScreenShareHeader 对齐（56dp）。 */
@Composable
private fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    onShare: () -> Unit,
) {
    val tokens = LocalLyricShareTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(tokens.headerHeight)
            .padding(start = tokens.spacingXs, end = tokens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = tokens.onSurface,
            )
        }
        Spacer(Modifier.width(tokens.spacingSm))
        Text(
            text = title,
            style = LyricShareTextStyles.title(tokens),
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onShare) {
            Text("分享", color = tokens.onSurface, fontSize = tokens.textBody)
        }
    }
}

/**
 * 中间预览区。
 *
 * 三个要点：
 *  1. 预览位图就是**出图分辨率**（皮肤参考宽度，默认 1080），这里只做缩放显示，
 *     所以画面清晰、和最终出图完全一致；`FilterQuality.High` 保证缩小时不糊。
 *  2. 用 [BoxWithConstraints] 手算贴合尺寸（而不是 fillMaxWidth + ContentScale.Fit），
 *     这样圆角和描边是**紧贴海报边缘**的，长图也不会溢出可用区域。
 *  3. 不负责任何编辑入口 —— 封面/背景的入口由底部操作行按**皮肤声明**动态生成，
 *     这里保持"干净的海报"，不遮挡画面。
 */
@Composable
private fun PreviewArea(
    preview: Bitmap?,
    rendering: Boolean,
    busy: Boolean,
    emptyHint: String?,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalLyricShareTokens.current
    BoxWithConstraints(
        modifier = modifier.padding(horizontal = tokens.spacingLg, vertical = tokens.spacingSm),
        contentAlignment = Alignment.Center,
    ) {
        when {
            emptyHint != null -> Text(emptyHint, style = LyricShareTextStyles.label(tokens))

            preview != null -> {
                val ratio = preview.width.toFloat() / preview.height.toFloat()
                // 先按宽度铺满，再按高度收敛：取两者中更小的那个方向，保证完整显示
                val width = minOf(maxWidth, maxHeight * ratio)
                val height = width / ratio

                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = "生成效果预览",
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.High,   // 高清缩略：1080 渲染 → 界面缩放
                    modifier = Modifier
                        .size(width, height)
                        .clip(RoundedCornerShape(tokens.radiusMd))
                        .border(1.dp, tokens.outline, RoundedCornerShape(tokens.radiusMd)),
                )
            }

            else -> Text("正在准备预览…", style = LyricShareTextStyles.label(tokens))
        }

        // 渲染/导出进度：半透明遮罩 + 环形进度，避免误触
        if (rendering || busy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = tokens.onSurface, strokeWidth = 2.dp)
            }
        }
    }
}

/** 底部操作面板容器：只圆上面两个角，和 Melodia 的 BottomSheetShape 观感一致。 */
@Composable
private fun BottomPanel(content: @Composable () -> Unit) {
    val tokens = LocalLyricShareTokens.current
    Surface(
        color = tokens.surfaceContainer,
        shape = RoundedCornerShape(topStart = tokens.sheetRadius, topEnd = tokens.sheetRadius),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(
                    start = tokens.spacingLg,
                    end = tokens.spacingLg,
                    top = tokens.spacingMd,
                    bottom = tokens.spacingLg,
                ),
        ) {
            content()
        }
    }
}

/**
 * 可声明式操作行。
 *
 * [entries] 来自当前皮肤的 editable 声明：皮肤声明了封面 → 出现「封面」，声明了背景 → 出现「背景」，
 * 一个都没声明就只剩「字体」。所以这一行是**皮肤驱动的**，换皮肤会立刻变化。
 * 条目多了可以横向滚动，不会挤爆。
 */
@Composable
private fun EditChipRow(
    entries: List<EditEntry>,
    onEdit: (EditTarget) -> Unit,
    onPickFont: () -> Unit,
) {
    val tokens = LocalLyricShareTokens.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(tokens.spacingSm),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        entries.forEach { entry ->
            PillButton(
                text = entry.label,
                icon = entry.icon,
                onClick = { onEdit(entry.target) },
            )
        }
        PillButton("字体", Icons.Rounded.FontDownload, onPickFont)
    }
}

@Composable
private fun PillButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalLyricShareTokens.current
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(tokens.radiusPill),
        border = BorderStroke(1.dp, tokens.outline),
        contentPadding = PaddingValues(horizontal = tokens.spacingSm, vertical = 0.dp),
        modifier = modifier.height(36.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tokens.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(tokens.spacingXs))
        Text(text, color = tokens.onSurface, fontSize = tokens.textBody, maxLines = 1)
    }
}

/** 分组小标题。 */
@Composable
private fun SectionLabel(text: String) {
    val tokens = LocalLyricShareTokens.current
    Text(text, style = LyricShareTextStyles.label(tokens))
}

/** 皮肤横向列表。缩略图由渲染引擎实时生成，所以新增皮肤不用额外准备预览图。 */
@Composable
private fun SkinRow(
    skins: List<LoadedSkin>,
    previews: Map<String, Bitmap>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    val tokens = LocalLyricShareTokens.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(tokens.spacingSm)) {
        items(skins, key = { it.id }) { skin ->
            val selected = skin.id == selectedId
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(72.dp)
                    .clip(RoundedCornerShape(tokens.radiusSm))
                    .clickable { onSelect(skin.id) },
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 72.dp, height = 96.dp)   // 3:4，与常见海报比例一致
                        .clip(RoundedCornerShape(tokens.radiusSm))
                        .background(tokens.surfaceContainerHigh)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) tokens.primary else tokens.outline,
                            shape = RoundedCornerShape(tokens.radiusSm),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val thumb = previews[skin.id]
                    if (thumb != null) {
                        Image(
                            bitmap = thumb.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        CircularProgressIndicator(
                            color = tokens.onSurfaceVariant,
                            strokeWidth = 1.5.dp,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.height(tokens.spacingXxs))
                Text(
                    text = skin.name,
                    color = if (selected) tokens.onSurface else tokens.onSurfaceVariant,
                    fontSize = tokens.textLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 二维码开关：关掉后引擎不会绘制二维码，也不会为它保留任何高度。 */
@Composable
private fun QrToggleRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val tokens = LocalLyricShareTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.QrCode2, contentDescription = null, tint = tokens.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(tokens.spacingSm))
        Text("显示二维码", color = tokens.onSurface, fontSize = tokens.textBody)
        Spacer(Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
