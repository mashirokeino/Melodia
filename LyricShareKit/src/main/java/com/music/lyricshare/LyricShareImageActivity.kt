package com.music.lyricshare

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.music.lyricshare.ui.LyricShareImageScreen
import com.music.lyricshare.ui.LyricShareTheme
import com.music.lyricshare.ui.LyricShareTokens

/**
 * 歌词图片生成页的宿主 Activity。
 *
 * 只做三件事：edge-to-edge、解析 Intent 里的 [LyricShareRequest]、把 Compose 页面挂上去。
 * 所有业务逻辑都在 [LyricShareImageScreen] 里 —— 如果你想把这一页塞进自己的
 * Compose Navigation 图里，直接调用那个 Composable 即可，不必经过 Activity。
 */
class LyricShareImageActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 沉浸式：状态栏/导航栏全透明 + 浅色图标，页面内容（#101014 底色）直接铺到状态栏下面。
        //
        // 为什么不用无参 enableEdgeToEdge()：它内部用 SystemBarStyle.auto(...)，
        // 会跟随**系统**明暗模式决定状态栏遮罩色 —— 系统是浅色时会给状态栏刷一层浅色 scrim，
        // 与这个深色页面拼在一起就是一条突兀的色带。这里显式指定 dark（透明 + 浅色图标）。
        // 说明：Android 15+ (API 35+) 已忽略 statusBarColor，边到边是强制的，
        // 但下面这次调用在低版本上依然是必要的。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val request = LyricShareRequest.from(intent)
        setContent {
            // 用模块自带主题包一层：MaterialTheme 与 LocalLyricShareTokens 同源，
            // 不会出现"按钮是 Material 默认紫、卡片是品牌红"的割裂。
            // 页面底色跟随播放页取色（宿主通过 request.baseColor 传进来），保持色彩一致。
            LyricShareTheme(tokens = LyricShareTokens.forBackdrop(request.baseColor)) {
                LyricShareImageScreen(
                    request = request,
                    onBack = { finish() },
                )
            }
        }
    }
}

/**
 * 保存到相册在 API 28 及以下需要写外部存储权限（API 29+ 走 MediaStore，无需权限）。
 * 抽成小工具，页面里就不用到处写版本判断。
 */
internal object LegacyStoragePermission {

    const val PERMISSION = Manifest.permission.WRITE_EXTERNAL_STORAGE

    fun required(): Boolean = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P

    fun granted(context: Context): Boolean = !required() ||
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED
}

/** 统一轻提示：与 Melodia 现有交互（复制歌词的 Toast）保持一致。 */
internal fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
