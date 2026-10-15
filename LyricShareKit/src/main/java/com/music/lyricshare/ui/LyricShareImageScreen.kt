package com.music.lyricshare.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateMapOf
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
import com.music.lyricshare.render.EditState
import com.music.lyricshare.render.QrPlacement
import com.music.lyricshare.render.RenderOptions
import com.music.lyricshare.skin.EditableOwner
import com.music.lyricshare.skin.ElementType
import com.music.lyricshare.skin.FontSource
import com.music.lyricshare.skin.FontSpec
import com.music.lyricshare.skin.ImageShape
import com.music.lyricshare.skin.LoadedSkin
import com.music.lyricshare.skin.SkinEditable
import com.music.lyricshare.skin.editables
import com.music.lyricshare.skin.SkinSource
import com.music.lyricshare.toast
import java.io.File
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
/**
 * 一个皮肤上的全部用户调整，**每个皮肤各存一份**。
 * 换皮肤 = 换一份设置；换回来时原样恢复。
 */
private data class SkinAdjustments(
    val cover: EditState = EditState(),
    val background: EditState = EditState(),
    val font: FontSpec? = null,
    /** 字体颜色覆盖；null = 用皮肤声明的配色。 */
    val fontColor: Int? = null,
    /** 是否显示二维码。 */
    val qrVisible: Boolean = true,
    /** 二维码相关的用户调整（皮肤声明了 editable 才会出现入口）。 */
    val qr: QrAdjust = QrAdjust(),
)

/** 二维码的用户调整；null 值一律表示"用皮肤声明的"。 */
private data class QrAdjust(
    val visible: Boolean = true,
    val placement: QrPlacement = QrPlacement.SKIN,
    /** 底色；android.graphics.Color.TRANSPARENT = 透明底。 */
    val background: Int? = null,
    /** 码点前景色。 */
    val color: Int? = null,
    /** 大小倍数。 */
    val scale: Float = 1f,
)

/** 入口图标：按"这一项挂在什么承载物上"给（纯 UI 装饰，皮肤只需给 label）。 */
private fun iconFor(entry: SkinEditable): ImageVector = when {
    entry.owner is EditableOwner.TextDefaults -> Icons.Rounded.FontDownload
    entry.owner is EditableOwner.Background -> Icons.Rounded.Wallpaper
    entry.owner is EditableOwner.Element && entry.owner.element.type == ElementType.QRCODE -> Icons.Rounded.QrCode2
    else -> Icons.Rounded.Image
}

/** 操作行里的一项：可编辑项 id + 显示名 + 图标。 */
private data class EditEntry(
    /** 对应皮肤声明的可编辑项 id。 */
    val id: String,
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
    // 可变：导入皮肤后要立刻刷新列表
    var skins by remember(context) { mutableStateOf(LyricShareKit.skins(context)) }

    // ---------------------------------------------------------------- 状态
    var selectedSkinId by remember { mutableStateOf(request.defaultSkinId ?: skins.firstOrNull()?.id) }
    var albumCover by remember { mutableStateOf<Bitmap?>(null) }   // 专辑封面（来自播放器）
    /**
     * 用户调整**按皮肤各存一份**，皮肤内部再**按可编辑项的 id 各存一条**。
     * 换皮肤再换回来、或皮肤新增了声明的项，都不会互相污染。
     */
    val adjustmentsBySkin = remember { mutableStateMapOf<String, Map<String, EditState>>() }
    /** 二维码内容：显式传入优先，否则按 songId 自动生成（默认是这首歌的网页链接）。 */
    val qrContent = remember(request) { request.resolvedQrContent() }
    var activeEditId by remember { mutableStateOf<String?>(null) }      // 当前打开哪个可编辑项的面板
    var imageTargetId by remember { mutableStateOf<String?>(null) }     // 「从相册选择」是在给哪一项换图
    var fontTargetId by remember { mutableStateOf<String?>(null) }      // 「从文件选择字体」是在给哪一项换字体

    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var previewInputs by remember { mutableStateOf<List<Any?>>(emptyList()) } // preview 对应的输入快照
    var rendering by remember { mutableStateOf(true) }
    // 封面是否已经"有结论"（取到了 / 确认没有）。请求里没给封面时一开始就是 true。
    var coverResolved by remember { mutableStateOf(request.coverUri == null && request.coverUrl == null) }
    var busy by remember { mutableStateOf(false) }                 // 导出中（生成大图 + 存/分享）
    var skinPreviews by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var showShareSheet by remember { mutableStateOf(false) }
    var markedSkinId by remember { mutableStateOf<String?>(null) }   // 长按后显示删除角标的皮肤
    var deleteTarget by remember { mutableStateOf<LoadedSkin?>(null) } // 待确认删除的皮肤

    val selectedSkin = remember(skins, selectedSkinId) {
        skins.firstOrNull { it.id == selectedSkinId } ?: skins.firstOrNull()
    }

    // 当前皮肤的那一份设置：id -> 用户调整
    val skinKey = selectedSkin?.id.orEmpty()
    val edits: Map<String, EditState> = adjustmentsBySkin[skinKey].orEmpty()
    fun editStateFor(id: String): EditState = edits[id] ?: EditState()
    fun updateEdit(id: String, next: EditState) {
        adjustmentsBySkin[skinKey] = edits + (id to next)
    }

    /**
     * **皮肤声明的全部可编辑项**：入口有哪些、叫什么、什么顺序、面板里有什么，全看它。
     * 模块里不再有"封面/背景/字体/二维码"这种硬编码清单。
     */
    val editables: List<SkinEditable> = remember(selectedSkin) { selectedSkin?.spec?.editables.orEmpty() }

    // ---------------- 引擎参数 ----------------
    // 皮肤声明的项按「承载物」落到引擎的三个通用入口：
    //   元素序号 → 该项自己的 EditState（引擎按属性通用应用）
    //   EditableOwner.Background → 画布背景
    //   EditableOwner.TextDefaults → 文本默认样式（字体/颜色）
    val elementEdits: Map<Int, EditState> = editables.mapNotNull { entry ->
        (entry.owner as? EditableOwner.Element)?.let { it.index to editStateFor(entry.id) }
    }.toMap()
    val backgroundEdit: EditState = editables.firstOrNull { it.owner == EditableOwner.Background }
        ?.let { editStateFor(it.id) } ?: EditState()
    val textEdit: EditState = editables.firstOrNull { it.owner == EditableOwner.TextDefaults }
        ?.let { editStateFor(it.id) } ?: EditState()
    /** 封面那一项（用于给引擎准备专辑图 + 等图时先不渲染）。 */
    val coverEdit: EditState = editables
        .firstOrNull { it.owner is EditableOwner.Element && it.owner.element.isCoverSlot }
        ?.let { editStateFor(it.id) } ?: EditState()
    /** 二维码那一项（决定"要不要出二维码"）。 */
    val qrEdit: EditState = editables
        .firstOrNull { it.owner is EditableOwner.Element && it.owner.element.type == ElementType.QRCODE }
        ?.let { editStateFor(it.id) } ?: EditState()

    val showQrCode = qrEdit.visible

    /** 用户换过图的封面优先；否则用播放器给的专辑图。 */
    val effectiveCover = coverEdit.bitmap ?: albumCover
    val data = remember(request, effectiveCover) { LyricShareData.from(request, effectiveCover) }

    /** 入口列表：一项一个 chip，图标按承载物类型给（纯 UI 装饰，皮肤不用关心）。 */
    val editEntries: List<EditEntry> = remember(editables, qrContent) {
        editables.mapNotNull { entry ->
            // 二维码没内容时不给入口（画不出来，调了也没意义）
            val isQr = entry.owner is EditableOwner.Element && entry.owner.element.type == ElementType.QRCODE
            if (isQr && qrContent.isNullOrBlank()) return@mapNotNull null
            EditEntry(entry.id, entry.label, iconFor(entry))
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
        selectedSkin?.id, exportWidth, effectiveCover, elementEdits, backgroundEdit,
        textEdit, showQrCode, coverResolved,
    )

    /**
     * 封面还在路上（请求给了封面地址，但解码/下载没结束）。这期间先不渲染：
     * 否则会白渲染一张没有封面的图，等封面到位再被取消 —— 既浪费一次渲染，
     * 又会让"取消"和"参数变化"交织在一起，徒增困惑。
     */
    val coverPending = !coverResolved && coverEdit.bitmap == null

    // 构造渲染参数：集中一处，预览和导出保证用同一套开关
    fun optionsFor(width: Int) = RenderOptions(
        widthPx = width,
        fontOverride = textEdit.font,
        showQrCode = showQrCode && !qrContent.isNullOrBlank(),
        showBrandElement = false, // 「来自 XXX」默认关闭：既不绘制，也不预留高度
        elementEdits = elementEdits,
        backgroundEdit = backgroundEdit,
        textEdit = textEdit,
    )

    // ---------------------------------------------------------------- 数据准备
    // 1) 解码播放器给的专辑封面（content Uri 直接解码；http 地址交给宿主注册的 coverLoader）
    LaunchedEffect(request.coverUri, request.coverUrl) {
        albumCover = LyricShareKit.resolveCover(context, request)
        coverResolved = true      // 无论取到没有都要落地，否则会一直停在"封面加载中"
    }

    // 2) 渲染：预览和出图共用同一个分辨率（= exportWidth），改任一参数就重渲染
    LaunchedEffect(
        selectedSkin?.id, exportWidth, effectiveCover, elementEdits, backgroundEdit,
        textEdit, showQrCode, coverResolved,
    ) {
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
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val id = imageTargetId
        scope.launch {
            LyricShareKit.loadBitmap(context, uri)?.let { picked ->
                if (id != null) {
                    updateEdit(id, editStateFor(id).copy(bitmap = picked, visible = true))
                }
            }
        }
    }
    // 皮肤导入：系统文件选择器选 zip → 拷到缓存目录 → installSkin 校验并安装 → 刷新列表
    // （MIME 各 ROM 报得不一样，放开类型，真正的合法性由 skin.json 解析来判定）
    val pickSkinZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatchingNoCancel {
                    val tmp = File(context.cacheDir, "lyric_skin_import_${System.currentTimeMillis()}.zip")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    } ?: error("无法读取所选文件")
                    LyricShareKit.installSkin(context, tmp).getOrThrow().also { runCatching { tmp.delete() } }
                }
            }
            result.onSuccess { installed ->
                skins = LyricShareKit.skins(context)      // 刷新列表
                selectedSkinId = installed.id             // 直接切到新皮肤
                toast(context, "已导入皮肤：${installed.name}")
            }.onFailure { toast(context, "导入失败：${it.message ?: "不是有效的皮肤包"}") }
        }
    }

    // 字体：系统文件选择器拿不到可靠的 font/* MIME（不同 ROM 报 application/octet-stream），
    // 所以放开类型、由 FontProvider 真正加载一次来校验，坏文件直接提示失败。
    val pickFontFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) { fontProvider.importUserFont(uri) }
            result.onSuccess { spec ->
                fontTargetId?.let { id -> updateEdit(id, editStateFor(id).copy(font = spec)) }
                toast(context, "已应用字体：${uri.lastPathSegment?.substringAfterLast('/') ?: "自定义字体"}")
            }.onFailure { toast(context, "字体文件无法解析，已回退默认字体") }
        }
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
            EditChipRow(entries = editEntries, onEdit = { activeEditId = it })

            Spacer(Modifier.height(tokens.spacingMd))

            // ② 皮肤
            SectionLabel("皮肤")
            Spacer(Modifier.height(tokens.spacingSm))
            SkinRow(
                skins = skins,
                previews = skinPreviews,
                selectedId = selectedSkin?.id,
                onSelect = { skinId ->
                    markedSkinId = null                 // 点任意皮肤 = 收起删除角标
                    selectedSkinId = skinId
                },
                onImport = { pickSkinZip.launch(arrayOf("*/*")) },
                markedId = markedSkinId,
                onLongPress = { skin ->
                    // 内置皮肤在 assets 里删不掉，只对导入的皮肤给删除入口
                    if (skin.source == SkinSource.DOWNLOADED) {
                        markedSkinId = skin.id
                    } else {
                        toast(context, "内置皮肤不可删除，导入的皮肤才可以")
                    }
                },
                onDelete = { skin -> deleteTarget = skin },
            )

            // 分享入口只在顶部栏；二维码开关已挪到上面那行（与封面/背景/字体同排）
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


    // 删除皮肤确认框（长按 → 角标 → 这里确认）
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除皮肤", style = LyricShareTextStyles.title(tokens)) },
            text = {
                Text(
                    "确定删除「${target.name}」吗？删除后可以重新导入。",
                    color = tokens.onSurfaceVariant,
                    fontSize = tokens.textBody,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (LyricShareKit.removeSkin(context, target.id)) {
                        skins = LyricShareKit.skins(context)
                        if (selectedSkinId == target.id) selectedSkinId = skins.firstOrNull()?.id
                        toast(context, "已删除皮肤：${target.name}")
                    } else {
                        toast(context, "内置皮肤不可删除")
                    }
                    markedSkinId = null
                    deleteTarget = null
                }) { Text("删除", color = tokens.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消", color = tokens.onSurfaceVariant)
                }
            },
        )
    }

    // 二维码面板：显示开关 + 放置位置（左下 / 下方 / 右下）
    // 唯一的编辑面板：皮肤声明了哪一项，就按那一项的声明渲染
    val activeEdit = activeEditId?.let { id -> editables.firstOrNull { it.id == id } }
    if (activeEdit != null) {
        EditSheet(
            entry = activeEdit,
            state = editStateFor(activeEdit.id),
            bundledFonts = remember(context) { fontProvider.bundledFonts() },
            userFonts = remember(context) { fontProvider.userFonts().map { it.name } },
            playerColor = request.baseColor,
            onDismiss = { activeEditId = null },
            onPickImage = {
                imageTargetId = activeEdit.id
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onPickFontFile = {
                fontTargetId = activeEdit.id
                pickFontFile.launch(arrayOf("*/*"))
            },
            onChange = { updated -> updateEdit(activeEdit.id, updated) },
        )
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
 *  2. **宽度铺满可用空间、高度不够就上下滚动**（不再"缩到能塞下"）：
 *     长图不会被压成小卡片，文字大小始终和导出一致。
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
    val scrollState = rememberScrollState()
    Box(
        modifier = modifier.padding(horizontal = tokens.spacingLg, vertical = tokens.spacingSm),
        contentAlignment = Alignment.TopCenter,
    ) {
        when {
            emptyHint != null -> Text(emptyHint, style = LyricShareTextStyles.label(tokens))

            preview != null -> {
                val ratio = preview.width.toFloat() / preview.height.toFloat()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = "生成效果预览",
                        contentScale = ContentScale.FillWidth,
                        filterQuality = FilterQuality.High,   // 高清缩略：1080 渲染 → 界面缩放
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ratio)
                            .clip(RoundedCornerShape(tokens.radiusMd))
                            .border(1.dp, tokens.outline, RoundedCornerShape(tokens.radiusMd)),
                    )
                    // 底部留一点呼吸空间，滚到底时不会贴着操作面板
                    Spacer(Modifier.height(tokens.spacingSm))
                }
            }

            else -> Text("正在准备预览…", style = LyricShareTextStyles.label(tokens))
        }

        // 渲染/导出进度：半透明遮罩 + 环形进度（固定在可视区，不随海报滚动）
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
    onEdit: (String) -> Unit,
) {
    val tokens = LocalLyricShareTokens.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(tokens.spacingSm),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        // 内容与顺序完全来自皮肤声明：封面 / 背景 / 字体 / 二维码
        entries.forEach { entry ->
            PillButton(
                text = entry.label,
                icon = entry.icon,
                onClick = { onEdit(entry.id) },
            )
        }
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SkinRow(
    skins: List<LoadedSkin>,
    previews: Map<String, Bitmap>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onImport: () -> Unit,
    markedId: String?,
    onLongPress: (LoadedSkin) -> Unit,
    onDelete: (LoadedSkin) -> Unit,
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
                    .combinedClickable(
                        onClick = { onSelect(skin.id) },
                        onLongClick = { onLongPress(skin) },
                    ),
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

                    // 长按后：缩略图右上角出现删除角标，点击后弹确认框
                    if (markedId == skin.id) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(tokens.spacingXxs)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable { onDelete(skin) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "删除皮肤",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                        }
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

        // 最后一项：导入皮肤（选 zip → installSkin）
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(72.dp)
                    .clip(RoundedCornerShape(tokens.radiusSm))
                    .clickable(onClick = onImport),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 72.dp, height = 96.dp)
                        .clip(RoundedCornerShape(tokens.radiusSm))
                        .background(tokens.surfaceContainerHigh)
                        .border(1.dp, tokens.outline, RoundedCornerShape(tokens.radiusSm)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FileOpen,
                        contentDescription = "导入皮肤",
                        tint = tokens.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.height(tokens.spacingXxs))
                Text("导入", color = tokens.onSurfaceVariant, fontSize = tokens.textLabel, maxLines = 1)
            }
        }
    }
}

