package com.music.lyricshare.util

import java.io.File
import java.util.zip.ZipInputStream

/** 极简解压工具（带 zip-slip 防护），用于安装下载下来的皮肤包。 */
internal object ZipUtils {

    /**
     * 把 [zipFile] 解压到 [destDir]。
     * @return 解压出的文件数量；为 0 视为非法包。
     */
    fun extract(zipFile: File, destDir: File): Int {
        if (!destDir.exists() && !destDir.mkdirs()) return 0
        val destPath = destDir.canonicalPath + File.separator
        var count = 0
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name.contains("..") || name.startsWith("/")) {
                    zis.closeEntry(); entry = zis.nextEntry; continue  // 拒绝穿越路径
                }
                val out = File(destDir, name)
                if (!out.canonicalPath.startsWith(destPath)) {
                    zis.closeEntry(); entry = zis.nextEntry; continue
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().buffered().use { zis.copyTo(it) }
                    count++
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return count
    }

    /** 递归删除目录。 */
    fun deleteRecursively(dir: File) {
        runCatching { dir.deleteRecursively() }
    }
}
