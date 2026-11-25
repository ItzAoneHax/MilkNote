package com.milknote.app.ui.agent

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.milknote.app.ui.common.softClickable
import com.milknote.app.ui.theme.LocalMilkDark
import com.milknote.app.ui.theme.MilkRadius
import com.milknote.app.ui.theme.MilkSpace
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * 原生 LaTeX:纯 Kotlin AST 解析 + Compose Canvas 二维排版。
 *
 * 为什么要有它:KaTeX 走 WebView,首帧有 60–200ms 空窗,流式输出时每来一个 token 重载 WebView
 * 会疯狂闪烁。原生渲染零延迟,因此流式期间与 WebView 就绪前都由它顶上,WebView 不可用时永久兜底。
 *
 * 排版模型:每个节点量出一个 [MathBox](宽 + 基线上下高度 + 绘制闭包),再由容器按基线拼装;
 * 分式/根号/上下标/大型运算符/矩阵括号全部按真实数学排版规则定位,不是把源码铺成一行。
 */
object LatexAstParser {

    sealed interface Node

    data class Seq(val items: List<Node>) : Node

    /** \text{…} / \mathrm{…} 等逐字文本 */
    data class Text(val text: String) : Node

    data class Symbol(val symbol: String) : Node

    data class Sup(val base: Node, val exp: Node) : Node

    data class Sub(val base: Node, val index: Node) : Node

    data class SubSup(val base: Node, val index: Node, val exp: Node) : Node

    data class Frac(val num: Node, val den: Node) : Node

    data class Sqrt(val body: Node, val degree: Node? = null) : Node

    data class BigOp(
        val operator: String,
        val lower: Node? = null,
        val upper: Node? = null,
    ) : Node

    data class Accent(val accent: String, val body: Node) : Node

    data class Delim(val left: String, val body: Node, val right: String) : Node

    data class Matrix(val env: String, val rows: List<List<Node>>) : Node

    data class Styled(val style: Style, val body: Node) : Node

    enum class Style { BOLD, MATHBB, ROMAN, TEXT, ITALIC }

    /** 永不抛异常:任何残缺/流式半截源码都退化成能显示的符号序列 */
    fun parse(latex: String): Node = runCatching { Parser(latex).parse() }.getOrElse { Seq(emptyList()) }

    private class Parser(private val src: String) {
        private var i = 0

        fun parse(): Node = normalize(Seq(parseSeq()))

        private fun parseSeq(): List<Node> {
            val out = ArrayList<Node>()
            while (i < src.length) {
                skipSpaces()
                if (i >= src.length) break
                val c = src[i]
                if (c == '}') break
                if (c == '&') break
                if (c == '\\' && src.startsWith("\\\\", i)) break
                if (c == '\\' && src.startsWith("\\end", i)) break
                if (c == '\\' && src.startsWith("\\right", i)) break
                if (c == '^' || c == '_') {
                    attachScript(out, c == '^')
                    continue
                }
                val before = i
                val node = parseAtom()
                if (node != null) out.add(node)
                if (i == before) i++ // 防御:保证前进,绝不空转
            }
            return out
        }

        private fun parseAtom(): Node? {
            skipSpaces()
            if (i >= src.length) return null
            val c = src[i]
            return when {
                c == '{' -> {
                    i++
                    val inner = normalize(Seq(parseSeq()))
                    if (i < src.length && src[i] == '}') i++
                    inner
                }
                c == '\\' -> parseCommand()
                c == '\'' -> {
                    i++
                    Symbol("′")
                }
                c == '~' -> {
                    i++
                    Symbol(" ")
                }
                c.isWhitespace() -> {
                    i++
                    Symbol(" ")
                }
                else -> {
                    i++
                    Symbol(normalizeChar(c))
                }
            }
        }

        private fun parseCommand(): Node? {
            val start = i
            i++ // '\'
            if (i >= src.length) return Symbol("\\")
            val name: String
            if (src[i].isLetter()) {
                val sb = StringBuilder()
                while (i < src.length && src[i].isLetter()) {
                    sb.append(src[i])
                    i++
                }
                name = sb.toString()
            } else {
                name = src[i].toString()
                i++
            }
            return when {
                name == "frac" || name == "dfrac" || name == "tfrac" ->
                    Frac(argument(), argument())
                name == "sqrt" -> {
                    skipSpaces()
                    val degree = if (i < src.length && src[i] == '[') {
                        i++
                        val sb = StringBuilder()
                        while (i < src.length && src[i] != ']') {
                            sb.append(src[i])
                            i++
                        }
                        if (i < src.length) i++
                        normalize(Seq(Parser(sb.toString()).parseSeq()))
                    } else {
                        null
                    }
                    Sqrt(argument(), degree)
                }
                name in BIG_OPERATORS -> {
                    val sym = SYMBOLS[name] ?: name
                    BigOp(sym)
                }
                name in FUNCTION_NAMES -> Text(SYMBOLS[name] ?: name)
                name in ACCENTS -> Accent(name, argument())
                name == "mathbf" -> Styled(Style.BOLD, argument())
                name == "mathbb" -> Styled(Style.MATHBB, argument())
                name == "mathrm" || name == "operatorname" -> Styled(Style.ROMAN, argument())
                name == "mathit" || name == "mathcal" || name == "mathscr" -> Styled(Style.ITALIC, argument())
                name == "text" || name == "textrm" || name == "textnormal" ->
                    Styled(Style.TEXT, Text(rawGroup()))
                name == "textbf" -> Styled(Style.BOLD, Text(rawGroup()))
                name == "left" -> parseLeftRight()
                name == "begin" -> parseEnvironment()
                name == "right" || name == "end" -> {
                    rawGroup()
                    null
                }
                name in SPACES -> Symbol(SPACES[name] ?: " ")
                name in IGNORED -> null
                else -> Symbol(SYMBOLS[name] ?: name)
            }.also {
                if (i == start) i = start + 1
            }
        }

        private fun parseLeftRight(): Node {
            skipSpaces()
            val left = delimiterToken()
            val body = normalize(Seq(parseSeq()))
            skipSpaces()
            var right = ""
            if (i < src.length && src.startsWith("\\right", i)) {
                i += "\\right".length
                skipSpaces()
                right = delimiterToken()
            }
            return Delim(left, body, right)
        }

        private fun delimiterToken(): String {
            skipSpaces()
            if (i >= src.length) return ""
            if (src[i] == '\\') {
                i++
                if (i >= src.length) return ""
                if (src[i].isLetter()) {
                    val sb = StringBuilder()
                    while (i < src.length && src[i].isLetter()) {
                        sb.append(src[i])
                        i++
                    }
                    return SYMBOLS[sb.toString()] ?: sb.toString()
                }
                val ch = src[i]
                i++
                return if (ch == '|') "‖" else ch.toString()
            }
            val ch = src[i]
            i++
            return if (ch == '.') "" else ch.toString()
        }

        private fun parseEnvironment(): Node {
            val env = rawGroup().trim()
            val terminator = "\\end{$env}"
            val end = src.indexOf(terminator, i)
            val body = if (end >= 0) src.substring(i, end) else src.substring(i)
            i = if (end >= 0) end + terminator.length else src.length
            val rows = splitRows(body)
            return Matrix(env, rows.map { row -> row.map { cell -> normalize(Seq(Parser(cell).parseSeq())) } })
        }

        private fun splitRows(body: String): List<List<String>> {
            val rows = ArrayList<List<String>>()
            var cells = ArrayList<String>()
            val cell = StringBuilder()
            var depth = 0
            var j = 0
            while (j < body.length) {
                val c = body[j]
                when {
                    c == '\\' && j + 1 < body.length && body[j + 1] == '\\' && depth == 0 -> {
                        cells.add(cell.toString())
                        cell.setLength(0)
                        rows.add(cells)
                        cells = ArrayList()
                        j += 2
                    }
                    c == '&' && depth == 0 -> {
                        cells.add(cell.toString())
                        cell.setLength(0)
                        j++
                    }
                    c == '\\' && j + 1 < body.length -> {
                        cell.append(c).append(body[j + 1])
                        j += 2
                    }
                    c == '{' -> {
                        depth++
                        cell.append(c)
                        j++
                    }
                    c == '}' -> {
                        depth = (depth - 1).coerceAtLeast(0)
                        cell.append(c)
                        j++
                    }
                    else -> {
                        cell.append(c)
                        j++
                    }
                }
            }
            cells.add(cell.toString())
            rows.add(cells)
            return rows.map { row -> row.map { it.trim() } }.filter { row -> row.any { it.isNotEmpty() } }
        }

        private fun attachScript(out: MutableList<Node>, isSup: Boolean) {
            i++
            val arg = argument()
            val base = out.removeLastOrNull()
            val node = when (base) {
                null -> if (isSup) Sup(Symbol(""), arg) else Sub(Symbol(""), arg)
                is Sub -> if (isSup) SubSup(base.base, base.index, arg) else Sub(base.base, arg)
                is Sup -> if (isSup) Sup(base.base, arg) else SubSup(base.base, arg, base.exp)
                is SubSup -> if (isSup) SubSup(base.base, base.index, arg) else SubSup(base.base, arg, base.exp)
                is BigOp -> if (isSup) base.copy(upper = arg) else base.copy(lower = arg)
                else -> if (isSup) Sup(base, arg) else Sub(base, arg)
            }
            out.add(node)
        }

        private fun argument(): Node {
            skipSpaces()
            if (i >= src.length) return Seq(emptyList())
            if (src[i] == '{') {
                i++
                val inner = normalize(Seq(parseSeq()))
                if (i < src.length && src[i] == '}') i++
                return inner
            }
            return parseAtom() ?: Seq(emptyList())
        }

        /** 逐字读取 `{…}`,保留内部反斜杠原样(`\text{a\_b}`) */
        private fun rawGroup(): String {
            skipSpaces()
            if (i >= src.length || src[i] != '{') return ""
            i++
            val sb = StringBuilder()
            var depth = 1
            while (i < src.length) {
                val c = src[i]
                when {
                    c == '\\' && i + 1 < src.length -> {
                        sb.append(c).append(src[i + 1])
                        i += 2
                    }
                    c == '{' -> {
                        depth++
                        sb.append(c)
                        i++
                    }
                    c == '}' -> {
                        depth--
                        i++
                        if (depth == 0) break
                        sb.append(c)
                    }
                    else -> {
                        sb.append(c)
                        i++
                    }
                }
            }
            return sb.toString()
        }

        private fun skipSpaces() {
            while (i < src.length && src[i].isWhitespace()) i++
        }

        private fun normalize(node: Node): Node =
            if (node is Seq && node.items.size == 1) node.items[0] else node

        private fun normalizeChar(c: Char): String = when (c) {
            '-' -> "−"
            '*' -> "∗"
            '\'' -> "′"
            else -> c.toString()
        }
    }

    private val BIG_OPERATORS = setOf(
        "sum", "prod", "coprod", "int", "iint", "iiint", "oint",
        "bigcup", "bigcap", "bigoplus", "bigotimes",
        "lim", "limsup", "liminf", "max", "min", "sup", "inf",
    )

    private val FUNCTION_NAMES = setOf(
        "log", "ln", "lg", "sin", "cos", "tan", "cot", "sec", "csc",
        "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh", "det", "dim", "ker", "deg", "gcd",
    )

    private val ACCENTS = setOf(
        "hat", "widehat", "bar", "overline", "vec", "overrightarrow",
        "tilde", "widetilde", "dot", "ddot", "acute", "grave", "check", "breve",
    )

    private val SPACES = mapOf(
        "," to " ", ";" to " ", ":" to " ", " " to " ", "quad" to "  ", "qquad" to "    ", "!" to "",
    )

    private val IGNORED = setOf(
        "displaystyle", "textstyle", "scriptstyle", "limits", "nolimits",
        "big", "Big", "bigg", "Bigg", "bigl", "bigr", "Bigl", "Bigr", "left.", "right.",
    )

    /** 常用符号表:希腊字母 / 运算符 / 关系 / 箭头 / 大符号 / 定界符 */
    private val SYMBOLS = hashMapOf(
        // 希腊
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ϵ",
        "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η", "theta" to "θ", "vartheta" to "ϑ",
        "iota" to "ι", "kappa" to "κ", "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
        "omicron" to "ο", "pi" to "π", "varpi" to "ϖ", "rho" to "ρ", "varrho" to "ϱ",
        "sigma" to "σ", "varsigma" to "ς", "tau" to "τ", "upsilon" to "υ", "phi" to "ϕ",
        "varphi" to "φ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ",
        "Pi" to "Π", "Sigma" to "Σ", "Upsilon" to "Υ", "Phi" to "Φ", "Psi" to "Ψ",
        "Omega" to "Ω",
        // 运算符与关系
        "pm" to "±", "mp" to "∓", "times" to "×", "div" to "÷", "cdot" to "⋅", "ast" to "∗",
        "star" to "⋆", "circ" to "∘", "bullet" to "∙", "oplus" to "⊕", "otimes" to "⊗",
        "odot" to "⊙", "le" to "≤", "leq" to "≤", "ge" to "≥", "geq" to "≥", "neq" to "≠",
        "ne" to "≠", "approx" to "≈", "sim" to "∼", "simeq" to "≃", "cong" to "≅",
        "equiv" to "≡", "propto" to "∝", "ll" to "≪", "gg" to "≫", "infty" to "∞",
        "partial" to "∂", "nabla" to "∇", "forall" to "∀", "exists" to "∃", "nexists" to "∄",
        "in" to "∈", "notin" to "∉", "ni" to "∋", "subset" to "⊂", "subseteq" to "⊆",
        "supset" to "⊃", "supseteq" to "⊇", "cup" to "∪", "cap" to "∩", "setminus" to "∖",
        "emptyset" to "∅", "varnothing" to "∅", "neg" to "¬", "land" to "∧", "lor" to "∨",
        "perp" to "⊥", "parallel" to "∥", "angle" to "∠", "triangle" to "△", "square" to "□",
        "therefore" to "∴", "because" to "∵", "degree" to "°", "prime" to "′",
        "hbar" to "ℏ", "ell" to "ℓ", "Re" to "ℜ", "Im" to "ℑ", "aleph" to "ℵ", "wp" to "℘",
        // 箭头
        "to" to "→", "rightarrow" to "→", "leftarrow" to "←", "leftrightarrow" to "↔",
        "Rightarrow" to "⇒", "Leftarrow" to "⇐", "Leftrightarrow" to "⇔", "iff" to "⇔",
        "implies" to "⇒", "mapsto" to "↦", "gets" to "←", "uparrow" to "↑",
        "downarrow" to "↓", "longrightarrow" to "⟶", "Longrightarrow" to "⟹",
        // 省略号
        "ldots" to "…", "dots" to "…", "cdots" to "⋯", "vdots" to "⋮", "ddots" to "⋱",
        // 大型运算符
        "sum" to "∑", "prod" to "∏", "coprod" to "∐", "int" to "∫", "iint" to "∬",
        "iiint" to "∭", "oint" to "∮", "bigcup" to "⋃", "bigcap" to "⋂",
        "bigoplus" to "⨁", "bigotimes" to "⨂",
        "lim" to "lim", "limsup" to "lim sup", "liminf" to "lim inf",
        "max" to "max", "min" to "min", "sup" to "sup", "inf" to "inf",
        "log" to "log", "ln" to "ln", "lg" to "lg", "sin" to "sin", "cos" to "cos",
        "tan" to "tan", "cot" to "cot", "sec" to "sec", "csc" to "csc",
        "arcsin" to "arcsin", "arccos" to "arccos", "arctan" to "arctan",
        "sinh" to "sinh", "cosh" to "cosh", "tanh" to "tanh",
        "det" to "det", "dim" to "dim", "ker" to "ker", "deg" to "deg", "gcd" to "gcd",
        // 定界符
        "{" to "{", "}" to "}", "|" to "‖", "lfloor" to "⌊", "rfloor" to "⌋",
        "lceil" to "⌈", "rceil" to "⌉", "langle" to "⟨", "rangle" to "⟩", "mid" to "∣",
        "vert" to "|", "Vert" to "‖", "backslash" to "\\",
    )

    /** `\mathbb{R}` 这类黑板体单字母 */
    internal fun blackboard(letter: String): String = when (letter) {
        "R" -> "ℝ"
        "N" -> "ℕ"
        "Z" -> "ℤ"
        "Q" -> "ℚ"
        "C" -> "ℂ"
        "P" -> "ℙ"
        "H" -> "ℍ"
        "E" -> "𝔼"
        else -> letter
    }
}

/**
 * 原生公式渲染(单行,适合行内 `$…$` 与流式期间的即时显示)。
 * 排版结果按基线量好尺寸后交给父级布局,不参与滚动也不重排。
 */
@Composable
fun LatexNativeFormula(
    latex: String,
    displayMode: Boolean = false,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: TextUnit = if (displayMode) 18.sp else 15.sp,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val sizePx = with(density) { fontSize.toPx() }
    val box = remember(latex, displayMode, color, sizePx) {
        val engine = MathEngine(measurer, density, color, sizePx, displayMode)
        runCatching { engine.box(LatexAstParser.parse(latex)) }
            .getOrElse { engine.fallback(latex) }
    }
    val width = with(density) { box.width.toDp() }
    val height = with(density) { box.height.toDp() }
    Canvas(modifier.size(width.coerceAtLeast(2.dp), height.coerceAtLeast(fontSize.value.dp * 0.9f))) {
        box.draw(this, 0f, box.ascent)
    }
}

/**
 * 块级公式:流式期间走原生(零闪烁),稳定后交给 KaTeX WebView 精修;
 * WebView 就绪前 / 渲染失败 / 无 WebView 时始终显示原生排版,永不留白。
 */
@Composable
fun LatexDisplayBlock(
    latex: String,
    streaming: Boolean = false,
    modifier: Modifier = Modifier,
    onOpenZoom: (() -> Unit)? = null,
) {
    val dark = LocalMilkDark.current
    val cs = MaterialTheme.colorScheme
    var webReady by remember(latex) { mutableStateOf(false) }
    var webFailed by remember(latex) { mutableStateOf(false) }
    val useWeb = !streaming && !webFailed
    val shape = RoundedCornerShape(MilkRadius.md)
    val scroll = rememberScrollState()

    // 兜底:WebView 起不来(被禁用/JS 被拦)时永久保留原生排版,不留下 1dp 的空壳
    LaunchedEffect(latex, useWeb) {
        if (useWeb && !webReady) {
            delay(2000)
            if (!webReady) webFailed = true
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cs.surfaceVariant.copy(alpha = if (dark) 0.35f else 0.5f))
            .border(1.dp, cs.outline.copy(alpha = 0.6f), shape)
            .then(if (onOpenZoom != null) Modifier.softClickable(onClick = onOpenZoom) else Modifier)
            .padding(horizontal = MilkSpace.md, vertical = MilkSpace.sm),
        contentAlignment = Alignment.CenterStart,
    ) {
        // WebView 走有界宽度(内部横向滚动),原生兜底走外层横向滚动
        if (useWeb) {
            KaTeXFormulaWebView(
                latex = latex,
                displayMode = true,
                dark = dark,
                modifier = Modifier.fillMaxWidth(),
                // 回报值是 CSS px(= dp),这里只当“首帧已好”的信号用
                onReady = { webReady = true },
                onFailed = { webFailed = true },
            )
        }
        if (!useWeb || !webReady) {
            Row(Modifier.horizontalScroll(scroll, enabled = true)) {
                LatexNativeFormula(
                    latex = latex,
                    displayMode = true,
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = cs.onSurface,
                )
            }
        }
    }
}

// ---------------- 尺寸模型 ----------------

/** 一个已排好的数学盒子:宽 + 基线以上/以下高度 + 绘制闭包(基线定位) */
private class MathBox(
    val width: Float,
    val ascent: Float,
    val descent: Float,
    val draw: (DrawScope, Float, Float) -> Unit,
) {
    val height: Float get() = ascent + descent
}

private fun emptyBox(): MathBox = MathBox(0f, 0f, 0f) { _, _, _ -> }

private fun spaceBox(width: Float): MathBox = MathBox(width, 0f, 0f) { _, _, _ -> }

private fun hbox(boxes: List<MathBox>): MathBox {
    if (boxes.isEmpty()) return emptyBox()
    var width = 0f
    var ascent = 0f
    var descent = 0f
    val offsets = FloatArray(boxes.size)
    boxes.forEachIndexed { idx, box ->
        offsets[idx] = width
        width += box.width
        ascent = max(ascent, box.ascent)
        descent = max(descent, box.descent)
    }
    return MathBox(width, ascent, descent) { scope, x, y ->
        boxes.forEachIndexed { idx, box -> box.draw(scope, x + offsets[idx], y) }
    }
}

/** 二维排版引擎:一次组合内测量、一次 Canvas 绘制 */
private class MathEngine(
    private val measurer: TextMeasurer,
    private val density: Density,
    private val color: Color,
    private val basePx: Float,
    private val display: Boolean,
) {

    private val scriptScale = if (display) 0.7f else 0.68f
    private val axis = 0.26f

    fun box(
        node: LatexAstParser.Node,
        scale: Float = 1f,
        upright: Boolean = false,
        bold: Boolean = false,
    ): MathBox {
        val sizePx = basePx * scale
        return when (node) {
            is LatexAstParser.Seq -> seqBox(node.items, scale, upright, bold)
            is LatexAstParser.Text -> measureText(node.text, sizePx, TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontStyle = FontStyle.Normal))
            is LatexAstParser.Symbol -> symbolBox(node.symbol, sizePx, upright, bold)
            is LatexAstParser.Sup -> scriptBox(node.base, node.exp, null, scale, upright, bold)
            is LatexAstParser.Sub -> scriptBox(node.base, null, node.index, scale, upright, bold)
            is LatexAstParser.SubSup -> scriptBox(node.base, node.exp, node.index, scale, upright, bold)
            is LatexAstParser.Frac -> fracBox(node, scale, upright, bold)
            is LatexAstParser.Sqrt -> sqrtBox(node, scale, upright, bold)
            is LatexAstParser.BigOp -> bigOpBox(node, scale)
            is LatexAstParser.Accent -> accentBox(node, scale, upright, bold)
            is LatexAstParser.Delim -> delimBox(node, scale, upright, bold)
            is LatexAstParser.Matrix -> matrixBox(node, scale)
            is LatexAstParser.Styled -> when (node.style) {
                LatexAstParser.Style.BOLD -> box(node.body, scale, upright = true, bold = true)
                LatexAstParser.Style.MATHBB -> mathbbBox(node.body, scale)
                LatexAstParser.Style.ROMAN, LatexAstParser.Style.TEXT ->
                    box(node.body, scale, upright = true, bold = bold)
                LatexAstParser.Style.ITALIC -> box(node.body, scale, upright = false, bold = bold)
            }
        }
    }

    fun fallback(latex: String): MathBox = measureText(
        latex,
        basePx * 0.9f,
        TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal),
    )

    private fun seqBox(
        items: List<LatexAstParser.Node>,
        scale: Float,
        upright: Boolean,
        bold: Boolean,
    ): MathBox {
        if (items.isEmpty()) return emptyBox()
        val sizePx = basePx * scale
        val boxes = ArrayList<MathBox>(items.size * 2)
        items.forEachIndexed { idx, item ->
            if (idx > 0) boxes.add(spaceBox(sizePx * 0.06f))
            boxes.add(box(item, scale, upright, bold))
        }
        return hbox(boxes)
    }

    private fun mathbbBox(node: LatexAstParser.Node, scale: Float): MathBox = when (node) {
        is LatexAstParser.Symbol -> symbolBox(
            LatexAstParser.blackboard(node.symbol), basePx * scale, upright = true, bold = false,
        )
        is LatexAstParser.Seq -> hbox(node.items.map { mathbbBox(it, scale) })
        else -> box(node, scale, upright = true)
    }

    private fun symbolBox(symbol: String, sizePx: Float, upright: Boolean, bold: Boolean): MathBox {
        if (symbol.isEmpty()) return emptyBox()
        if (symbol == " ") return spaceBox(sizePx * 0.22f)
        val italic = !upright && symbol.length == 1 && symbol[0].isLetter() && symbol[0].code < 0x0400
        val style = TextStyle(
            fontFamily = FontFamily.Serif,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
        return measureText(symbol, sizePx, style)
    }

    private fun measureText(text: String, sizePx: Float, style: TextStyle): MathBox {
        if (text.isEmpty()) return emptyBox()
        val resolved = style.copy(
            fontSize = with(density) { sizePx.toSp() },
            color = color,
        )
        val layout = measurer.measure(
            text = AnnotatedString(text),
            style = resolved,
            softWrap = false,
            maxLines = 1,
        )
        val width = layout.size.width.toFloat().coerceAtLeast(0f)
        val height = layout.size.height.toFloat()
        val baseline = layout.firstBaseline
            .takeIf { it.isFinite() && it > 0f }
            ?: (height * 0.8f)
        return MathBox(width, baseline, max(0f, height - baseline)) { scope, x, y ->
            scope.drawText(layout, topLeft = Offset(x, y - baseline))
        }
    }

    private fun scriptBox(
        baseNode: LatexAstParser.Node,
        sup: LatexAstParser.Node?,
        sub: LatexAstParser.Node?,
        scale: Float,
        upright: Boolean,
        bold: Boolean,
    ): MathBox {
        val sizePx = basePx * scale
        val base = box(baseNode, scale, upright, bold)
        val supBox = sup?.let { box(it, scale * scriptScale, upright, bold) }
        val subBox = sub?.let { box(it, scale * scriptScale, upright, bold) }
        val supShift = sizePx * 0.46f
        val subShift = sizePx * 0.22f
        var ascent = base.ascent
        var descent = base.descent
        if (supBox != null) ascent = max(ascent, supShift + supBox.ascent)
        if (subBox != null) descent = max(descent, subShift + subBox.descent)
        val width = base.width + max(supBox?.width ?: 0f, subBox?.width ?: 0f) + sizePx * 0.02f
        return MathBox(width, ascent, descent) { scope, x, y ->
            base.draw(scope, x, y)
            supBox?.draw(scope, x + base.width, y - supShift)
            subBox?.draw(scope, x + base.width, y + subShift)
        }
    }

    private fun fracBox(
        node: LatexAstParser.Frac,
        scale: Float,
        upright: Boolean,
        bold: Boolean,
    ): MathBox {
        val sizePx = basePx * scale
        val numScale = scale * if (display) 0.95f else 0.85f
        val denScale = numScale
        val num = box(node.num, numScale, upright, bold)
        val den = box(node.den, denScale, upright, bold)
        val ruleHalf = sizePx * 0.028f
        val gap = sizePx * 0.14f
        val axisY = sizePx * axis
        val padding = sizePx * 0.1f
        val width = max(num.width, den.width) + padding * 2
        val ascent = axisY + ruleHalf + gap + num.height
        val descent = axisY + ruleHalf + gap + den.height
        return MathBox(width, ascent, descent) { scope, x, y ->
            val ruleY = y - axisY
            val numBaseline = ruleY - ruleHalf - gap - num.descent
            val denBaseline = ruleY + ruleHalf + gap + den.ascent
            num.draw(scope, x + (width - num.width) / 2f, numBaseline)
            den.draw(scope, x + (width - den.width) / 2f, denBaseline)
            scope.drawLine(
                color = color,
                start = Offset(x + padding * 0.35f, ruleY),
                end = Offset(x + width - padding * 0.35f, ruleY),
                strokeWidth = ruleHalf * 2f,
                cap = StrokeCap.Round,
            )
        }
    }

    private fun sqrtBox(
        node: LatexAstParser.Sqrt,
        scale: Float,
        upright: Boolean,
        bold: Boolean,
    ): MathBox {
        val sizePx = basePx * scale
        val body = box(node.body, scale, upright, bold)
        val degree = node.degree?.let { box(it, scale * 0.55f, upright, bold) }
        val radicalWidth = sizePx * 0.62f
        val overlineY = -body.ascent - sizePx * 0.14f
        val degreeWidth = degree?.width ?: 0f
        val degreeAscent = degree?.let { it.height + sizePx * 0.05f } ?: 0f
        val ascent = body.ascent + sizePx * 0.24f + degreeAscent * 0.4f
        val descent = max(body.descent, sizePx * 0.12f)
        val width = body.width + radicalWidth + degreeWidth + sizePx * 0.08f
        return MathBox(width, ascent, descent) { scope, x, y ->
            val topY = y + overlineY
            val path = Path().apply {
                moveTo(x + degreeWidth + radicalWidth * 0.42f, topY + sizePx * 0.18f)
                lineTo(x + degreeWidth + radicalWidth * 0.62f, topY - sizePx * 0.06f)
                lineTo(x + degreeWidth + radicalWidth * 0.78f, topY)
                lineTo(x + degreeWidth + radicalWidth, topY)
            }
            scope.drawPath(
                path = path,
                color = color,
                style = Stroke(width = sizePx * 0.055f, cap = StrokeCap.Round),
            )
            // 根号的横线(vinculum)压住被开方式上方
            scope.drawLine(
                color = color,
                start = Offset(x + degreeWidth + radicalWidth, topY),
                end = Offset(x + width - sizePx * 0.02f, topY),
                strokeWidth = sizePx * 0.05f,
                cap = StrokeCap.Round,
            )
            body.draw(scope, x + degreeWidth + radicalWidth + sizePx * 0.04f, y)
            degree?.draw(scope, x, y - sizePx * 0.55f)
        }
    }

    private fun bigOpBox(node: LatexAstParser.BigOp, scale: Float): MathBox {
        val sizePx = basePx * scale
        val opScale = if (display) 1.45f else 1.05f
        val glyph = box(LatexAstParser.Symbol(node.operator), scale * opScale, upright = true)
        val lower = node.lower?.let { box(it, scale * scriptScale) }
        val upper = node.upper?.let { box(it, scale * scriptScale) }
        if (lower == null && upper == null) return glyph
        if (!display) {
            // 行内:上下限缩成右侧上下标
            var width = glyph.width
            var ascent = glyph.ascent
            var descent = glyph.descent
            val supShift = sizePx * 0.5f
            val subShift = sizePx * 0.2f
            upper?.let {
                width = max(width, glyph.width + it.width)
                ascent = max(ascent, supShift + it.ascent)
            }
            lower?.let {
                width = max(width, glyph.width + it.width)
                descent = max(descent, subShift + it.descent)
            }
            return MathBox(width, ascent, descent) { scope, x, y ->
                glyph.draw(scope, x, y)
                upper?.draw(scope, x + glyph.width, y - supShift)
                lower?.draw(scope, x + glyph.width, y + subShift)
            }
        }
        val gap = sizePx * 0.08f
        val width = listOfNotNull(glyph.width, lower?.width, upper?.width).max()
        val ascent = glyph.ascent + (upper?.let { it.height + gap } ?: 0f)
        val descent = glyph.descent + (lower?.let { it.height + gap } ?: 0f)
        return MathBox(width, ascent, descent) { scope, x, y ->
            upper?.draw(scope, x + (width - upper.width) / 2f, y - glyph.ascent - gap - upper.descent)
            glyph.draw(scope, x + (width - glyph.width) / 2f, y)
            lower?.draw(scope, x + (width - lower.width) / 2f, y + glyph.descent + gap + lower.ascent)
        }
    }

    private fun accentBox(
        node: LatexAstParser.Accent,
        scale: Float,
        upright: Boolean,
        bold: Boolean,
    ): MathBox {
        val sizePx = basePx * scale
        val body = box(node.body, scale, upright, bold)
        val accentHeight = sizePx * 0.24f
        val ascent = body.ascent + accentHeight
        val width = max(body.width, sizePx * 0.5f)
        return MathBox(width, ascent, body.descent) { scope, x, y ->
            body.draw(scope, x + (width - body.width) / 2f, y)
            val topY = y - body.ascent - accentHeight * 0.55f
            val left = x + (width - body.width * 0.8f) / 2f
            val right = left + body.width * 0.8f
            val center = (left + right) / 2f
            when (node.accent) {
                "bar", "overline" -> scope.drawLine(
                    color, Offset(left, topY), Offset(right, topY), strokeWidth = sizePx * 0.05f,
                )
                "vec", "overrightarrow" -> {
                    scope.drawLine(color, Offset(left, topY), Offset(right, topY), strokeWidth = sizePx * 0.045f)
                    scope.drawLine(
                        color, Offset(right - sizePx * 0.12f, topY - sizePx * 0.07f),
                        Offset(right, topY), strokeWidth = sizePx * 0.045f,
                    )
                    scope.drawLine(
                        color, Offset(right - sizePx * 0.12f, topY + sizePx * 0.07f),
                        Offset(right, topY), strokeWidth = sizePx * 0.045f,
                    )
                }
                "dot" -> scope.drawCircle(color, radius = sizePx * 0.04f, center = Offset(center, topY))
                "ddot" -> {
                    scope.drawCircle(color, sizePx * 0.04f, Offset(center - sizePx * 0.09f, topY))
                    scope.drawCircle(color, sizePx * 0.04f, Offset(center + sizePx * 0.09f, topY))
                }
                "tilde", "widetilde" -> {
                    val wave = Path().apply {
                        moveTo(left, topY + sizePx * 0.04f)
                        cubicTo(
                            left + (right - left) * 0.3f, topY - sizePx * 0.05f,
                            left + (right - left) * 0.7f, topY + sizePx * 0.09f,
                            right, topY,
                        )
                    }
                    scope.drawPath(wave, color, style = Stroke(width = sizePx * 0.045f))
                }
                "acute" -> scope.drawLine(
                    color, Offset(center - sizePx * 0.05f, topY + sizePx * 0.03f),
                    Offset(center + sizePx * 0.04f, topY - sizePx * 0.06f), sizePx * 0.045f,
                )
                "grave" -> scope.drawLine(
                    color, Offset(center + sizePx * 0.05f, topY + sizePx * 0.03f),
                    Offset(center - sizePx * 0.04f, topY - sizePx * 0.06f), sizePx * 0.045f,
                )
                "check" -> {
                    val v = Path().apply {
                        moveTo(center - sizePx * 0.07f, topY - sizePx * 0.05f)
                        lineTo(center, topY + sizePx * 0.03f)
                        lineTo(center + sizePx * 0.07f, topY - sizePx * 0.05f)
                    }
                    scope.drawPath(v, color, style = Stroke(width = sizePx * 0.045f))
                }
                "breve" -> {
                    val v = Path().apply {
                        moveTo(center - sizePx * 0.08f, topY - sizePx * 0.03f)
                        cubicTo(
                            center - sizePx * 0.04f, topY + sizePx * 0.06f,
                            center + sizePx * 0.04f, topY + sizePx * 0.06f,
                            center + sizePx * 0.08f, topY - sizePx * 0.03f,
                        )
                    }
                    scope.drawPath(v, color, style = Stroke(width = sizePx * 0.045f))
                }
                else -> {
                    val v = Path().apply {
                        moveTo(center - sizePx * 0.09f, topY + sizePx * 0.05f)
                        lineTo(center, topY - sizePx * 0.05f)
                        lineTo(center + sizePx * 0.09f, topY + sizePx * 0.05f)
                    }
                    scope.drawPath(v, color, style = Stroke(width = sizePx * 0.045f))
                }
            }
        }
    }

    private fun delimBox(
        node: LatexAstParser.Delim,
        scale: Float,
        upright: Boolean,
        bold: Boolean,
    ): MathBox {
        val body = box(node.body, scale, upright, bold)
        val sizePx = basePx * scale
        val left = if (node.left.isBlank()) null else bracketBox(node.left, body.height, sizePx, upright, bold)
        val right = if (node.right.isBlank()) null else bracketBox(node.right, body.height, sizePx, upright, bold)
        val totalHeight = listOfNotNull(body.height, left?.height, right?.height).max()
        val ascent = (totalHeight - body.height) / 2f + body.ascent
        val descent = totalHeight - ascent
        val gap = sizePx * 0.04f
        val width = (left?.width ?: 0f) + body.width + (right?.width ?: 0f) + (if (left != null) gap else 0f) +
            (if (right != null) gap else 0f)
        return MathBox(width, ascent, descent) { scope, x, y ->
            var cursor = x
            left?.let {
                val top = y - ascent + (totalHeight - it.height) / 2f
                it.draw(scope, cursor, top + it.ascent)
                cursor += it.width + gap
            }
            body.draw(scope, cursor, y)
            cursor += body.width + gap
            right?.let {
                val top = y - ascent + (totalHeight - it.height) / 2f
                it.draw(scope, cursor, top + it.ascent)
            }
        }
    }

    /** 定界符按内容高度等比放大(最多 6 倍),矩阵/分式括号才不会显得矮一截 */
    private fun bracketBox(
        glyph: String,
        contentHeight: Float,
        sizePx: Float,
        upright: Boolean,
        bold: Boolean,
    ): MathBox {
        val style = TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (upright) FontStyle.Normal else FontStyle.Normal,
        )
        var size = sizePx
        var box = measureText(glyph, size, style)
        val target = contentHeight + sizePx * 0.24f
        if (box.height > 0f && box.height < target) {
            size = (size * target / box.height).coerceAtMost(sizePx * 6f)
            box = measureText(glyph, size, style)
        }
        return box
    }

    private fun matrixBox(node: LatexAstParser.Matrix, scale: Float): MathBox {
        val sizePx = basePx * scale
        val cellGap = sizePx * 0.75f
        val rowGap = sizePx * 0.3f
        val cells = node.rows.map { row -> row.map { box(it, scale) } }
        if (cells.isEmpty()) return emptyBox()
        val cols = cells.maxOf { it.size }
        val colWidths = FloatArray(cols)
        cells.forEach { row -> row.forEachIndexed { idx, cell -> colWidths[idx] = max(colWidths[idx], cell.width) } }
        val rowAscents = FloatArray(cells.size)
        val rowDescents = FloatArray(cells.size)
        cells.forEachIndexed { r, row ->
            row.forEach { cell ->
                rowAscents[r] = max(rowAscents[r], cell.ascent)
                rowDescents[r] = max(rowDescents[r], cell.descent)
            }
        }
        val contentWidth = colWidths.sum() + cellGap * (cols - 1).coerceAtLeast(0)
        val contentHeight = (rowAscents.indices).sumOf { (rowAscents[it] + rowDescents[it] + rowGap).toDouble() }
            .toFloat() - rowGap

        val env = node.env.lowercase()
        val leftBracket = when {
            env.contains("pmatrix") -> "("
            env.contains("bmatrix") -> "["
            env.contains("vmatrix") -> "|"
            env.contains("cases") -> "{"
            else -> ""
        }
        val rightBracket = when {
            env.contains("pmatrix") -> ")"
            env.contains("bmatrix") -> "]"
            env.contains("vmatrix") -> "|"
            env.contains("Vmatrix") -> "‖"
            else -> ""
        }
        val left = if (leftBracket.isBlank()) null else bracketBox(leftBracket, contentHeight, sizePx, true, false)
        val right = if (rightBracket.isBlank()) null else bracketBox(rightBracket, contentHeight, sizePx, true, false)
        val gap = sizePx * 0.12f
        val width = (left?.width ?: 0f) + contentWidth + (right?.width ?: 0f) +
            (if (left != null) gap else 0f) + (if (right != null) gap else 0f)
        val totalHeight = max(contentHeight, max(left?.height ?: 0f, right?.height ?: 0f))
        val ascent = (totalHeight - contentHeight) / 2f + (rowAscents.firstOrNull() ?: 0f)
        val descent = totalHeight - ascent
        return MathBox(width, ascent, descent) { scope, x, y ->
            var cursor = x
            left?.let {
                it.draw(scope, cursor, y - ascent + (totalHeight - it.height) / 2f + it.ascent)
                cursor += it.width + gap
            }
            var rowBase = y - ascent + (totalHeight - contentHeight) / 2f + (rowAscents.firstOrNull() ?: 0f)
            cells.forEachIndexed { r, row ->
                var cellX = cursor
                row.forEachIndexed { c, cell ->
                    cell.draw(scope, cellX, rowBase)
                    cellX += colWidths[c] + cellGap
                }
                rowBase += rowAscents[r] + rowDescents[r] + rowGap
            }
            cursor += contentWidth + gap
            right?.let {
                it.draw(scope, cursor, y - ascent + (totalHeight - it.height) / 2f + it.ascent)
            }
        }
    }
}

/**
 * 行内 LaTeX → Unicode 排版文本(正文段落里内联显示,零 WebView 依赖)。
 * 只做“不会更难读”的折叠:`\frac{a}{b}` → `a/b`,`\sqrt{x}` → `√x`,`x^2` → `x²`。
 */
internal object LatexUnicode {

    private val SUPERSCRIPTS = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵',
        '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹', '+' to '⁺', '-' to '⁻',
        '=' to '⁼', '(' to '⁽', ')' to '⁾', 'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ', 'y' to 'ʸ',
    )

    private val SUBSCRIPTS = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄', '5' to '₅',
        '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉', '+' to '₊', '-' to '₋',
        '=' to '₌', '(' to '₍', ')' to '₎', 'a' to 'ₐ', 'e' to 'ₑ', 'i' to 'ᵢ',
        'j' to 'ⱼ', 'n' to 'ₙ', 'x' to 'ₓ',
    )

    fun inline(latex: String): String {
        val out = StringBuilder()
        var i = 0
        val src = latex.replace("\\\\", " ")
        while (i < src.length) {
            val c = src[i]
            when {
                c == '\\' -> {
                    val cmd = buildString {
                        var j = i + 1
                        while (j < src.length && src[j].isLetter()) {
                            append(src[j])
                            j++
                        }
                    }
                    if (cmd.isEmpty()) {
                        i++
                        continue
                    }
                    i += cmd.length + 1
                    when (cmd) {
                        "frac", "dfrac", "tfrac" -> {
                            val num = readGroup(src, i)
                            i = num.second
                            val den = readGroup(src, i)
                            i = den.second
                            out.append(num.first).append('/').append(den.first)
                        }
                        "sqrt" -> {
                            if (i < src.length && src[i] == '[') {
                                val idx = src.indexOf(']', i)
                                if (idx > i) i = idx + 1
                            }
                            val body = readGroup(src, i)
                            i = body.second
                            out.append('√').append(body.first)
                        }
                        "text", "mathrm", "mathbf", "mathit" -> {
                            val body = readGroup(src, i)
                            i = body.second
                            out.append(body.first)
                        }
                        "left", "right" -> Unit
                        "cdot" -> out.append('⋅')
                        "times" -> out.append('×')
                        "div" -> out.append('÷')
                        "pm" -> out.append('±')
                        "mp" -> out.append('∓')
                        "le", "leq" -> out.append('≤')
                        "ge", "geq" -> out.append('≥')
                        "neq", "ne" -> out.append('≠')
                        "approx" -> out.append('≈')
                        "equiv" -> out.append('≡')
                        "infty" -> out.append('∞')
                        "to", "rightarrow" -> out.append('→')
                        "Rightarrow", "implies" -> out.append('⇒')
                        "Leftrightarrow", "iff" -> out.append('⇔')
                        "sum" -> out.append('∑')
                        "prod" -> out.append('∏')
                        "int" -> out.append('∫')
                        "partial" -> out.append('∂')
                        "nabla" -> out.append('∇')
                        "in" -> out.append('∈')
                        "notin" -> out.append('∉')
                        "subset" -> out.append('⊂')
                        "subseteq" -> out.append('⊆')
                        "cup" -> out.append('∪')
                        "cap" -> out.append('∩')
                        "ldots", "dots" -> out.append('…')
                        "cdots" -> out.append('⋯')
                        "alpha" -> out.append('α')
                        "beta" -> out.append('β')
                        "gamma" -> out.append('γ')
                        "delta" -> out.append('δ')
                        "epsilon", "varepsilon" -> out.append('ε')
                        "theta" -> out.append('θ')
                        "lambda" -> out.append('λ')
                        "mu" -> out.append('μ')
                        "pi" -> out.append('π')
                        "rho" -> out.append('ρ')
                        "sigma" -> out.append('σ')
                        "tau" -> out.append('τ')
                        "phi", "varphi" -> out.append('φ')
                        "psi" -> out.append('ψ')
                        "omega" -> out.append('ω')
                        "Omega" -> out.append('Ω')
                        "Delta" -> out.append('Δ')
                        "Sigma" -> out.append('Σ')
                        "Pi" -> out.append('Π')
                        "Lambda" -> out.append('Λ')
                        "Theta" -> out.append('Θ')
                        "angle" -> out.append('∠')
                        "degree" -> out.append('°')
                        "quad", "qquad" -> out.append(' ')
                        "sin", "cos", "tan", "log", "ln", "lim" -> out.append(cmd)
                        else -> out.append(cmd)
                    }
                }
                c == '^' -> {
                    val group = readGroup(src, i + 1)
                    i = group.second
                    val converted = group.first.map { SUPERSCRIPTS[it] ?: it }.joinToString("")
                    if (converted.all { SUPERSCRIPTS.containsValue(it) }) {
                        out.append(converted)
                    } else {
                        out.append('^').append(group.first)
                    }
                }
                c == '_' -> {
                    val group = readGroup(src, i + 1)
                    i = group.second
                    val converted = group.first.map { SUBSCRIPTS[it] ?: it }.joinToString("")
                    if (converted.all { SUBSCRIPTS.containsValue(it) }) {
                        out.append(converted)
                    } else {
                        out.append('_').append(group.first)
                    }
                }
                c == '{' -> {
                    val group = readGroup(src, i)
                    i = group.second
                    out.append(inline(group.first))
                }
                c == '}' -> i++
                c == '&' -> {
                    out.append(' ')
                    i++
                }
                c == '\\' -> i++
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString().trim()
    }

    /** 读取 `{…}`(或单个字符),返回内容与下一个下标 */
    private fun readGroup(src: String, start: Int): Pair<String, Int> {
        var i = start
        while (i < src.length && src[i] == ' ') i++
        if (i >= src.length) return "" to i
        if (src[i] == '{') {
            var depth = 1
            val sb = StringBuilder()
            i++
            while (i < src.length) {
                val c = src[i]
                if (c == '{') depth++
                if (c == '}') {
                    depth--
                    if (depth == 0) {
                        i++
                        break
                    }
                }
                sb.append(c)
                i++
            }
            return sb.toString() to i
        }
        return src[i].toString() to (i + 1)
    }
}
