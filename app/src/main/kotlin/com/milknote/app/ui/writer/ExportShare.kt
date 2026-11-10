package com.milknote.app.ui.writer

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

/** 系统 Sharesheet:单文件 ACTION_SEND / 多文件 ACTION_SEND_MULTIPLE,并显式授予读权限。 */
object ExportShare {

    fun share(context: Context, uris: List<Uri>, mimeType: String) {
        if (uris.isEmpty()) return
        val stream = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        stream.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        stream.clipData = ClipData.newUri(context.contentResolver, "MilkNote 导出", uris.first()).apply {
            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        val chooser = Intent.createChooser(stream, "分享导出文件")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
    }
}
