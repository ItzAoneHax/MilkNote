package com.milknote.device.pen

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 笔事件流水(PEN-ADAPTATION §3.5 诊断面板的数据源,亦是 §7-P1 真机验证通道):
 * 实时打印 ①buttonState 变化 ②dispatchKeyEvent keyCode ③所有 motion 事件摘要。
 */
class PenEventLog(private val capacity: Int = 300) {

    data class Entry(val timeMs: Long, val text: String)

    private val buffer = ArrayDeque<Entry>(capacity)
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    @Volatile var enabled: Boolean = true

    fun log(text: String) {
        if (!enabled) return
        synchronized(buffer) {
            if (buffer.size >= capacity) buffer.removeFirst()
            buffer.addLast(Entry(System.currentTimeMillis(), text))
            _entries.value = buffer.toList()
        }
    }

    fun clear() = synchronized(buffer) { buffer.clear(); _entries.value = emptyList() }
}
