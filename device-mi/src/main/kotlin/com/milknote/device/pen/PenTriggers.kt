package com.milknote.device.pen

/**
 * 笔触发器(PEN-ADAPTATION §4.4 映射表)。
 * 焦点键通路未验证(P1),占位;PRO_* 为焦点笔 Pro 触控区(前瞻)。
 */
enum class PenTrigger(val label: String) {
    PRIMARY_TAP("主键单击"),
    PRIMARY_DOUBLE("主键双击"),
    PRIMARY_LONG("主键长按"),
    PRIMARY_HOLD_WRITE("主键按住书写"),
    SECONDARY_TAP("副键单击"),
    SECONDARY_HOLD_WRITE("副键按住书写"),
    FOCUS_KEY("焦点键(待验证)"),
    PRO_PINCH("Pro 轻捏(前瞻)"),
    PRO_DOUBLE_TAP("Pro 双击(前瞻)"),
    PRO_SLIDE_UP("Pro 上滑(前瞻)"),
    PRO_SLIDE_DOWN("Pro 下滑(前瞻)"),
}

/** 可绑定动作 */
enum class PenAction(val label: String) {
    TOGGLE_ERASER("切换笔⇄橡皮"),
    LASO("套索选择"),
    SNIP("框选截图"),
    UNDO("撤销"),
    REDO("重做"),
    NEW_PAGE("新建页"),
    TEMP_ERASER("临时橡皮"),
    NONE("无"),
}

/**
 * 默认映射(PEN-ADAPTATION §4.4 与 DESIGN-V0 §3.2 对齐)。
 * 2026-11-01 修订:系统长按行为已被 gb_boosting 接管方案压制(书写页写
 * Secure 表,PEN-ADAPTATION §4.1-3),PRIMARY_LONG 恢复 SNIP ——
 * 笔键长按 = 应用内框选截图,即 DESIGN-V0 §4.1 的核心交互(M2 落地)。
 */
val DEFAULT_MAPPING: Map<PenTrigger, PenAction> = mapOf(
    PenTrigger.PRIMARY_TAP to PenAction.LASO,
    PenTrigger.PRIMARY_DOUBLE to PenAction.TOGGLE_ERASER,
    PenTrigger.PRIMARY_LONG to PenAction.SNIP,
    PenTrigger.PRIMARY_HOLD_WRITE to PenAction.LASO,
    PenTrigger.SECONDARY_TAP to PenAction.UNDO,
    PenTrigger.SECONDARY_HOLD_WRITE to PenAction.TEMP_ERASER,
    PenTrigger.FOCUS_KEY to PenAction.NONE,
    PenTrigger.PRO_PINCH to PenAction.TOGGLE_ERASER,
    PenTrigger.PRO_DOUBLE_TAP to PenAction.NONE,
    PenTrigger.PRO_SLIDE_UP to PenAction.NONE,
    PenTrigger.PRO_SLIDE_DOWN to PenAction.NONE,
)
