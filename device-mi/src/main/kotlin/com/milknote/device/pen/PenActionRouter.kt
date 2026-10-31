package com.milknote.device.pen

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private val Context.penMappingStore by preferencesDataStore(name = "pen_mapping")

/** 映射表持久化编解码(`T=A,T=A,...`;损坏输入返回 null 走默认) */
object PenMappingCodec {
    fun serialize(m: Map<PenTrigger, PenAction>): String =
        m.entries.joinToString(",") { "${it.key.name}=${it.value.name}" }

    fun parse(raw: String): Map<PenTrigger, PenAction>? = runCatching {
        raw.split(",").associate { pair ->
            val (t, a) = pair.split("=")
            PenTrigger.valueOf(t) to PenAction.valueOf(a)
        }
    }.getOrNull()
}

/**
 * 按键映射路由(PEN-ADAPTATION §4.4):
 * - `Map<PenTrigger, PenAction>` 持久化到 DataStore,device-mi 只读写、不解释语义
 * - 设置页「笔与手势」可改绑;「恢复默认」重置
 */
class PenActionRouter(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val store = context.penMappingStore

    private val _mapping = MutableStateFlow(DEFAULT_MAPPING)
    val mapping: StateFlow<Map<PenTrigger, PenAction>> = _mapping

    init {
        scope.launch {
            store.data.collect { prefs ->
                prefs[MAPPING_KEY]?.let { raw ->
                    PenMappingCodec.parse(raw)?.let { _mapping.value = it }
                }
            }
        }
    }

    fun actionOf(trigger: PenTrigger): PenAction = _mapping.value[trigger] ?: PenAction.NONE

    /** 改绑;持久化失败仅保内存 */
    fun rebind(trigger: PenTrigger, action: PenAction) {
        _mapping.value = _mapping.value + (trigger to action)
        scope.launch {
            store.edit { it[MAPPING_KEY] = PenMappingCodec.serialize(_mapping.value) }
        }
    }

    fun resetDefault() {
        _mapping.value = DEFAULT_MAPPING
        scope.launch { store.edit { it[MAPPING_KEY] = PenMappingCodec.serialize(DEFAULT_MAPPING) } }
    }

    companion object {
        private val MAPPING_KEY = stringPreferencesKey("mapping_v1")
    }
}
