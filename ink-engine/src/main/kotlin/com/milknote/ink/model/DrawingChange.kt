package com.milknote.ink.model

/**
 * 统一绘图变更(供落盘层按「每 UI Batch 一次」记录):
 * - [Apply] 正向动作;批量为一个不可分割的记录。
 * - [Undo]/[Redo] 只记录动作码,内容与栈由操作日志回放重建。
 */
sealed interface DrawingChange {
    data class Apply(val op: InkOp) : DrawingChange
    data object Undo : DrawingChange
    data object Redo : DrawingChange
}
