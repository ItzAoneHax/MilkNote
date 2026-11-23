package com.milknote.app.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.milknote.ink.model.PressureGamma
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "app_settings")

/** 外观模式 */
enum class ThemeMode(val label: String) {
    FOLLOW("跟随系统"), LIGHT("浅色"), DARK("深色");
}

/** 应用设置(设置页的数据面) */
data class AppSettings(
    val frontBufferInk: Boolean = false,   // 前置缓冲出墨(实验性,默认关)
    val pressureGamma: PressureGamma = PressureGamma.STANDARD,
    val btPermissionAsked: Boolean = false, // 蓝牙权限只自动弹一次
    val themeMode: ThemeMode = ThemeMode.FOLLOW,
    val updateServer: String = "",         // 局域网更新服务地址(./gradlew :app:serveUpdates)
    val defaultPaper: String = "dot",      // 新建笔记本默认纸纹
    val defaultPaperColor: String = "#FFFFFF", // 新建笔记本默认纸色
    val bookshelfListView: Boolean = false,    // 书架默认视图:false 网格 / true 列表
    val trashDays: Int = 30,               // 回收站保留天数;0 = 永久保留
    val swipeNewPage: Boolean = true,      // 末页继续右滑自动新建页
    val penPresets: String = "",           // 笔盒收藏(编码字符串,见 WriterToolSettings.PenPreset)
)

/**
 * 设置持久化(DataStore Preferences)。
 * 压感曲线档作用于渲染(笔宽是样式),换档后全页笔迹按新档重绘。
 */
class AppSettingsStore(context: Context) {

    private val store = context.settingsStore

    val flow: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            frontBufferInk = prefs[KEY_FRONT_BUFFER] ?: false,
            pressureGamma = prefs[KEY_GAMMA]?.let { runCatching { PressureGamma.valueOf(it) }.getOrNull() }
                ?: PressureGamma.STANDARD,
            btPermissionAsked = prefs[KEY_BT_ASKED] ?: false,
            themeMode = prefs[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.FOLLOW,
            updateServer = prefs[KEY_UPDATE_SERVER] ?: "",
            defaultPaper = prefs[KEY_DEFAULT_PAPER] ?: "dot",
            defaultPaperColor = prefs[KEY_DEFAULT_PAPER_COLOR] ?: "#FFFFFF",
            bookshelfListView = prefs[KEY_BOOKSHELF_LIST] ?: false,
            trashDays = prefs[KEY_TRASH_DAYS] ?: 30,
            swipeNewPage = prefs[KEY_SWIPE_NEW_PAGE] ?: true,
            penPresets = prefs[KEY_PEN_PRESETS] ?: "",
        )
    }

    suspend fun setUpdateServer(addr: String) {
        store.edit { it[KEY_UPDATE_SERVER] = addr }
    }

    suspend fun setFrontBufferInk(enabled: Boolean) {
        store.edit { it[KEY_FRONT_BUFFER] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setPressureGamma(gamma: PressureGamma) {
        store.edit { it[KEY_GAMMA] = gamma.name }
    }

    suspend fun setBtPermissionAsked() {
        store.edit { it[KEY_BT_ASKED] = true }
    }

    suspend fun setDefaultPaper(paper: String) {
        store.edit { it[KEY_DEFAULT_PAPER] = paper }
    }

    suspend fun setDefaultPaperColor(hex: String) {
        store.edit { it[KEY_DEFAULT_PAPER_COLOR] = hex }
    }

    suspend fun setBookshelfListView(list: Boolean) {
        store.edit { it[KEY_BOOKSHELF_LIST] = list }
    }

    suspend fun setTrashDays(days: Int) {
        store.edit { it[KEY_TRASH_DAYS] = days.coerceAtLeast(0) }
    }

    suspend fun setSwipeNewPage(enabled: Boolean) {
        store.edit { it[KEY_SWIPE_NEW_PAGE] = enabled }
    }

    suspend fun setPenPresets(encoded: String) {
        store.edit { it[KEY_PEN_PRESETS] = encoded }
    }

    private companion object {
        val KEY_FRONT_BUFFER = booleanPreferencesKey("front_buffer_ink")
        val KEY_GAMMA = stringPreferencesKey("pressure_gamma")
        val KEY_BT_ASKED = booleanPreferencesKey("bt_permission_asked")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_UPDATE_SERVER = stringPreferencesKey("update_server")
        val KEY_DEFAULT_PAPER = stringPreferencesKey("default_paper")
        val KEY_DEFAULT_PAPER_COLOR = stringPreferencesKey("default_paper_color")
        val KEY_BOOKSHELF_LIST = booleanPreferencesKey("bookshelf_list_view")
        val KEY_TRASH_DAYS = intPreferencesKey("trash_days")
        val KEY_SWIPE_NEW_PAGE = booleanPreferencesKey("swipe_new_page")
        val KEY_PEN_PRESETS = stringPreferencesKey("pen_presets")
    }
}
