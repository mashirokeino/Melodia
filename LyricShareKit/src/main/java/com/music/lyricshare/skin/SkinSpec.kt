package com.music.lyricshare.skin

import android.graphics.Color

/**
 * ============================================================================
 *  皮肤（Skin）数据模型 —— 一份 JSON 描述一张歌词海报长什么样
 * ============================================================================
 *  设计原则（和「下载可执行插件」那套做法最大的区别）：
 *   1. 皮肤是**纯数据**，不含代码 → 可以热更、可以审核、可以跨端复用、不会执行任意代码；
 *   2. 坐标系以「参考画布宽度」(canvas.width，默认 1080) 为单位，渲染时整体等比缩放，
 *      所以同一套皮肤在预览(小图)和导出(大图)下完全一致，且与设备密度无关；
 *   3. 元素默认「流式排版」（从上往下堆叠、自动算高度），需要精确位置时再用 x/y 绝对定位，
 *      皮肤作者不用写布局代码。
 */

/** 元素类型。除 TEXT/LYRIC/IMAGE/QRCODE 外，DIVIDER/SPACER 用来做留白与分隔线。 */
enum class ElementType {
    TEXT, LYRIC, IMAGE, QRCODE, DIVIDER, SPACER,

    /**
     * 组合类型：把若干子元素按 [SkinElement.orientation] 摆在一起，作为**一个整体**参与排版。
     * 皮肤可以借它拼出引擎里没有的"新类型"（例如「歌名+歌手+二维码」信息条），
     * 整体也能有自己的可编辑项（显隐 / 缩放 / 位移 / 锚点 / 底色）。子元素可继续嵌套 GROUP。
     */
    GROUP;

    companion object {
        fun from(raw: String?): ElementType = when (raw?.trim()?.lowercase()) {
            "text" -> TEXT
            "lyric", "lyrics", "lrc" -> LYRIC
            "image", "img" -> IMAGE
            "qrcode", "qr" -> QRCODE
            "divider", "line" -> DIVIDER
            "spacer", "space" -> SPACER
            "group", "row", "column", "stack", "composite" -> GROUP
            else -> TEXT
        }
    }
}

/** 水平对齐。既用于元素整体的 x 定位，也用于文本内部的对齐。 */
enum class HAlign {
    LEFT, CENTER, RIGHT;

    companion object {
        fun from(raw: String?, fallback: HAlign = LEFT): HAlign = when (raw?.trim()?.lowercase()) {
            "left", "start" -> LEFT
            "center", "centre" -> CENTER
            "right", "end" -> RIGHT
            else -> fallback
        }
    }
}

/** 垂直定位模式：流式 / 绝对 / 垂直居中 / 贴底。 */
enum class VMode { FLOW, TOP, CENTER, BOTTOM }

/** 图片填充方式。 */
enum class ScaleType {
    CENTER_CROP, FIT_CENTER;

    companion object {
        fun from(raw: String?): ScaleType = when (raw?.trim()?.lowercase()) {
            "fit", "fit_center", "contain" -> FIT_CENTER
            else -> CENTER_CROP
        }
    }
}

/** 字体来源。 */
enum class FontSource {
    /** 渲染引擎默认字体（宿主可配置）。 */
    DEFAULT,

    /** 皮肤包内的字体文件，路径相对皮肤根目录，如 "fonts/serif.otf"。 */
    ASSETS,

    /** 绝对路径的字体文件（用户从系统选择字体后复制到 App 私有目录的场景）。 */
    FILE,

    /** 系统字体族名，如 "serif" / "sans-serif" / "sans-serif-medium"。 */
    SYSTEM;

    companion object {
        fun from(raw: String?): FontSource = when (raw?.trim()?.lowercase()) {
            "assets", "asset", "skin" -> ASSETS
            "file", "path" -> FILE
            "system", "family" -> SYSTEM
            else -> DEFAULT
        }
    }
}

/** 背景类型。 */
enum class BgType {
    COLOR, GRADIENT, IMAGE;

    companion object {
        fun from(raw: String?): BgType = when (raw?.trim()?.lowercase()) {
            "gradient", "linear" -> GRADIENT
            "image", "img", "photo" -> IMAGE
            else -> COLOR
        }
    }
}

/** 四边内边距 / 外边距（单位：参考画布像素）。 */
data class Insets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    companion object {
        val ZERO = Insets()
    }
}

/** 字体描述：皮肤里写 [FontSpec]，用户在 UI 上选择的字体则以「全局覆盖」的形式传进渲染器。 */
data class FontSpec(
    val source: FontSource = FontSource.DEFAULT,
    /** ASSETS/FILE 为路径，SYSTEM 为字体族名，DEFAULT 忽略。 */
    val value: String? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
) {
    companion object {
        val DEFAULT = FontSpec()
    }
}

/** 阴影（画在 Bitmap 上是软件渲染，setShadowLayer 可正常生效）。 */
data class ShadowSpec(
    val radius: Float = 0f,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val color: Int = Color.argb(64, 0, 0, 0),
) {
    val enabled: Boolean get() = radius > 0f
}

/** x 轴定位：对齐方式 + 偏移量（数字形式的 "x" 会被解析成 LEFT + offset）。 */
data class PosX(val align: HAlign = HAlign.LEFT, val offset: Int = 0)

/** y 轴定位：缺省 [VMode.FLOW]（跟在上一元素下面），否则按参考画布绝对定位。 */
data class PosY(val mode: VMode = VMode.FLOW, val offset: Int = 0)

/** 二维码专属参数。 */
data class QrSpec(
    /** 纠错级别 L/M/Q/H；带 logo 时引擎会自动抬到 H。 */
    val errorCorrection: String = "M",
    /** 静默区（模块数），0 表示不留白，由皮肤自己控制留白。 */
    val quietZone: Int = 1,
    /** 二维码底色，默认白色。 */
    val backgroundColor: Int = Color.WHITE,
    /** 中心 logo：皮肤包内路径。 */
    val logo: String? = null,
    /** 中心 logo 用运行时封面（专辑图）。 */
    val logoUseCover: Boolean = false,
    /** logo 占二维码边长比例，建议 ≤ 0.22。 */
    val logoScale: Float = 0.2f,
    /** 二维码下方的说明文案；为 null 表示不画、也不占空间。 */
    val label: String? = null,
)

/**
 * 图片形状（用户可在界面上切换）。
 */
enum class ImageShape {
    /** 用元素自身声明的形状（圆角/圆形）。 */
    DEFAULT,
    /** 直角。 */
    RECT,
    /** 圆角矩形。 */
    ROUNDED,
    /** 圆形。 */
    CIRCLE;

    companion object {
        fun from(raw: String?): ImageShape = when (raw?.trim()?.lowercase()) {
            "rect", "square", "none" -> RECT
            "rounded", "round" -> ROUNDED
            "circle", "oval" -> CIRCLE
            else -> DEFAULT
        }
    }
}

/**
 * ============================================================================
 *  「用户在界面上能改什么」——完全由皮肤包声明
 * ============================================================================
 *  写在图片元素上（`"editable": {...}`）或画布背景上（`canvas.background.editable`）。
 *  简写 `"editable": true` = 全部开启；**不写 = 这一项不可编辑，界面上不出现入口**。
 *
 *  界面按声明动态生成入口（"封面"/"背景"）与二级面板（换图/显示/形状/大小/位置），
 *  所以切换皮肤时这些入口会自动跟着新皮肤变。
 */
/**
 * 可调「属性」—— 引擎按它**通用地**应用到任意元素；元素渲染器再各自落到自己的绘制上。
 * 界面层不需要知道元素是什么类型，只按属性 + 控件渲染。
 */
enum class EditProperty {

    /** 显示 / 隐藏（关掉后不占空间）。 */
    VISIBLE,
    /** 换掉元素的可替换位图（图片内容 / 背景图 / 二维码中心 logo）。 */
    SOURCE,
    /** 形状（绘制矩形的圆角 / 圆形）。 */
    SHAPE,
    /** 缩放：乘到元素的"生效尺寸"上（文字=字号、图片=占位、二维码=码块、分割线=粗细、组合=整体）。 */
    SCALE,
    /** 水平位移（相对画布宽的比例）。 */
    OFFSET_X,
    /** 垂直位移（相对画布高的比例）。 */
    OFFSET_Y,
    /** 锚点位置（枚举，如左下 / 下方 / 右下 / 居中）—— 吸附到画布对应角，并脱离流式排版。 */
    PLACEMENT,
    /** 主色：文本=字色、二维码=码点、分割线=线色、图片=着色。 */
    COLOR,
    /** 元素背后的底色（二维码静默区、文字高亮底、图片底板），可设为透明。 */
    BACKGROUND,
    /** 字体（文本类）。 */
    FONT;

    companion object {
        fun from(raw: String?): EditProperty? = when (raw?.trim()?.lowercase()?.replace("-", "_")) {
            "visible", "visibility", "show" -> VISIBLE
            "source", "replace", "image", "bitmap" -> SOURCE
            "shape", "corner" -> SHAPE
            "scale", "size" -> SCALE
            "offset_x", "x", "move_x" -> OFFSET_X
            "offset_y", "y", "move_y" -> OFFSET_Y
            "placement", "anchor", "align", "position" -> PLACEMENT
            "color", "tint" -> COLOR
            "background", "bg", "solid" -> BACKGROUND
            "font", "typeface" -> FONT
            else -> null
        }
    }
}

/** 界面用哪种控件来编辑某个属性。 */
enum class ControlKind {
    SWITCH, SLIDER, COLOR, ENUM, FONT, IMAGE, SHAPE;

    companion object {
        fun from(raw: String?): ControlKind? = when (raw?.trim()?.lowercase()) {
            "switch", "toggle", "bool" -> SWITCH
            "slider", "range" -> SLIDER
            "color", "colour", "swatch" -> COLOR
            "enum", "options", "chips", "select" -> ENUM
            "font", "fonts" -> FONT
            "image", "photo", "album" -> IMAGE
            "shape", "corner" -> SHAPE
            else -> null
        }

        /** 没写 `control` 时按属性推断。 */
        fun forProperty(property: EditProperty): ControlKind = when (property) {
            EditProperty.VISIBLE -> SWITCH
            EditProperty.SOURCE -> IMAGE
            EditProperty.SHAPE -> SHAPE
            EditProperty.SCALE, EditProperty.OFFSET_X, EditProperty.OFFSET_Y -> SLIDER
            EditProperty.PLACEMENT -> ENUM
            EditProperty.COLOR, EditProperty.BACKGROUND -> COLOR
            EditProperty.FONT -> FONT
        }
    }
}

/**
 * 一条「控件声明」：皮肤自己决定给用户开放哪个属性、用什么控件、范围多少。
 * 界面按这张表逐条渲染控件，**不做任何元素类型判断**。
 */
data class ControlSpec(
    val property: EditProperty,
    val kind: ControlKind = ControlKind.forProperty(property),
    /** 控件标签；不写则用属性默认名。 */
    val label: String? = null,
    /** SLIDER 的范围。 */
    val range: List<Float> = emptyList(),
    /** ENUM 的可选项（`left` / `center` / `right` / `top` / `bottom` / `skin`…）。 */
    val options: List<String> = emptyList(),
    /** COLOR：是否给一个"透明"档。 */
    val allowTransparent: Boolean = false,
    /** 是否允许"跟随皮肤"（第一张斜杠）。 */
    val allowNone: Boolean = true,
)

data class EditableSpec(
    /** 稳定标识（可选）。界面用它记住用户的调整；不写则按元素顺序自动生成。 */
    val id: String? = null,
    /** 入口在操作行里的排序（可选，小的在前）；不写则按它在皮肤里的先后。 */
    val order: Int? = null,
    /** 界面上显示的名字，如 "封面" / "背景"。 */
    val label: String? = null,
    /** 允许换图（系统图片选择器）。 */
    val replace: Boolean = true,
    /** 允许显示/隐藏 —— "是否添加封面"就是这个开关。 */
    val visibility: Boolean = true,
    /** 允许改形状（仅图片元素；背景恒为铺满，忽略此项）。 */
    val shape: Boolean = true,
    /** 形状可选项，默认三种全给。 */
    val shapes: List<ImageShape> = listOf(ImageShape.ROUNDED, ImageShape.CIRCLE, ImageShape.RECT),
    /** 允许改大小：图片元素 = 改变占位尺寸（文字会跟着重排）；背景 = 缩放画面。 */
    val size: Boolean = true,
    /** 缩放范围（相对皮肤声明值）。 */
    val sizeRange: List<Float> = listOf(0.6f, 1.4f),
    /** 允许调位置。 */
    val move: Boolean = true,
    /** 位置偏移上限，相对参考画布宽/高的比例（0.25 = 最多挪 1/4 画布）。 */
    val moveRange: Float = 0.25f,
    /** 允许改颜色：文本=字体颜色；二维码=码点前景色。 */
    val color: Boolean = false,
    /** 允许改底色：目前用于二维码（含"透明底"）。 */
    val background: Boolean = false,
    /** 允许改字体（仅文本）。 */
    val font: Boolean = false,
    /**
     * 控件表：**新的、完全数据驱动**的写法。写了它就以它为准；
     * 不写则把上面的开关按元素类型的能力表展开（兼容旧皮肤）。
     */
    val controls: List<ControlSpec> = emptyList(),
) {
    /**
     * 展开成控件表：优先用手写的 [controls]；否则按 [type] 的能力表把旧式开关展开。
     * 这样旧皮肤不用改也能用，新皮肤可以精确控制"开哪些项、用什么控件、范围多少"。
     */
    fun controlsFor(type: ElementType): List<ControlSpec> =
        controls.ifEmpty { ElementCapabilities.expandLegacy(this, type) }

    companion object {
        /** 全开的默认声明（`"editable": true`）。 */
        val ALL = EditableSpec()
    }
}

/**
 * 一个绘制元素。
 *
 * 文本类元素的 [text] 支持占位符：
 *   {title} 歌名   {artists} 歌手   {album} 专辑
 *   {lines} 全部选中歌词   {firstLine} 第一行歌词   {count} 行数
 * LYRIC 类型默认渲染 [com.music.lyricshare.LyricShareRequest.lyricLines]，不需要写 text。
 */
/** 组合元素的排布方向。 */
enum class GroupOrientation {
    /** 横向一行。 */
    ROW,
    /** 纵向一列。 */
    COLUMN,
    /** 叠放（互相覆盖，取最大尺寸）。 */
    STACK;

    companion object {
        fun from(raw: String?): GroupOrientation = when (raw?.trim()?.lowercase()) {
            "column", "col", "vertical", "v" -> COLUMN
            "stack", "overlay", "z" -> STACK
            else -> ROW
        }
    }
}

data class SkinElement(
    val type: ElementType = ElementType.TEXT,
    /** 语义角色：cover / background / decoration / brand。role=="brand" 的元素可被全局开关隐藏且不占空间。 */
    val role: String? = null,
    val text: String? = null,
    val src: String? = null,
    val useCover: Boolean = false,
    val blur: Int = 0,
    val scrim: Int? = null,
    val x: PosX = PosX(),
    val y: PosY = PosY(),
    val width: Int? = null,
    val height: Int? = null,
    val margin: Insets = Insets.ZERO,
    val align: HAlign? = null,
    val size: Float? = null,
    val color: Int? = null,
    val font: FontSpec? = null,
    /** 行间距（参考画布像素，累加值）。 */
    val lineSpacing: Float? = null,
    /** 字间距（参考画布像素，内部换算成 em 交给 StaticLayout）。 */
    val letterSpacing: Float? = null,
    val maxLines: Int? = null,
    val ellipsize: Boolean = true,
    val cornerRadius: Int = 0,
    val circle: Boolean = false,
    val scaleType: ScaleType = ScaleType.CENTER_CROP,
    val shadow: ShadowSpec? = null,
    val qr: QrSpec = QrSpec(),
    /** 用户在界面上可以如何调整这个元素；null = 不可编辑（界面上不出现入口）。 */
    val editable: EditableSpec? = null,

    // ------------------------------------------------------------ 组合元素（type == GROUP）
    /** 子元素（仅 GROUP 用）。它们由组合自己排布，子元素的 x/y 不参与外层流式排版。 */
    val children: List<SkinElement> = emptyList(),
    /** 组合方向：一行 / 一列 / 叠放。 */
    val orientation: GroupOrientation = GroupOrientation.ROW,
    /** 子元素间距（参考画布像素）。 */
    val gap: Int = 24,
    /** 交叉轴对齐：start / center / end（组合自身在画布上的对齐仍由 [x]/[align] 决定）。 */
    val crossAlign: HAlign? = null,
) {
    /** 是否属于「品牌/来源」元素（例如"来自 XXX"）。默认不渲染，见 RenderOptions.showBrandElement。 */
    val isBrand: Boolean get() = role?.equals("brand", ignoreCase = true) == true

    /** 是否是「封面位」：会被用户在界面上替换/调整的那张图。 */
    val isCoverSlot: Boolean get() = type == ElementType.IMAGE && (useCover || role.equals("cover", ignoreCase = true))
}

/** 画布：参考宽度 + 高度策略（null = 按内容自适应）+ 内边距 + 背景。 */
data class CanvasSpec(
    val width: Int = 1080,
    /** 固定高度；null 表示 auto（内容多高就多高，至少 [minHeight]）。 */
    val height: Int? = null,
    val minHeight: Int = 0,
    val padding: Insets = Insets(72, 96, 72, 96),
    val background: BackgroundSpec = BackgroundSpec(),
)

/** 背景描述：纯色 / 线性渐变 / 图片（可用运行时封面、可模糊、可叠压暗层）。 */
data class BackgroundSpec(
    val type: BgType = BgType.COLOR,
    val color: Int = Color.WHITE,
    val colors: List<Int> = emptyList(),
    /** 渐变角度（度），0 = 从上到下。 */
    val angle: Float = 0f,
    val src: String? = null,
    val useCover: Boolean = false,
    val blur: Int = 0,
    val scrim: Int? = null,
    val scaleType: ScaleType = ScaleType.CENTER_CROP,
    /** 用户在界面上可以如何调整背景；null = 不可编辑。 */
    val editable: EditableSpec? = null,
)

/** 文本类元素的默认样式，元素自身字段优先。 */
data class ElementDefaults(
    /**
     * 文本类的「用户可编辑」声明，如 `"defaults": { "editable": { "font": true, "color": true } }`。
     * 不写 = 界面上没有「字体」入口（文字样式完全由皮肤决定）。
     */
    val editable: EditableSpec? = null,
    val color: Int = Color.BLACK,
    val size: Float = 44f,
    val font: FontSpec = FontSpec.DEFAULT,
    val lineSpacing: Float = 20f,
    val letterSpacing: Float = 0f,
    val align: HAlign = HAlign.LEFT,
)

/** 一处可编辑项挂在皮肤的哪个部位 —— 决定引擎如何应用用户的调整。 */
sealed interface EditableOwner {
    /**
     * 某个绘制元素，用**路径**寻址：顶层是 `[i]`，组合子元素是 `[i, j]`、`[i, j, k]`…
     * 这样组合里的子元素（如"二维码旁那行"里的二维码）也能有自己的可编辑项。
     */
    data class Element(val path: List<Int>, val element: SkinElement) : EditableOwner {
        /** 与渲染器对齐的键，如 "3" / "3.1"。 */
        val key: String get() = path.joinToString(".")
    }

    /** 画布背景。 */
    data object Background : EditableOwner

    /** 文本默认样式（字体 / 颜色）。 */
    data object TextDefaults : EditableOwner
}

/** 解析后的「可编辑项」：界面上的入口与面板完全由它生成。 */
data class SkinEditable(
    val id: String,
    val label: String,
    val spec: EditableSpec,
    val owner: EditableOwner,
)

/**
 * 皮肤声明的全部可编辑项，按 [EditableSpec.order]（没写则按出现顺序）排列。
 *
 * **界面不硬编码任何入口**：皮肤声明了几个、叫什么、按什么顺序、面板里有哪些可调项，
 * 全部由这份列表决定。皮肤一个都不声明 → 操作行上一个入口都没有。
 */
val SkinSpec.editables: List<SkinEditable>
    get() {
        val list = ArrayList<Pair<Int, SkinEditable>>()
        var seq = 0

        // 递归：顶层元素与组合子元素一视同仁 —— 谁能被编辑，看它自己有没有声明 editable
        fun walk(elements: List<SkinElement>, prefix: List<Int>) {
            elements.forEachIndexed { index, el ->
                val path = prefix + index
                el.editable?.let { spec ->
                    list += (spec.order ?: (1000 + seq++)) to SkinEditable(
                        id = spec.id ?: ("el_" + path.joinToString("_")),
                        label = spec.label ?: defaultLabel(el),
                        spec = spec,
                        owner = EditableOwner.Element(path, el),
                    )
                }
                if (el.children.isNotEmpty()) walk(el.children, path)
            }
        }
        walk(elements, emptyList())

        canvas.background.editable?.let { spec ->
            list += (spec.order ?: (1000 + seq++)) to SkinEditable(
                id = spec.id ?: "background",
                label = spec.label ?: "背景",
                spec = spec,
                owner = EditableOwner.Background,
            )
        }
        defaults.editable?.let { spec ->
            list += (spec.order ?: (1000 + seq++)) to SkinEditable(
                id = spec.id ?: "text",
                label = spec.label ?: "字体",
                spec = spec,
                owner = EditableOwner.TextDefaults,
            )
        }
        return list.sortedBy { it.first }.map { it.second }
    }

/** 没写 label 时按承载物给个默认名。 */
private fun defaultLabel(el: SkinElement): String = when {
    el.isCoverSlot -> "封面"
    el.type == ElementType.QRCODE -> "二维码"
    el.type == ElementType.IMAGE -> "图片"
    else -> "文字"
}

/** 一份皮肤。 */
data class SkinSpec(
    val id: String,
    val name: String,
    /** 皮肤格式版本；引擎按此判断能否解析。 */
    val schemaVersion: Int = 1,
    /** 要求的最低引擎版本，低于它则由宿主决定是提示升级还是直接忽略。 */
    val minEngineVersion: Int = 1,
    val canvas: CanvasSpec = CanvasSpec(),
    val defaults: ElementDefaults = ElementDefaults(),
    val elements: List<SkinElement> = emptyList(),
)

/** 皮肤的加载来源，用于 UI 上分组（内置 / 已下载）。 */
enum class SkinSource { BUNDLED, DOWNLOADED }

/**
 * 已经和「资源解析器」绑定好的皮肤。
 * [assets] 负责把皮肤内部的相对路径（背景图、字体、logo）读出来，
 * 内置皮肤从 assets/ 读，下载皮肤从 filesDir 读，渲染引擎不关心区别。
 */
class LoadedSkin(
    val spec: SkinSpec,
    val assets: SkinAssetResolver,
    val source: SkinSource = SkinSource.BUNDLED,
) {
    val id: String get() = spec.id
    val name: String get() = spec.name
}

/**
 * ============================================================================
 *  元素类型「能力注册表」
 * ============================================================================
 *  这里回答的是**引擎问题**：这一类元素在绘制上支持哪些可调属性。
 *  （例如"图片能着色、能换源、能裁形状"，而"文本能换字体，图片不能"。）
 *
 *  它**不参与界面逻辑**：界面只认皮肤声明的控件表（[EditableSpec.controlsFor]）；
 *  注册表只用来把旧式开关（`replace/visibility/size/move/...`）和 `"editable": true`
 *  展开成控件表，以及校验皮肤有没有声明引擎做不到的属性。
 */
object ElementCapabilities {

    /** 这一类元素支持哪些属性（用于 `"editable": true` 与旧式开关展开）。 */
    fun properties(type: ElementType): List<EditProperty> = when (type) {
        ElementType.IMAGE -> listOf(
            EditProperty.SOURCE, EditProperty.VISIBLE, EditProperty.SHAPE, EditProperty.SCALE,
            EditProperty.OFFSET_X, EditProperty.OFFSET_Y, EditProperty.PLACEMENT,
            EditProperty.COLOR, EditProperty.BACKGROUND,
        )
        ElementType.TEXT, ElementType.LYRIC -> listOf(
            EditProperty.VISIBLE, EditProperty.SCALE, EditProperty.OFFSET_X, EditProperty.OFFSET_Y,
            EditProperty.COLOR, EditProperty.BACKGROUND, EditProperty.FONT,
        )
        ElementType.QRCODE -> listOf(
            EditProperty.VISIBLE, EditProperty.PLACEMENT, EditProperty.SCALE,
            EditProperty.COLOR, EditProperty.BACKGROUND, EditProperty.SOURCE,
        )
        ElementType.DIVIDER -> listOf(
            EditProperty.VISIBLE, EditProperty.SCALE, EditProperty.OFFSET_X, EditProperty.OFFSET_Y, EditProperty.COLOR,
        )
        ElementType.SPACER -> listOf(EditProperty.VISIBLE, EditProperty.SCALE)
        ElementType.GROUP -> listOf(
            EditProperty.VISIBLE, EditProperty.SCALE, EditProperty.OFFSET_X, EditProperty.OFFSET_Y,
            EditProperty.PLACEMENT, EditProperty.BACKGROUND, EditProperty.SHAPE,
        )
    }

    fun supports(type: ElementType, property: EditProperty): Boolean = property in properties(type)

    /** 某个属性的"开箱"控件（范围/选项给一套通用默认值）。 */
    fun defaultControl(type: ElementType, property: EditProperty): ControlSpec = when (property) {
        EditProperty.VISIBLE -> ControlSpec(property, label = "显示")
        EditProperty.SOURCE -> ControlSpec(property, label = "换图")
        EditProperty.SHAPE -> ControlSpec(property, label = "形状")
        EditProperty.SCALE -> ControlSpec(property, label = "大小", range = defaultRange(type, property))
        EditProperty.OFFSET_X -> ControlSpec(property, label = "水平位置", range = listOf(-0.25f, 0.25f))
        EditProperty.OFFSET_Y -> ControlSpec(property, label = "垂直位置", range = listOf(-0.25f, 0.25f))
        EditProperty.PLACEMENT -> ControlSpec(
            property,
            label = "放置位置",
            options = listOf("skin", "left", "center", "right"),
        )
        EditProperty.COLOR -> ControlSpec(property, label = "颜色")
        EditProperty.BACKGROUND -> ControlSpec(property, label = "底色", allowTransparent = true)
        EditProperty.FONT -> ControlSpec(property, label = "字体")
    }

    private fun defaultRange(type: ElementType, property: EditProperty): List<Float> = when (type) {
        ElementType.QRCODE -> listOf(0.5f, 2f)
        ElementType.GROUP -> listOf(0.5f, 2f)
        ElementType.TEXT, ElementType.LYRIC -> listOf(0.6f, 1.6f)
        else -> listOf(0.6f, 1.4f)
    }

    /**
     * 把旧式开关（`replace / visibility / shape / size / move / color / background / font`）
     * 按元素类型的能力表展开成控件表 —— 老皮肤不用改也能跑。
     */
    fun expandLegacy(spec: EditableSpec, type: ElementType): List<ControlSpec> = buildList {
        if (spec.replace && supports(type, EditProperty.SOURCE)) add(defaultControl(type, EditProperty.SOURCE))
        if (spec.visibility) add(defaultControl(type, EditProperty.VISIBLE))
        if (spec.background && supports(type, EditProperty.BACKGROUND)) {
            add(defaultControl(type, EditProperty.BACKGROUND))
        }
        if (spec.color && supports(type, EditProperty.COLOR)) add(defaultControl(type, EditProperty.COLOR))
        if (spec.shape && supports(type, EditProperty.SHAPE)) add(defaultControl(type, EditProperty.SHAPE))
        if (spec.font && supports(type, EditProperty.FONT)) add(defaultControl(type, EditProperty.FONT))
        if (spec.size && supports(type, EditProperty.SCALE)) {
            add(defaultControl(type, EditProperty.SCALE).copy(range = spec.sizeRange))
        }
        if (spec.move) {
            // 注册表决定"位置"用哪种语义：二维码/组合是枚举锚点，其余是连续偏移
            if (type == ElementType.QRCODE || type == ElementType.GROUP) {
                add(defaultControl(type, EditProperty.PLACEMENT))
            } else {
                add(defaultControl(type, EditProperty.OFFSET_X).copy(range = listOf(-spec.moveRange, spec.moveRange)))
                add(defaultControl(type, EditProperty.OFFSET_Y).copy(range = listOf(-spec.moveRange, spec.moveRange)))
            }
        }
    }
}
