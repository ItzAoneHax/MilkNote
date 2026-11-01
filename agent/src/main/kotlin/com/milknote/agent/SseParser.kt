package com.milknote.agent

/**
 * SSE 流式解析(AGENT-BACKEND §3.5,okhttp-sse 的零依赖替代):
 * - 事件以空行分隔;`data:` 行逐条累积(多 data 行以 \n 连接)
 * - `event:` / `id:` / `retry:` / `:注释` 忽略;CR 剥离
 * - 容错:半行跨 chunk 缓冲;尾包无空行时 finish() 兜底冲刷
 */
class SseParser {

    private val lineBuf = StringBuilder()
    private val dataBuf = StringBuilder()
    private var dataLines = 0

    /** 喂入一段文本(任意切割);返回其中所有完整事件的 data 载荷 */
    fun feed(chunk: String): List<String> {
        val out = ArrayList<String>()
        for (c in chunk) {
            if (c == '\n') {
                feedLine(lineBuf.toString(), out)
                lineBuf.setLength(0)
            } else if (c != '\r') {
                lineBuf.append(c)
            }
        }
        return out
    }

    /** 流结束:冲刷未换行的尾巴与未闭合事件 */
    fun finish(): List<String> {
        val out = ArrayList<String>()
        if (lineBuf.isNotEmpty()) {
            feedLine(lineBuf.toString(), out)
            lineBuf.setLength(0)
        }
        if (dataLines > 0) {
            out.add(dataBuf.toString())
            dataBuf.setLength(0)
            dataLines = 0
        }
        return out
    }

    private fun feedLine(line: String, out: MutableList<String>) {
        when {
            line.isEmpty() -> {
                // 空行 = 事件边界
                if (dataLines > 0) {
                    out.add(dataBuf.toString())
                    dataBuf.setLength(0)
                    dataLines = 0
                }
            }
            line.startsWith("data:") -> {
                var payload = line.substring(5)
                if (payload.startsWith(" ")) payload = payload.substring(1)
                if (dataLines > 0) dataBuf.append('\n')
                dataBuf.append(payload)
                dataLines++
            }
            // event:/id:/retry:/注释行 → 忽略
        }
    }
}
