package com.milknote.app.ui.writer

/**
 * 轻量结构化公式编辑模型(纯 Kotlin):
 * - 原始 LaTeX 只作为内部状态,UI 用 LatexNativeFormula 排版展示;
 * - 模板插入后光标停在第一个 \square 占位符,下一次输入直接填充;
 * - 键盘 `/` 优先把前一个原子变成分式;退格按命令/括号组/占位符原子删除。
 */
data class FormulaInputState(
    val latex: String,
    val cursor: Int = latex.length,
) {
    companion object {
        fun of(latex: String, cursor: Int = latex.length): FormulaInputState =
            FormulaInputState(latex, cursor.coerceIn(0, latex.length))
    }
}

enum class FormulaTemplate(val latex: String) {
    FRACTION("\\frac{\\square}{\\square}"),
    SQRT("\\sqrt{\\square}"),
    SQUARE("^{2}"),
    PI("\\pi"),
    PARENS("(\\square)"),
    SIN("\\sin(\\square)"),
    COS("\\cos(\\square)"),
    TAN("\\tan(\\square)"),
    LN("\\ln(\\square)"),
    LOG("\\log(\\square)"),
    ABS("|\\square|"),
}

object FormulaInputEditor {

    const val PLACEHOLDER = "\\square"
    private const val PLACEHOLDER_LEN = 7

    /** 按钮/软键盘输入一个模板 */
    fun insertTemplate(state: FormulaInputState, template: FormulaTemplate): FormulaInputState =
        insertTemplate(state, template.latex)

    fun insertTemplate(state: FormulaInputState, template: String): FormulaInputState {
        val at = state.cursor.coerceIn(0, state.latex.length)
        val base = if (placeholderAt(state.latex, at)) {
            state.latex.removeRange(at, at + PLACEHOLDER_LEN)
        } else {
            state.latex
        }
        val text = base.substring(0, at) + template + base.substring(at)
        val firstSquare = template.indexOf(PLACEHOLDER)
        val cursor = if (firstSquare >= 0) at + firstSquare else at + template.length
        return FormulaInputState(text, cursor.coerceIn(0, text.length))
    }

    /** 普通键盘文字;单个 `/` 转为分式,占位符位置直接填入 */
    fun insertText(state: FormulaInputState, inserted: String): FormulaInputState {
        if (inserted.isEmpty()) return state
        if (inserted == "/") return insertFraction(state)
        val at = state.cursor.coerceIn(0, state.latex.length)
        val text = state.latex
        if (placeholderAt(text, at)) {
            val next = text.removeRange(at, at + PLACEHOLDER_LEN)
                .let { it.substring(0, at) + inserted + it.substring(at) }
            return FormulaInputState(next, (at + inserted.length).coerceIn(0, next.length))
        }
        val next = text.substring(0, at) + inserted + text.substring(at)
        return FormulaInputState(next, (at + inserted.length).coerceIn(0, next.length))
    }

    /**
     * `/`:若光标前是一个完整原子,把它变成分子;否则插入空分式。
     * 两种结果光标都落在分母占位符,继续输入即填分母。
     */
    fun insertFraction(state: FormulaInputState): FormulaInputState {
        val at = state.cursor.coerceIn(0, state.latex.length)
        val start = atomStartBefore(state.latex, at)
        val atom = state.latex.substring(start, at)
        if (atom.isBlank() || atom == PLACEHOLDER || isOperator(atom)) {
            return insertTemplate(FormulaInputState(state.latex, at), FormulaTemplate.FRACTION)
        }
        val replacement = "\\frac{$atom}{\\square}"
        val text = state.latex.substring(0, start) + replacement + state.latex.substring(at)
        val denominator = start + "\\frac{$atom}{".length
        return FormulaInputState(text, denominator.coerceIn(0, text.length))
    }

    /** 退格:删除光标前的原子(命令、括号组、占位符或单字符) */
    fun backspace(state: FormulaInputState): FormulaInputState = backspaceAt(state, state.cursor)

    fun backspaceAt(state: FormulaInputState, at: Int): FormulaInputState {
        val cursor = at.coerceIn(0, state.latex.length)
        if (cursor <= 0) return state
        val start = atomStartBefore(state.latex, cursor)
        val text = state.latex.removeRange(start, cursor)
        return FormulaInputState(text, start.coerceIn(0, text.length))
    }

    /** 前向删除:删除光标后的原子 */
    fun deleteForward(state: FormulaInputState): FormulaInputState {
        val cursor = state.cursor.coerceIn(0, state.latex.length)
        if (cursor >= state.latex.length) return state
        val end = atomEndAfter(state.latex, cursor)
        val text = state.latex.removeRange(cursor, end)
        return FormulaInputState(text, cursor.coerceIn(0, text.length))
    }

    fun moveLeft(state: FormulaInputState): FormulaInputState {
        val cursor = state.cursor.coerceIn(0, state.latex.length)
        if (cursor <= 0) return state
        return state.copy(cursor = atomStartBefore(state.latex, cursor))
    }

    fun moveRight(state: FormulaInputState): FormulaInputState {
        val cursor = state.cursor.coerceIn(0, state.latex.length)
        if (cursor >= state.latex.length) return state
        return state.copy(cursor = atomEndAfter(state.latex, cursor))
    }

    /**
     * 隐藏 BasicTextField 的变更 → 结构化状态。
     * 单字符删除走原子退格;多字符删除(选区)按实际区间删除;插入 `/` 转分式。
     */
    fun onFieldChange(
        state: FormulaInputState,
        newText: String,
        newCursor: Int,
    ): FormulaInputState {
        if (newText == state.latex) {
            return state.copy(cursor = newCursor.coerceIn(0, newText.length))
        }
        val old = state.latex
        val maxCommon = minOf(old.length, newText.length)
        var prefix = 0
        while (prefix < maxCommon && old[prefix] == newText[prefix]) prefix++
        var suffix = 0
        while (
            suffix < maxCommon - prefix &&
            old[old.length - 1 - suffix] == newText[newText.length - 1 - suffix]
        ) {
            suffix++
        }
        val removedStart = prefix
        val removedEnd = old.length - suffix
        val inserted = newText.substring(prefix, newText.length - suffix)

        if (inserted.isNotEmpty()) {
            val base = if (removedStart < removedEnd) {
                old.removeRange(removedStart, removedEnd)
            } else {
                old
            }
            val at = removedStart.coerceIn(0, base.length)
            val after = if (inserted == "/") {
                insertFraction(FormulaInputState(base, at))
            } else {
                insertText(FormulaInputState(base, at), inserted)
            }
            return after
        }

        if (removedStart < removedEnd) {
            if (removedEnd - removedStart == 1) {
                val deletedAt = removedStart
                val baseCursor = state.cursor.coerceIn(0, old.length)
                return when {
                    baseCursor >= removedEnd -> backspaceAt(state, removedEnd)
                    baseCursor <= deletedAt -> FormulaInputState(
                        old.removeRange(deletedAt, removedEnd),
                        deletedAt,
                    )
                    else -> FormulaInputState(old.removeRange(deletedAt, removedEnd), deletedAt)
                }
            }
            val text = old.removeRange(removedStart, removedEnd)
            return FormulaInputState(text, removedStart.coerceIn(0, text.length))
        }

        return state.copy(cursor = newCursor.coerceIn(0, old.length))
    }

    private fun placeholderAt(text: String, index: Int): Boolean =
        index >= 0 && index + PLACEHOLDER_LEN <= text.length &&
            text.regionMatches(index, PLACEHOLDER, 0, PLACEHOLDER_LEN)

    private fun isOperator(atom: String): Boolean =
        atom.length == 1 && atom[0] in "+-*/^=(),[]{}|"

    /** 光标前一个完整原子的起点 */
    internal fun atomStartBefore(text: String, cursor: Int): Int {
        if (cursor <= 0) return 0
        if (cursor >= PLACEHOLDER_LEN && text.regionMatches(cursor - PLACEHOLDER_LEN, PLACEHOLDER, 0, PLACEHOLDER_LEN)) {
            return cursor - PLACEHOLDER_LEN
        }
        val before = text[cursor - 1]
        if (before in charArrayOf(')', ']', '}', '|')) {
            val open = matchingOpen(text, cursor - 1, before) ?: return cursor - 1
            return atomStartIncludingReceiver(text, open)
        }
        if (before.isLetterOrDigit() || before == '_') {
            var i = cursor - 1
            while (i >= 0 && (text[i].isLetterOrDigit() || text[i] == '_')) i--
            return if (i >= 0 && text[i] == '\\') i else cursor - 1
        }
        return cursor - 1
    }

    /** 光标后一个完整原子的终点 */
    internal fun atomEndAfter(text: String, cursor: Int): Int {
        if (cursor >= text.length) return text.length
        if (placeholderAt(text, cursor)) return cursor + PLACEHOLDER_LEN
        val c = text[cursor]
        if (c == '\\') {
            var i = cursor + 1
            while (i < text.length && text[i].isLetter()) i++
            var end = i
            if (end < text.length && text[end] in charArrayOf('{', '[', '(')) {
                end = matchingClose(text, end, text[end]) ?: end
            }
            return end
        }
        if (c in charArrayOf('{', '[', '(')) {
            return matchingClose(text, cursor, c) ?: cursor + 1
        }
        if (c == '|') {
            val next = text.indexOf('|', cursor + 1)
            return if (next > cursor) next + 1 else cursor + 1
        }
        return cursor + 1
    }

    /** 找到括号闭合后的下一个位置 */
    private fun matchingClose(text: String, openIndex: Int, open: Char): Int? {
        if (open == '|') {
            var i = openIndex + 1
            while (i < text.length) {
                if (text[i] == '|' && (i == 0 || text[i - 1] != '\\')) return i + 1
                i++
            }
            return null
        }
        val close = when (open) {
            '(' -> ')'
            '[' -> ']'
            '{' -> '}'
            else -> return null
        }
        var depth = 0
        for (i in openIndex until text.length) {
            if (text[i] == open) depth++
            if (text[i] == close) {
                depth--
                if (depth == 0) return i + 1
            }
        }
        return null
    }

    /** 找到与 closeIndex 配对的左括号位置 */
    private fun matchingOpen(text: String, closeIndex: Int, close: Char): Int? {
        if (close == '|') {
            var i = closeIndex - 1
            while (i >= 0) {
                if (text[i] == '|' && (i == 0 || text[i - 1] != '\\')) return i
                i--
            }
            return null
        }
        val open = when (close) {
            ')' -> '('
            ']' -> '['
            '}' -> '{'
            else -> return null
        }
        var depth = 0
        for (i in closeIndex downTo 0) {
            if (text[i] == close) depth++
            if (text[i] == open) {
                depth--
                if (depth == 0) return i
            }
        }
        return null
    }

    /**
     * 括号组是一个原子;若前面紧贴命令/函数名,把命令一起算进来。
     * \frac 的第二个参数组还会把第一个参数组一并纳入,保证整条命令原子删除。
     */
    private fun atomStartIncludingReceiver(text: String, openIndex: Int): Int {
        var i = openIndex - 1
        while (i >= 0 && text[i].isWhitespace()) i--
        if (i < 0) return openIndex

        // \sqrt[3]{...}: 先跨过可选指数
        if (text[i] == ']') {
            val bracket = matchingOpen(text, i, ']') ?: return openIndex
            val commandStart = commandStartBefore(text, bracket)
            return commandStart ?: openIndex
        }

        // \frac{a}{b}: 第二个组前面是第一个组
        if (text[i] == '}') {
            val firstOpen = matchingOpen(text, i, '}') ?: return openIndex
            val commandStart = commandStartBefore(text, firstOpen)
            if (commandStart != null && text.substring(commandStart, firstOpen).trimStart('\\') == "frac") {
                return commandStart
            }
            return openIndex
        }

        return commandStartBefore(text, openIndex) ?: openIndex
    }

    /** 紧贴 position 前的命令/函数名起点(含反斜杠),否则 null */
    private fun commandStartBefore(text: String, position: Int): Int? {
        var i = position - 1
        while (i >= 0 && text[i].isWhitespace()) i--
        if (i < 0 || !text[i].isLetter()) return null
        while (i >= 0 && text[i].isLetter()) i--
        return if (i >= 0 && text[i] == '\\') i else null
    }
}
