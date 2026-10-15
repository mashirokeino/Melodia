package com.music.lyricshare.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.music.lyricshare.render.EditState
import com.music.lyricshare.render.ElementPlacement
import com.music.lyricshare.skin.FontSource
import com.music.lyricshare.skin.FontSpec
import com.music.lyricshare.skin.ImageShape
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * ============================================================================
 *  用户调整的持久化
 * ============================================================================
 *  需求：**这次调好的封面/背景/字体/二维码，下次进来还是调好的样子。**
 *
 *  存法（按皮肤各存一份，和界面上的"每个皮肤设置独立"一致）：
 *   · 标量（显隐 / 颜色 / 底色 / 形状 / 缩放 / 位移 / 锚点 / 字体）→ SharedPreferences 里一段 JSON；
 *   · 用户选的图片 → 单独落盘到 `filesDir/lyric_share_edits/`，JSON 里只记文件名。
 *
 *  图片按"位图实例"做记忆：同一个 Bitmap 不会重复编码写盘（拖滑杆时只有 JSON 在写），
 *  省掉每秒几十次的 JPEG 编码。
 */
class EditStateStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val imageDir: File
        get() = File(appContext.filesDir, IMAGE_DIR_NAME).apply { if (!exists()) mkdirs() }

    /** `皮肤id/项id` → 已经落盘的那张位图，用于跳过重复编码。 */
    private val savedImages = HashMap<String, Bitmap>()

    // ------------------------------------------------------------------ 读

    /** 读出某套皮肤的全部用户调整；没有存过就返回空表（= 全用皮肤声明）。 */
    fun load(skinId: String): Map<String, EditState> {
        val raw = prefs.getString(key(skinId), null) ?: return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            buildMap {
                root.keys().forEach { editId ->
                    val jo = root.optJSONObject(editId) ?: return@forEach
                    put(editId, decode(jo, skinId, editId))
                }
            }
        }.getOrElse { emptyMap() }     // 存坏了就当没存过，不影响出图
    }

    // ------------------------------------------------------------------ 写

    /** 写入某套皮肤的全部用户调整（含把用户选的图片落盘 / 清理已重置的图片）。 */
    fun save(skinId: String, edits: Map<String, EditState>) {
        // 没有任何调整、磁盘上也没存过 → 不凭空建一条空配置
        // （皮肤刚被删掉时，页面切走的兜底落盘会走到这里）
        if (edits.isEmpty() && !prefs.contains(key(skinId))) return
        val root = JSONObject()
        edits.forEach { (editId, state) ->
            root.put(editId, encode(state, skinId, editId))
        }
        prefs.edit().putString(key(skinId), root.toString()).apply()
    }

    /** 皮肤被删除时，把它的调整和图片一起清掉。 */
    fun clear(skinId: String) {
        prefs.edit().remove(key(skinId)).apply()
        runCatching {
            savedImages.keys.filter { it.startsWith("$skinId/") }.forEach { savedImages.remove(it) }
            imageDir.listFiles()?.filter { it.name.startsWith("${prefix(skinId)}") }?.forEach { it.delete() }
        }
    }

    // ------------------------------------------------------------------ 编解码

    private fun encode(state: EditState, skinId: String, editId: String): JSONObject {
        val jo = JSONObject()
        jo.put("visible", state.visible)
        state.solidColor?.let { jo.put("solidColor", it) }
        state.color?.let { jo.put("color", it) }
        state.text?.let { jo.put("text", it) }
        jo.put("shape", state.shape.name)
        jo.put("scale", state.scale.toDouble())
        jo.put("offsetX", state.offsetX.toDouble())
        jo.put("offsetY", state.offsetY.toDouble())
        jo.put("placement", state.placement.name)
        state.font?.let { font ->
            jo.put(
                "font",
                JSONObject()
                    .put("source", font.source.name)
                    .put("value", font.value)
                    .put("bold", font.bold)
                    .put("italic", font.italic),
            )
        }
        // 图片：只在换了图时重新编码写盘；重置（bitmap == null）时把旧文件删掉
        val key = "$skinId/$editId"
        val bitmap = state.bitmap
        if (bitmap != null) {
            if (savedImages[key] !== bitmap || !imageFile(skinId, editId).exists()) {
                if (writeImage(skinId, editId, bitmap)) savedImages[key] = bitmap
            }
            if (imageFile(skinId, editId).exists()) jo.put("image", imageFile(skinId, editId).name)
        } else {
            savedImages.remove(key)
            imageFile(skinId, editId).delete()
        }
        return jo
    }

    private fun decode(jo: JSONObject, skinId: String, editId: String): EditState {
        val file = imageFile(skinId, editId)
        val bitmap = if (jo.optString("image").isNotBlank() && file.exists()) {
            runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()?.also {
                savedImages["$skinId/$editId"] = it
            }
        } else {
            null
        }
        val font = jo.optJSONObject("font")?.let { f ->
            FontSpec(
                source = runCatching { FontSource.valueOf(f.optString("source")) }.getOrDefault(FontSource.DEFAULT),
                value = f.optStringOrNull("value"),
                bold = f.optBoolean("bold", false),
                italic = f.optBoolean("italic", false),
            )
        }
        return EditState(
            bitmap = bitmap,
            solidColor = jo.optIntOrNull("solidColor"),
            visible = jo.optBoolean("visible", true),
            shape = runCatching { ImageShape.valueOf(jo.optString("shape")) }.getOrDefault(ImageShape.DEFAULT),
            scale = jo.optDouble("scale", 1.0).toFloat(),
            offsetX = jo.optDouble("offsetX", 0.0).toFloat(),
            offsetY = jo.optDouble("offsetY", 0.0).toFloat(),
            color = jo.optIntOrNull("color"),
            text = jo.optStringOrNull("text"),
            font = font,
            placement = runCatching { ElementPlacement.valueOf(jo.optString("placement")) }
                .getOrDefault(ElementPlacement.SKIN),
        )
    }

    // ------------------------------------------------------------------ 图片落盘

    private fun writeImage(skinId: String, editId: String, bitmap: Bitmap): Boolean = runCatching {
        imageDir.mkdirs()
        val target = imageFile(skinId, editId)
        val hasAlpha = bitmap.hasAlpha()
        val format = if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        ByteArrayOutputStream().use { out ->
            bitmap.compress(format, 92, out)
            target.writeBytes(out.toByteArray())
        }
        true
    }.getOrDefault(false)

    private fun imageFile(skinId: String, editId: String): File =
        File(imageDir, prefix(skinId) + editId + "." + IMAGE_EXT)

    private fun prefix(skinId: String): String = "${skinId.replace(Regex("[^A-Za-z0-9_.-]"), "_")}__"

    private fun key(skinId: String): String = "edits_$skinId"

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (has(name) && !isNull(name)) optString(name) else null

    private companion object {
        const val PREFS_NAME = "lyric_share_edits"
        const val IMAGE_DIR_NAME = "lyric_share_edits"
        /** 写盘用固定扩展名；实际内容可能是 PNG 也可能是 JPEG（解码不看扩展名）。 */
        const val IMAGE_EXT = "img"
    }
}
