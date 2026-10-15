# LyricShareKit —— 歌词图片分享（Kotlin / Compose）

把「选中歌词 → 生成一张好看的海报 → 存相册或走系统分享」做成一个可拖进任何 Compose 工程的独立模块。
渲染方式是**直接往 Bitmap 上画**（不是 View 截图），皮肤是**纯数据 JSON**（不是 dex 插件）。

> 编译验证：Gradle 9.3.1 + AGP 9.1.1 + Kotlin 2.2.10 + Compose BOM 2026.02.01 + compileSdk 36 / minSdk 26，
> `:lyricshare:assembleDebug` **BUILD SUCCESSFUL**（校验工程见文末）。

---

## 1. 需求对照

| 你的要求 | 实现位置 |
|---|---|
| 用 Kotlin 重写图片分享 | 全模块 Kotlin，`LyricShareImageActivity` + `ui/LyricShareImageScreen.kt` |
| 已有多选/复制沿用，不改 | 只接收 `LyricShareRequest.lyricLines`，不碰你的选择逻辑 |
| 图片生成时自定义字体 | `font/FontProvider.kt` + 页面「字体」入口 + `RenderOptions.fontOverride`（优先级最高） |
| 默认使用系统图片选择器 | 「封面」「背景」入口点开后走 `ActivityResultContracts.PickVisualMedia`（Android 13+ 系统相册，低版本自动回落） |
| 封面/背景由**皮肤包声明** | 皮肤在图片元素/背景上写 `editable`，界面据此**动态生成**入口与二级面板（换图/显隐/形状/大小/位置）；没声明就不出现。见 [皮肤制作指南 §4.4.2](docs/皮肤制作指南.md) |
| 可选择是否添加封面、形状、大小 | 二级面板：`显示封面`开关、形状（直角/圆角/圆形）、大小滑块——选项与范围都由皮肤声明 |
| 背景可调位置、大小、纯色 | 二级面板：水平/垂直位置、缩放、**纯色色卡**（首张"跟随播放页"= 宿主传的 `baseColor`）；出图与预览一致 |
| 版式完全由皮肤拼装 | 组合类型 `group`（row/column/stack，可嵌套）：子元素是完整元素，可各自声明 `editable`（含**行内锚点** `placement`），引擎无任何"某个皮肤专属"的版式代码 |
| 长歌词不截断 | 皮肤不写 `maxLines`，画布按内容自动加高；预览**铺满宽度 + 上下滚动**，长图不缩成小卡片 |
| **界面入口由皮肤声明** | 模块里没有硬编码的「封面/背景/字体/二维码」清单：扫描皮肤所有 `editable` 声明，一项渲染一个入口（名字/顺序/面板内容全来自声明） |
| 每个皮肤设置独立 | 用户调整按「皮肤 id + 可编辑项 id」各存一份，来回切换互不影响 |
| **调整会持久保存** | 换过的封面/背景、调好的颜色/大小/位置/字体**下次进页面自动读回**（含用户从相册选的图片，单独落盘）；重置清掉该项，删皮肤清掉它的全部 |
| 可重置 | 二级面板标题行常驻「重置」（封面/背景/字体都有） |
| 预览要清晰、且与出图零偏差 | 预览**不降分辨率**：和导出共用同一个渲染宽度（皮肤参考宽度，默认 1080）与同一台引擎，界面只做高清缩放；预览位图直接复用于导出 |
| 二维码生成保留 + 开关 | `qr/QrCodeGenerator.kt`（ZXing）。内容**默认按 `songId` 生成这首歌的网页链接**，页面上有显示开关 |
| 「来自 XXX」隐藏，且成图不预留 | 默认 `showBrandElement = false`，`role:"brand"` 元素**不参与度量**，下面元素自然上移 |
| 分享时可选择本地保存或原生分享 | 底部弹层：`保存到相册` / `系统分享`（`share/ImageExporter.kt`） |
| UI 注释清楚、方便视觉统一 | `ui/LyricShareTokens.kt`（唯一取值来源）+ 每个 Composable 都有职责注释 |
| 皮肤怎么制作 | [`docs/皮肤制作指南.md`](docs/皮肤制作指南.md) + 3 份可直接抄的示例皮肤 |

### 与「模板 XML 截图 + 可执行模板插件」那套实现的差异

| | 常见做法（代码插件） | 本模块 |
|---|---|---|
| 出图方式 | 模板 XML inflate → `View.draw(Canvas)` 截屏 | `LyricTemplate` 数据 → 直接画 `Canvas` |
| 分辨率 | 绑定屏幕密度，预览=屏幕大小 | 预览与导出**同分辨率、同引擎**，界面只缩放显示；输出尺寸由皮肤决定，长歌词自动加高 |
| 长歌词 | 受 `ScrollView` 可见区域影响 | 自动高度，多长都完整 |
| 模板 | 下载 **dex 插件**，`DexClassLoader` 加载 `LyricTemplateFragment` | 下载 **JSON 皮肤**，解析失败自动忽略，无代码执行 |
| 封面/背景 | 固定专辑图 + uCrop 裁剪 | 默认固定专辑图，点预览右下角即用系统图片选择器直选（Photo Picker），可一键恢复专辑图 |
| 分享 | 塞静态字段 + JPEG 落缓存 + 微信 thumbData 水印 | 直接 `saveToGallery` / `ACTION_SEND` + `FileProvider` |

---

## 2. 目录结构

```
LyricShareKit/
├── build.gradle.kts                   # 版本与 Melodia 对齐，最小依赖（Compose + ZXing）
├── docs/皮肤制作指南.md                # 皮肤协议 + 制作流程 + 设计规范
└── src/main/
    ├── AndroidManifest.xml            # Activity + FileProvider(+ API28 及以下的写存储权限)
    ├── assets/skins/                  # 3 份内置皮肤（default_light / dark_card / film_black）
    ├── res/values/strings.xml         # 可本地化的文案
    └── java/com/music/lyricshare/
        ├── LyricShareKit.kt           # 门面：launch / render / save / share + LyricShareConfig
        ├── LyricShareModels.kt        # LyricShareRequest（入参）/ LyricShareData（渲染输入）
        ├── LyricShareImageActivity.kt # 宿主 Activity（薄壳）
        ├── render/LyricImageRenderer.kt   # ★ 渲染引擎：度量 + 绘制
        ├── skin/SkinSpec.kt           # 皮肤数据模型
        ├── skin/SkinParser.kt         # JSON 解析（org.json，零依赖）
        ├── skin/SkinAssets.kt         # 皮肤资源解析（assets / 本地目录）
        ├── skin/SkinRepository.kt     # 内置皮肤 + 下载安装 + 兜底皮肤
        ├── font/FontProvider.kt       # 字体解析（皮肤字体 / 系统字体 / 用户导入）
        ├── qr/QrCodeGenerator.kt      # 二维码
        ├── share/ImageExporter.kt     # 存相册 / 系统分享
        ├── ui/LyricShareTokens.kt     # 视觉 Token（= MelodiaSpacing/MelodiaShapes）
        ├── ui/LyricShareImageScreen.kt# ★ 页面（含全部注释）
        ├── ui/LyricShareSheets.kt     # 保存/分享弹层 + 字体选择弹层
        └── util/                      # 位图、模糊、zip 工具
```

---

## 3. 接入 Melodia（三步）

### 步骤 1：把模块加进工程

```kotlin
// settings.gradle.kts
include(":lyricshare")

// app/build.gradle.kts
implementation(project(":lyricshare"))
```

复制 `LyricShareKit/` 到工程根目录、改个包名（`com.music.lyricshare` → 你的）即可。
模块自带 `AndroidManifest` 和 `FileProvider`，**不需要**在 app 模块里重复声明。

### 步骤 2：初始化（`MelodiaApp.onCreate` 或 DI 里）

```kotlin
LyricShareKit.install(this)

// 封面通常是 http URL：模块不内置图片库，交给宿主的 Coil 去取
LyricShareConfig.coverLoader = { context, url ->
    context.imageLoader.execute(
        ImageRequest.Builder(context).data(url).size(2048).build()
    ).image?.toBitmap()          // Coil 3 写法，按你工程里的版本微调
}

LyricShareConfig.galleryAlbumName = "Melodia"
LyricShareConfig.shareTextProvider = { "「${it.title}」— ${it.artists}" }
```

### 步骤 3：在歌词分享模式加一个「歌词图片」按钮

**① `FullScreenLyricsShareComponents.kt`** —— 底栏加一个胶囊按钮（保留原「复制歌词」）：

```kotlin
@Composable
fun FullScreenShareBottomBar(
    onCopy: () -> Unit,
    onImage: () -> Unit,                       // ← 新增
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = MelodiaSpacing.lg)
            .padding(top = 0.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SharePill(Icons.Rounded.ContentCopy, "复制歌词", onCopy)
            SharePill(Icons.Rounded.Image, "歌词图片", onImage)   // ← 新增
        }
    }
}

/** 与原有胶囊按钮完全同款：同圆角、同描边、同透明底 */
@Composable
private fun SharePill(icon: ImageVector, text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}
```

**② `FullScreenLyricsView.kt`** —— 收集已选歌词并拉起页面（和 `handleCopyLyrics` 同一套取值逻辑）：

```kotlin
// 建议给 FullScreenLyricsView 增加三个可选参数，在调用处（FullPlayerScreen）从当前播放歌曲传入：
//   album: String? = null, coverUrl: String? = null, songId: Long? = null

val handleShareImageLyrics: () -> Unit = {
    if (selectedLyricKeys.isEmpty()) {
        Toast.makeText(context, "请先选择要生成图片的歌词", Toast.LENGTH_SHORT).show()
    } else {
        // 与 handleCopyLyrics 完全一致的取行规则：主歌词 / 翻译(罗马音) / 背景和声
        val lines = buildList {
            lyrics.forEachIndexed { index, line ->
                val secondary = when (fullScreenLyricSecondaryMode) {
                    "translation" -> line.translation
                    "roma" -> line.roma
                    else -> null
                }
                if ("${index}_main" in selectedLyricKeys && line.text.isNotBlank()) add(line.text)
                if ("${index}_sec" in selectedLyricKeys && !secondary.isNullOrBlank()) add(secondary)
                if ("${index}_bg" in selectedLyricKeys && !line.backgroundLine?.text.isNullOrBlank()) {
                    add(line.backgroundLine!!.text)
                }
            }
        }
        val activity = context as? android.app.Activity
        if (activity != null) {
            LyricShareKit.launch(
                activity,
                LyricShareRequest(
                    title = title,
                    artists = artist,
                    album = album,
                    lyricLines = lines,
                    coverUrl = coverUrl,                                   // Coil 用的 http 地址
                    qrContent = songId?.let { songShareLink(it) },         // 你自己的 scheme / https 链接；null = 不出二维码
                    // brandText 默认 null = 「来自 XXX」不显示、不占位
                )
            )
            exitShareMode()
        }
    }
}
```

**③ 调用处**（`FullScreenLyricsView.kt:825` 附近）：

```kotlin
FullScreenShareBottomBar(
    onCopy = handleCopyLyrics,
    onImage = handleShareImageLyrics,          // ← 新增
    modifier = Modifier.fillMaxWidth().padding(top = featherDp)
)
```

---

## 4. API 速查

```kotlin
// 打开生成页
LyricShareKit.launch(activity, request)

// 无界面直接出图（后台生成 / 自定义预览 / 批量）
val bitmap = LyricShareKit.render(context, request, skinId = "dark_card", widthPx = 1440)

// 存相册 / 系统分享
LyricShareKit.saveToGallery(context, bitmap)
LyricShareKit.shareViaSystem(context, bitmap, request)

// 皮肤
LyricShareKit.skins(context)                                  // 内置 + 已下载
LyricShareKit.installSkin(context, zipFile, expectedMd5)       // 安装下发皮肤

// 字体
LyricShareKit.fontProvider(context).bundledFonts()             // assets/fonts/*
LyricShareKit.fontProvider(context).userFonts()                // 用户导入的
```

`LyricShareRequest` 字段：`title` / `artists` / `album` / `lyricLines` / `coverUri` / `coverUrl` /
`qrContent` / `defaultSkinId` / `outputWidthPx` / `brandText`（默认 null）。

---

## 5. 皮肤怎么做

完整协议见 [`docs/皮肤制作指南.md`](docs/皮肤制作指南.md)。最短路径：

```jsonc
{
  "id": "my_skin", "name": "我的皮肤", "schemaVersion": 1,
  "canvas": {
    "width": 1080,
    "padding": { "left": 72, "top": 96, "right": 72, "bottom": 72 },
    "background": { "type": "image", "useCover": true, "blur": 48, "scrim": { "color": "#000000", "opacity": 0.7 } }
  },
  "defaults": { "color": "#F2F2F2", "size": 42, "lineSpacing": 32, "align": "center" },
  "elements": [
    { "type": "image", "useCover": true, "width": 420, "height": 420, "x": "center", "circle": true,
      "editable": { "label": "封面", "shapes": ["circle", "rounded", "rect"], "sizeRange": [0.6, 1.4] } },
    { "type": "text",  "text": "{title}",   "size": 48, "marginTop": 48 },   // 同样不写 maxLines，长标题自动换行
    { "type": "text",  "text": "{artists}", "size": 30, "marginTop": 10 },
    { "type": "lyric", "marginTop": 64 },          // 不写 maxLines：有多少歌词就画多少，画布自动加高
    { "type": "qrcode", "width": 132, "height": 132, "x": "center", "marginTop": 72,
      "qr": { "errorCorrection": "Q", "quietZone": 1 } }
  ]
}
```

要点：
- 元素**不写 `y` 就是流式**（自动堆叠、自动算高度）；需要精确压位再写 `y`。
- 所有数值都是「1080 基准像素」，渲染时整体等比缩放 —— 预览和导出长得一模一样。
- **颜色一律写 `#RRGGBB`**（出图不需要 alpha 通道）；需要半透明（压暗层、阴影）就加一个独立的
  `"opacity": 0~1` 字段，不要再写 `#AARRGGBB`（仍兼容，但示例与规范都不用）。
- 不需要来源文案就**别写**这类元素；写了标 `"role": "brand"` 也默认不画且不占位。
- 想让用户在界面上调什么，就在对应元素 / `canvas.background` / `defaults` 上写 `editable`（见指南 §4.4.2）；
  **界面上的入口完全由这些声明生成**（模块不硬编码），不写就没有入口。
- **歌词/标题别写 `maxLines`**：写了超出就会截断成 `…`；不写则画布自动加高（预览支持上下滚动）。
- **别用 `canvas.minHeight` 撑高度**：歌词少的时候会被撑成一张大卡片。
- 皮肤列表的缩略图由引擎实时渲染，**不需要单独准备预览图**。
- 内置：放 `assets/skins/<id>/`；下发：打成 zip（`skin.json` 在根目录）→ `installSkin(zip, md5)`。

---

## 6. 编译验证

```bash
# 校验工程（含 settings/build/local.properties，通过 projectDir 指向本模块）
cd /home/dev/wyy/official/verify
JAVA_HOME=/home/dev/tools/jdk-21.0.12.1+1 \
  ~/.gradle/wrapper/dists/gradle-9.3.1-bin/*/gradle-9.3.1/bin/gradle \
  -p . :lyricshare:assembleDebug --console=plain
# → BUILD SUCCESSFUL，零 warning，产物：LyricShareKit/build/outputs/aar/lyricshare-debug.aar
```

皮肤 JSON 另有一层校验：`docs/` 里的字段白名单脚本会把 `skin.json` 的所有键与解析器对照，防止拼错字段静默失效。

---

## 7. 已知限制

- `minSdk 26`（`StaticLayout.Builder` 与 `ExifInterface(InputStream)` 的要求；Melodia 也是 26）。
- API 28 及以下存相册需要 `WRITE_EXTERNAL_STORAGE`，页面会在需要时自己申请。
- 模糊是三次盒式模糊逼近高斯（纯 Kotlin），1080 宽、半径 48 大约 20~40ms，已放在后台线程；
  背景会先缩到 720 再模糊，所以实际更快。
- 一个皮肤同时只有一层背景；多层叠加请用 `image` 元素 + `scrim`。
- 导出 PNG 是 ARGB_8888，1080×2000 约 8.6MB；**预览就是这个分辨率**（保证清晰、且与出图零偏差），
  界面用 `FilterQuality.High` 缩放显示；只有皮肤列表缩略图用 220 宽以省内存与渲染时间。

---

## 8. 许可与合规

- 本模块：**MIT**，见 [`LICENSE`](LICENSE)（版权署名与 Melodia 保持一致；要改名直接改 `LICENSE`）。
- 第三方依赖：
  - `com.google.zxing:core` —— **Apache-2.0**，分发时需保留其许可/NOTICE 文本（建议在 App 的"开源许可"页列出）；
  - Compose / AndroidX —— Apache-2.0，同上。
- 合规注意（与本模块的实现无关，但会影响你的产品）：
  - 二维码内容由调用方传入，**不要把第三方 App 的 URL Scheme 硬编码进去**；用你自己的 scheme 或 https 分享链接。
  - 成图与界面不要出现第三方音乐 App 的品牌名/logo/"来自 XXX"（本模块默认就不绘制 `role:"brand"` 的元素）。

---

## 9. 常见问题

**Q：预览时弹出「生成预览失败：The coroutine scope left the composition」？**

这是把**协程取消**当成"失败"的经典坑，早期版本有过，现已修复（可对照 `ui/LyricShareImageScreen.kt` 里的 `runCatchingNoCancel`）。

原因：Compose 在 `LaunchedEffect` 的 key 变化、或页面离开组合时，会用 `CancellationException`
取消正在跑的协程（消息就是那句 "The coroutine scope left the composition"）——这是**正常控制流**。
而 `runCatching { }` 捕获的是 `Throwable`，会把取消也吞掉并当成渲染失败。

如果你在二开时新增了"包着挂起调用"的 `runCatching`，请改成：

```kotlin
private suspend fun <T> runCatchingNoCancel(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation          // 取消必须原样抛出
} catch (throwable: Throwable) {
    Result.failure(throwable)
}
```

另外本模块已做"封面未就绪先不渲染"的门控（`coverPending`），避免首帧白渲染一张无封面的图再被取消。

**Q：换皮肤/换封面后预览闪一下白？**
预览在渲染完成前会保留上一张图（只有首次没有图时才显示进度）。如果确实看到空白，检查是不是把
`preview = it` 改成了先置 `null`。

**Q：想改预览大小时注意什么？**
不要改。预览、导出、右下角换图后的重渲染共用同一个 `exportWidth`（= 皮肤参考宽度），
界面只负责缩放显示 —— 这是"预览清晰 + 与出图零偏差 + 导出可复用预览位图"三件事的前提。

**Q：界面上没有「封面」「背景」入口？**
这是**故意的**：入口由皮肤声明驱动。皮肤里对应元素/背景没写 `editable`，就不出现入口
（例如某个皮肤不想要可换的封面）。要加就在皮肤里写：

```jsonc
{ "type": "image", "role": "cover", "useCover": true, "editable": { "label": "封面" } }
```

**Q：状态栏是纯黑一条、和页面底色对不上？**
页面底色是 `#101014`，如果状态栏区域显示成 `#000` 就会出现一条接缝。本模块已经处理：
主题里 `windowBackground` = `@color/ls_surface`（同一个值），Activity 用
`enableEdgeToEdge(SystemBarStyle.dark(TRANSPARENT), ...)` 强制状态栏/导航栏透明 + 浅色图标。
如果你是把这个 Composable 嵌进**自己的** Activity，请在那边的主题里也把
`windowBackground` 设成页面底色，并同样用 `SystemBarStyle.dark(...)` —— 否则色块来自宿主主题。
（Android 15+ 已忽略 `statusBarColor`，边到边是强制的，这类接缝基本都是 `windowBackground` 造成的。）


