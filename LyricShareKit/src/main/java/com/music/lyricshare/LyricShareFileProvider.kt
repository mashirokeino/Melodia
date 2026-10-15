package com.music.lyricshare

import androidx.core.content.FileProvider

/**
 * 独立的 FileProvider 子类，避免宿主工程同样声明 androidx.core.content.FileProvider 时发生 Manifest 合并冲突。
 */
class LyricShareFileProvider : FileProvider()
