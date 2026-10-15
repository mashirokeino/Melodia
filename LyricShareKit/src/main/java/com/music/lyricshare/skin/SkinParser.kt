package com.music.lyricshare.skin

import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * ============================================================================
 *  皮肤 JSON 解析器
 * ============================================================================
 *  用 Android 自带的 org.json，不引第三方序列化库，保证模块零额外依赖。
 *  解析策略一律「缺字段就用默认值」，所以皮肤作者可以只写自己关心的字段，
 *  老引擎遇到新字段也只是忽略，向后兼容。
 *
 *  完整字段说明见 docs/皮肤制作指南.md。
 */
object SkinParser {

    /** 引擎能解析的最高皮肤格式版本。 */
    const val ENGINE_SCHEMA_VERSION = 1

    /** 引擎版本号，皮肤可用 minEngineVersion 做灰度/兜底。 */
    const val ENGINE_VERSION = 1

    fun parse(json: String, assets: SkinAssetResolver, source: SkinSource = SkinSource.BUNDLED): LoadedSkin {
        val root = JSONObject(json)
        val spec = SkinSpec(
            id = root.optString("id").ifBlank { "unnamed" },
            name = root.optString("name").ifBlank { root.optString("id", "未命名皮肤") },
            schemaVersion = root.optInt("schemaVersion", 1),
            minEngineVersion = root.optInt("minEngineVersion", 1),
            canvas = parseCanvas(root.optJSONObject("canvas")),
            defaults = parseDefaults(root.optJSONObject("defaults")),
            elements = parseElements(root.optJSONArray("elements")),
        )
        return LoadedSkin(spec, assets, source)
    }

    // ---------------------------------------------------------------- canvas

    private fun parseCanvas(jo: JSONObject?): CanvasSpec {
        if (jo == null) return CanvasSpec()
        return CanvasSpec(
            width = jo.optInt("width", 1080).coerceIn(360, 4096),
            height = jo.optIntOrNull("height")?.coerceIn(360, 8192),
            minHeight = jo.optInt("minHeight", 0),
            padding = parseInsets(jo.optJSONObject("padding")) ?: Insets(72, 96, 72, 96),
            background = parseBackground(jo.optJSONObject("background")),
            songInfo = parseSongInfo(jo.opt("songInfo")),
        )
    }

    private fun parseBackground(jo: JSONObject?): BackgroundSpec {
        if (jo == null) return BackgroundSpec()
        val colors = jo.optJSONArray("colors")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> parseColor(arr.opt(i)) }
        }.orEmpty()
        return BackgroundSpec(
            type = BgType.from(jo.optString("type")),
            color = parseColor(jo.opt("color")) ?: Color.WHITE,
            colors = colors,
            angle = jo.optDouble("angle", 0.0).toFloat(),
            src = jo.optStringOrNull("src"),
            useCover = jo.optBoolean("useCover", false),
            blur = jo.optInt("blur", 0),
            scrim = parseColor(jo.opt("scrim")),
            scaleType = ScaleType.from(jo.optString("scaleType")),
            editable = parseEditable(jo.opt("editable")),
        )
    }

    // -------------------------------------------------------------- defaults

    private fun parseDefaults(jo: JSONObject?): ElementDefaults {
        if (jo == null) return ElementDefaults()
        return ElementDefaults(
            color = parseColor(jo.opt("color")) ?: Color.BLACK,
            size = jo.optDouble("size", 44.0).toFloat(),
            editable = parseEditable(jo.opt("editable")),
            font = parseFont(jo.optJSONObject("font")) ?: FontSpec.DEFAULT,
            lineSpacing = jo.optDouble("lineSpacing", 20.0).toFloat(),
            letterSpacing = jo.optDouble("letterSpacing", 0.0).toFloat(),
            align = HAlign.from(jo.optString("align"), HAlign.LEFT),
        )
    }

    // -------------------------------------------------------------- elements

    private fun parseElements(arr: JSONArray?): List<SkinElement> {
        if (arr == null) return emptyList()
        val out = ArrayList<SkinElement>(arr.length())
        for (i in 0 until arr.length()) {
            val jo = arr.optJSONObject(i) ?: continue
            out += parseElement(jo)
        }
        return out
    }

    private fun parseElement(jo: JSONObject): SkinElement {
        val type = ElementType.from(jo.optString("type"))
        return SkinElement(
            type = type,
            role = jo.optStringOrNull("role"),
            text = jo.optStringOrNull("text"),
            src = jo.optStringOrNull("src"),
            useCover = jo.optBoolean("useCover", false) || jo.optStringOrNull("role") == "cover",
            blur = jo.optInt("blur", 0),
            scrim = parseColor(jo.opt("scrim")),
            x = parsePosX(jo),
            y = parsePosY(jo),
            width = jo.optIntOrNull("width"),
            height = jo.optIntOrNull("height"),
            margin = parseInsets(jo.optJSONObject("margin")) ?: parseFlatMargins(jo),
            align = jo.optStringOrNull("align")?.let { HAlign.from(it) },
            size = jo.optDoubleOrNull("size")?.toFloat(),
            color = parseColor(jo.opt("color")),
            font = parseFont(jo.optJSONObject("font")),
            lineSpacing = jo.optDoubleOrNull("lineSpacing")?.toFloat(),
            letterSpacing = jo.optDoubleOrNull("letterSpacing")?.toFloat(),
            maxLines = jo.optIntOrNull("maxLines"),
            ellipsize = jo.optBoolean("ellipsize", true),
            cornerRadius = jo.optInt("cornerRadius", 0),
            circle = jo.optBoolean("circle", false),
            scaleType = ScaleType.from(jo.optString("scaleType")),
            shadow = parseShadow(jo.optJSONObject("shadow")),
            qr = parseQr(jo.optJSONObject("qr")),
            editable = parseEditable(jo.opt("editable")),
            // 组合元素：子元素递归解析（皮肤可以靠它拼出引擎里没有的"新类型"）
            children = parseElements(jo.optJSONArray("children")),
            orientation = GroupOrientation.from(jo.optString("orientation")),
            gap = jo.optInt("gap", 24),
            crossAlign = jo.optStringOrNull("crossAlign")?.let { HAlign.from(it) },
        )
    }

    private fun parsePosX(jo: JSONObject): PosX {
        val raw = jo.opt("x")
        return when (raw) {
            null -> PosX(HAlign.LEFT, jo.optInt("marginLeft", 0))
            is Number -> PosX(HAlign.LEFT, raw.toInt())
            is String -> when (raw.trim().lowercase()) {
                "center", "centre" -> PosX(HAlign.CENTER, 0)
                "right", "end" -> PosX(HAlign.RIGHT, 0)
                else -> PosX(HAlign.LEFT, raw.toIntOrNull() ?: 0)
            }
            else -> PosX()
        }
    }

    private fun parsePosY(jo: JSONObject): PosY {
        val raw = jo.opt("y")
        return when (raw) {
            null -> PosY(VMode.FLOW, 0)
            is Number -> PosY(VMode.TOP, raw.toInt())
            is String -> when (raw.trim().lowercase()) {
                "bottom" -> PosY(VMode.BOTTOM, 0)
                "center", "centre", "middle" -> PosY(VMode.CENTER, 0)
                "flow", "auto" -> PosY(VMode.FLOW, 0)
                else -> PosY(VMode.TOP, raw.toIntOrNull() ?: 0)
            }
            else -> PosY()
        }
    }

    /** 支持 margin:{l,t,r,b} 与扁平的 marginLeft/marginTop/... 两种写法。 */
    private fun parseFlatMargins(jo: JSONObject) = Insets(
        left = jo.optInt("marginLeft", 0),
        top = jo.optInt("marginTop", 0),
        right = jo.optInt("marginRight", 0),
        bottom = jo.optInt("marginBottom", 0),
    )

    private fun parseInsets(jo: JSONObject?): Insets? {
        if (jo == null) return null
        return Insets(
            left = jo.optInt("left", jo.optInt("l", 0)),
            top = jo.optInt("top", jo.optInt("t", 0)),
            right = jo.optInt("right", jo.optInt("r", 0)),
            bottom = jo.optInt("bottom", jo.optInt("b", 0)),
        )
    }

    private fun parseFont(jo: JSONObject?): FontSpec? {
        if (jo == null) return null
        return FontSpec(
            source = FontSource.from(jo.optString("source")),
            value = jo.optStringOrNull("value"),
            bold = jo.optBoolean("bold", false),
            italic = jo.optBoolean("italic", false),
        )
    }

    private fun parseShadow(jo: JSONObject?): ShadowSpec? {
        if (jo == null) return null
        return ShadowSpec(
            radius = jo.optDouble("radius", 0.0).toFloat(),
            dx = jo.optDouble("dx", 0.0).toFloat(),
            dy = jo.optDouble("dy", 0.0).toFloat(),
            color = parseColor(jo.opt("color"), jo.optDoubleOrNull("opacity"))
                ?: Color.argb(64, 0, 0, 0),
        )
    }

    private fun parseQr(jo: JSONObject?): QrSpec {
        if (jo == null) return QrSpec()
        return QrSpec(
            errorCorrection = jo.optString("errorCorrection", "M").uppercase().take(1),
            quietZone = jo.optInt("quietZone", 1),
            backgroundColor = parseColor(jo.opt("backgroundColor")) ?: Color.WHITE,
            logo = jo.optStringOrNull("logo"),
            logoUseCover = jo.optBoolean("logoUseCover", false),
            logoScale = jo.optDouble("logoScale", 0.2).toFloat().coerceIn(0f, 0.3f),
            label = jo.optStringOrNull("label"),
        )
    }

    /**
     * 「用户可编辑」声明。
     * `"editable": true` 简写 = 全开；对象 = 逐项开关；不写 / false = 不可编辑（界面上不出现入口）。
     */
    /** `"songInfo": "besideQr"` 或 `{"mode": "besideQr"}` 两种写法都支持。 */
    private fun parseSongInfo(node: Any?): SongInfoSpec = when (node) {
        null -> SongInfoSpec()
        is String -> SongInfoSpec(SongInfoMode.from(node))
        is JSONObject -> SongInfoSpec(SongInfoMode.from(node.optString("mode")))
        else -> SongInfoSpec()
    }

    private fun parseEditable(node: Any?): EditableSpec? = when (node) {
        null -> null
        is Boolean -> if (node) EditableSpec.ALL else null
        is JSONObject -> {
            if (!node.optBoolean("enabled", true)) {
                null
            } else {
                EditableSpec(
                    id = node.optStringOrNull("id"),
                    order = node.optIntOrNull("order"),
                    label = node.optStringOrNull("label"),
                    replace = node.optBoolean("replace", true),
                    visibility = node.optBoolean("visibility", true),
                    shape = node.optBoolean("shape", true),
                    shapes = parseShapes(node.optJSONArray("shapes")),
                    size = node.optBoolean("size", true),
                    sizeRange = parseFloatRange(node.optJSONArray("sizeRange"), EditableSpec.ALL.sizeRange),
                    move = node.optBoolean("move", true),
                    moveRange = node.optDouble("moveRange", 0.25).toFloat().coerceIn(0f, 0.5f),
                    color = node.optBoolean("color", false),
                    background = node.optBoolean("background", false),
                    font = node.optBoolean("font", false),
                    controls = parseControls(node.optJSONArray("controls")),
                )
            }
        }
        else -> null
    }

    /** 控件表：一条 = 一个可调属性 + 用哪种控件（皮肤完全自由地决定给用户开放什么）。 */
    private fun parseControls(arr: JSONArray?): List<ControlSpec> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val jo = arr.optJSONObject(i) ?: return@mapNotNull null
            val property = EditProperty.from(jo.optString("property")) ?: return@mapNotNull null
            ControlSpec(
                property = property,
                kind = ControlKind.from(jo.optString("control")) ?: ControlKind.forProperty(property),
                label = jo.optStringOrNull("label"),
                range = parseFloatRange(jo.optJSONArray("range"), emptyList()),
                options = (jo.optJSONArray("options")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()),
                allowTransparent = jo.optBoolean("allowTransparent", false),
                allowNone = jo.optBoolean("allowNone", true),
            )
        }
    }

    private fun parseShapes(arr: JSONArray?): List<ImageShape> {
        if (arr == null) return EditableSpec.ALL.shapes
        val shapes = (0 until arr.length())
            .map { ImageShape.from(arr.optString(it)) }
            .filter { it != ImageShape.DEFAULT }
            .distinct()
        return shapes.ifEmpty { EditableSpec.ALL.shapes }
    }

    private fun parseFloatRange(arr: JSONArray?, fallback: List<Float>): List<Float> {
        if (arr == null || arr.length() < 2) return fallback
        val lo = arr.optDouble(0).toFloat()
        val hi = arr.optDouble(1).toFloat()
        return if (lo > 0f && hi > lo) listOf(lo, hi) else fallback
    }

    // ----------------------------------------------------------------- utils

    /**
     * 颜色解析。
     *
     * **推荐写法是 `#RRGGBB`（不透明）** —— 出图不需要 alpha 通道，皮肤里就别写 8 位了。
     * 需要半透明的地方（压暗层 scrim、阴影）用一个独立的 `opacity` 字段，值为 0~1：
     *
     * ```jsonc
     * "scrim":  { "color": "#000000", "opacity": 0.7 }
     * "shadow": { "color": "#000000", "opacity": 0.15, "radius": 20, "dy": 8 }
     * ```
     *
     * 也兼容 `#AARRGGBB` 旧写法（alpha 直接写进颜色），两者可叠加；`"none"`/`"transparent"` 表示全透明。
     *
     * @param opacity 0~1，null 表示 1（不透明）
     */
    fun parseColor(any: Any?, opacity: Double? = null): Int? {
        // 这里不写显式类型：让 `?: return null` 把类型收敛成 Int，后面 Color.red(base) 才不用再判空
        val base = when (any) {
            null -> null
            // 允许颜色本身写成对象：{"color": "#000000", "opacity": 0.5}
            is JSONObject -> parseColor(any.opt("color"), any.optDoubleOrNull("opacity"))
            is Number -> any.toInt()
            is String -> {
                val s = any.trim()
                when {
                    s.isEmpty() -> null
                    s.equals("none", true) || s.equals("transparent", true) -> Color.TRANSPARENT
                    else -> runCatching { Color.parseColor(s) }.getOrNull()
                }
            }
            else -> null
        } ?: return null

        val factor = (opacity ?: 1.0).toFloat().coerceIn(0f, 1f)
        if (factor >= 1f) return base
        return Color.argb(
            (Color.alpha(base) * factor).roundToInt().coerceIn(0, 255),
            Color.red(base),
            Color.green(base),
            Color.blue(base),
        )
    }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null
}
