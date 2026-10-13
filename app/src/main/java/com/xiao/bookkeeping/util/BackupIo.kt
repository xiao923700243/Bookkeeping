package com.xiao.bookkeeping.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * 备份文件的读写。整个工程里唯一碰 ContentResolver 的地方。
 *
 * 走 SAF（系统文件选择器）：用户选中的文件由系统按 URI 一次性授权给本应用，
 * 所以既不需要存储权限，也不用在 manifest 里声明任何东西。
 */
object BackupIo {

    /**
     * 备份文件的体积上限。
     *
     * 个人账本导出的 JSON 撑死几百 KB，8MB 已经极宽松。导入的选择器是不限 MIME 类型打开的
     * （见 MainActivity），用户手滑点到一个视频，这里就是最后一道闸。
     */
    const val MAX_BYTES = 8L * 1024 * 1024

    /** 文件超过 [MAX_BYTES]。单独分出来，好让提示是「文件过大」而不是笼统的「文件损坏」。 */
    class TooLargeException : IOException("backup file is larger than $MAX_BYTES bytes")

    /** 读成 UTF-8 文本。 */
    fun readText(resolver: ContentResolver, uri: Uri): String {
        // 先问 provider 要大小。不少 provider 不报这一列，所以下面读取时还要再兜一道
        sizeOf(resolver, uri)?.let { if (it > MAX_BYTES) throw TooLargeException() }

        val input = resolver.openInputStream(uri) ?: throw IOException("no input stream for $uri")
        return input.use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            val out = ByteArrayOutputStream()
            var total = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_BYTES) throw TooLargeException()
                out.write(buffer, 0, read)
            }
            out.toByteArray().toString(Charsets.UTF_8)
        }
    }

    /**
     * 写 UTF-8 文本。
     *
     * 模式必须是 `wt`：光写 `w` 不带 truncate，往一份长备份上覆盖短内容时只写前面一段，
     * 后面留着旧文件的尾巴，读回来就是坏 JSON。写字节而不是套 Writer，编码说死了更省心。
     */
    fun writeText(resolver: ContentResolver, uri: Uri, text: String) {
        val output = resolver.openOutputStream(uri, "wt")
            ?: throw IOException("no output stream for $uri")
        output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    /** 文件在选择器里显示的名字，取不到就退回 URI 末段。 */
    fun displayName(resolver: ContentResolver, uri: Uri): String {
        // 名字只用来在确认弹窗里给用户看一眼，取不到不值得让整次导入失败
        val name = runCatching { queryName(resolver, uri) }.getOrNull()
        return name ?: uri.lastPathSegment.orEmpty()
    }

    private fun queryName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
        }

    /** provider 不报大小时返回 null——拿不到大小不是错误，读取时还有一道限制。 */
    private fun sizeOf(resolver: ContentResolver, uri: Uri): Long? =
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && index >= 0 && !cursor.isNull(index)) {
                cursor.getLong(index)
            } else {
                null
            }
        }
}
