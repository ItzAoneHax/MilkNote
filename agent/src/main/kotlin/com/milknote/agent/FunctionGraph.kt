package com.milknote.agent

import java.util.Locale

/**
 * 函数图像共享模型(纯 Kotlin,不依赖 Android):
 * - 表达式规范化:接受普通算式与常用 LaTeX(\frac/\sqrt/^{}/\cdot/\pi/\left|\right| 等);
 * - 1–6 条曲线、固定 6 色调色板、视口与归一化框选;
 * - 采样逐点容错,定义域错误/NaN/无穷与渐近线大跳变都会断开折线。
 */

data class GraphViewport(
    val xMin: Double = -10.0,
    val xMax: Double = 10.0,
    val yMin: Double = -7.0,
    val yMax: Double = 7.0,
)

/** 归一化框选(0..1;left<right、top<bottom) */
data class GraphCrop(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top

    fun normalized(): GraphCrop {
        val l = left.coerceIn(0.0, 1.0)
        val r = right.coerceIn(0.0, 1.0)
        val t = top.coerceIn(0.0, 1.0)
        val b = bottom.coerceIn(0.0, 1.0)
        return GraphCrop(minOf(l, r), minOf(t, b), maxOf(l, r), maxOf(t, b))
    }

    companion object {
        /** 拖拽两点 → 归一化裁剪(顺序任意) */
        fun fromDrag(x1: Double, y1: Double, x2: Double, y2: Double): GraphCrop =
            GraphCrop(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2), maxOf(y1, y2)).normalized()
    }
}

data class FunctionGraphCurve(
    /** 用户/AI 原始表达式(可能是 LaTeX) */
    val source: String,
    /** 规范化后的 MathEvaluator 表达式 */
    val expression: String,
    /** ARGB;Long 便于 :agent 不依赖 Android */
    val color: Long,
)

data class FunctionGraphSpec(
    val curves: List<FunctionGraphCurve>,
    val viewport: GraphViewport = GraphViewport(),
)

class FunctionGraphException(message: String) : IllegalArgumentException(message)

data class GraphSamplePoint(val x: Double, val y: Double)

object FunctionGraph {

    const val MIN_CURVES = 1
    const val MAX_CURVES = 6
    const val MAX_EXPRESSION_LENGTH = 240
    const val MAX_VIEWPORT_ABS = 1000.0
    const val MIN_CROP_SIZE = 0.02

    /** 固定 6 色调色板(高对比、同图不同色) */
    val palette: List<Long> = listOf(
        0xFF2F6FEDL,
        0xFFE0523FL,
        0xFF1F9D6BL,
        0xFFB05CC9L,
        0xFFD98B1FL,
        0xFF3A9BBFL,
    )

    val defaultViewport = GraphViewport()

    fun colorAt(index: Int): Long = palette[((index % palette.size) + palette.size) % palette.size]

    /**
     * 校验并构造图像声明。颜色列表允许短于表达式(缺失按序号取调色板),也允许含 null。
     */
    fun build(
        expressions: List<String>,
        colors: List<Long?> = emptyList(),
        viewport: GraphViewport = defaultViewport,
    ): FunctionGraphSpec {
        if (expressions.size < MIN_CURVES) throw FunctionGraphException("至少需要 1 个函数表达式")
        if (expressions.size > MAX_CURVES) {
            throw FunctionGraphException("最多支持 $MAX_CURVES 个函数表达式(当前 ${expressions.size} 个)")
        }
        validateViewport(viewport)
        val curves = expressions.mapIndexed { index, raw ->
            val source = raw.trim()
            if (source.isEmpty()) throw FunctionGraphException("第 ${index + 1} 个函数表达式为空")
            val normalized = normalizeExpression(source)
            val supplied = colors.getOrNull(index)
            val color = normalizeColor(supplied ?: colorAt(index))
            FunctionGraphCurve(source = source, expression = normalized, color = color)
        }
        return FunctionGraphSpec(curves = curves, viewport = viewport)
    }

    fun validate(spec: FunctionGraphSpec): FunctionGraphSpec = build(
        spec.curves.map { it.source },
        spec.curves.map { it.color },
        spec.viewport,
    )

    fun validateViewport(viewport: GraphViewport) {
        val values = listOf(viewport.xMin, viewport.xMax, viewport.yMin, viewport.yMax)
        if (values.any { !it.isFinite() }) throw FunctionGraphException("视口范围必须是有限数值")
        if (values.any { kotlin.math.abs(it) > MAX_VIEWPORT_ABS }) {
            throw FunctionGraphException("视口范围不能超过 ±$MAX_VIEWPORT_ABS")
        }
        if (viewport.xMin >= viewport.xMax) throw FunctionGraphException("x 轴最小值必须小于最大值")
        if (viewport.yMin >= viewport.yMax) throw FunctionGraphException("y 轴最小值必须小于最大值")
    }

    fun validateCrop(crop: GraphCrop) {
        val c = crop.normalized()
        if (c.width < MIN_CROP_SIZE || c.height < MIN_CROP_SIZE) {
            throw FunctionGraphException("框选区域太小,请重新选择")
        }
    }

    fun normalizeColor(color: Long): Long {
        val argb = color and 0xFFFFFFFFL
        return if (argb ushr 24 == 0L) argb or 0xFF000000L else argb
    }

    /**
     * 普通式/常用 LaTeX → MathEvaluator 表达式。
     * 只保留自变量 x;拒绝多语句、赋值、下标、占位符和未知命令。
     */
    fun normalizeExpression(raw: String): String {
        var text = raw.trim()
        if (text.isEmpty()) throw FunctionGraphException("函数表达式不能为空")
        if (text.length > MAX_EXPRESSION_LENGTH * 2) throw FunctionGraphException("表达式过长")
        text = text
            .replace("$$", "")
            .replace("$", "")
            .replace("\\(", "")
            .replace("\\)", "")
            .replace("\\[", "")
            .replace("\\]", "")
            .replace('\u2212', '-')
            .replace('×', '*')
            .replace('·', '*')
            .replace('÷', '/')
            .replace("π", "pi")
        text = text.replace(
            Regex("^\\s*(?:y|f\\s*\\(\\s*x\\s*\\))\\s*=\\s*", RegexOption.IGNORE_CASE),
            "",
        )
        if (text.contains("\\square")) {
            throw FunctionGraphException("请先填写函数表达式中的占位符")
        }
        if (text.contains(';') || text.contains('\n') || text.contains('\r')) {
            throw FunctionGraphException("一次只能输入一个函数表达式")
        }
        if (text.contains('=')) throw FunctionGraphException("函数表达式不要包含等号,只输入等号右侧")

        // 绝对值:先处理 \left|...\right|,再处理普通 |...|
        text = text.replace("\\left|", "abs(").replace("\\right|", ")")
        val bar = Regex("\\|([^|]+)\\|")
        while (bar.containsMatchIn(text)) text = bar.replace(text, "abs($1)")

        text = text
            .replace("\\left(", "(")
            .replace("\\right)", ")")
            .replace("\\left[", "(")
            .replace("\\right]", ")")
            .replace("\\left\\{", "(")
            .replace("\\right\\}", ")")
            .replace("\\{", "(")
            .replace("\\}", ")")

        val converted = convertLatex(text)
        val plain = converted
            .replace('{', '(')
            .replace('}', ')')
            .lowercase(Locale.ROOT)
        val expression = plain.trim()
        if (expression.isEmpty()) throw FunctionGraphException("函数表达式不能为空")
        if (expression.contains(';') || expression.contains('\n') || expression.contains('=')) {
            throw FunctionGraphException("一次只能输入一个函数表达式")
        }
        if (expression.contains('\\')) throw FunctionGraphException("不支持的 LaTeX 命令")
        if (expression.length > MAX_EXPRESSION_LENGTH) {
            throw FunctionGraphException("表达式过长(最多 $MAX_EXPRESSION_LENGTH 字符)")
        }

        val identifiers = try {
            MathEvaluator.identifiers(expression)
        } catch (e: MathEvaluator.EvaluationException) {
            throw FunctionGraphException("表达式不合法:${e.message}")
        }
        val unknown = identifiers.firstOrNull { it != "x" && !MathEvaluator.isKnownIdentifier(it) }
        if (unknown != null) {
            throw FunctionGraphException("只支持自变量 x;未知变量或函数:$unknown")
        }

        // 语法/未知名称在采样前尽量明确报错;定义域错误留给逐点采样断段
        val probes = listOf(1.0, 3.3, -1.7, 0.25)
        val errors = ArrayList<String>()
        var ok = false
        for (x in probes) {
            try {
                MathEvaluator.evaluateAt(expression, "x", x)
                ok = true
                break
            } catch (e: MathEvaluator.EvaluationException) {
                errors += e.message ?: "无法解析"
            }
        }
        if (!ok) {
            val syntax = errors.firstOrNull { msg ->
                listOf(
                    "未知变量", "未知函数", "无法识别", "缺少", "意外", "不是有限",
                    "参数数量", "需要参数括号", "需要 1 个参数", "需要 2 个参数",
                    "需要 3 个参数", "表达式不完整", "非法记号",
                ).any { msg.contains(it) }
            }
            if (syntax != null) throw FunctionGraphException("表达式不合法:$syntax")
        }
        return expression
    }

    private fun convertLatex(src: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                c == '\\' -> {
                    i++
                    if (i >= src.length) throw FunctionGraphException("LaTeX 命令不完整")
                    if (!src[i].isLetter()) {
                        when (src[i]) {
                            '{', '}', '%', '$', '&', '#', '_', '|' -> out.append(src[i])
                            ',', ';', '!', ' ' -> Unit
                            else -> throw FunctionGraphException("不支持的 LaTeX 命令:\\${src[i]}")
                        }
                        i++
                    } else {
                        val nameStart = i
                        while (i < src.length && src[i].isLetter()) i++
                        val name = src.substring(nameStart, i).lowercase(Locale.ROOT)
                        when (name) {
                            "frac" -> {
                                val (a, next1) = readGroup(src, i)
                                    ?: throw FunctionGraphException("\\frac 需要 {分子}{分母}")
                                val (b, next2) = readGroup(src, next1)
                                    ?: throw FunctionGraphException("\\frac 需要 {分子}{分母}")
                                out.append("((").append(convertLatex(a)).append(")/(")
                                    .append(convertLatex(b)).append("))")
                                i = next2
                            }

                            "sqrt" -> {
                                var j = i
                                while (j < src.length && src[j].isWhitespace()) j++
                                var index: String? = null
                                if (j < src.length && src[j] == '[') {
                                    val (raw, next) = readDelimited(src, j, '[', ']')
                                        ?: throw FunctionGraphException("\\sqrt[n] 缺少右括号")
                                    index = raw
                                    j = next
                                }
                                val (body, next) = readGroup(src, j)
                                    ?: throw FunctionGraphException("\\sqrt 需要 {表达式}")
                                if (index == null) {
                                    out.append("sqrt(").append(convertLatex(body)).append(")")
                                } else {
                                    out.append("((").append(convertLatex(body)).append(")^(1/(")
                                        .append(convertLatex(index)).append(")))")
                                }
                                i = next
                            }

                            "sin", "cos", "tan", "asin", "acos", "atan", "sinh", "cosh", "tanh",
                            "ln", "log", "log2", "exp", "abs", "floor", "ceil", "round",
                            "min", "max", "pow", "cbrt", "sign",
                            -> out.append(name)

                            "pi" -> out.append("pi")
                            "cdot", "times" -> out.append('*')
                            "div" -> out.append('/')
                            "operatorname", "mathrm", "text" -> {
                                val (body, next) = readGroup(src, i)
                                    ?: throw FunctionGraphException("\\$name 需要 {…}")
                                out.append(convertLatex(body))
                                i = next
                            }

                            else -> throw FunctionGraphException("不支持的 LaTeX 命令:\\$name")
                        }
                    }
                }

                c == '^' -> {
                    out.append('^')
                    i++
                    if (i < src.length && src[i] == '{') {
                        val (body, next) = readDelimited(src, i, '{', '}')
                            ?: throw FunctionGraphException("上标缺少右花括号")
                        out.append('(').append(convertLatex(body)).append(')')
                        i = next
                    }
                }

                c == '_' -> throw FunctionGraphException("不支持下标;请用普通变量 x")
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    /** 跳过空白后读取 {…};返回内容与结束位置 */
    private fun readGroup(src: String, from: Int): Pair<String, Int>? {
        var i = from
        while (i < src.length && src[i].isWhitespace()) i++
        if (i >= src.length || src[i] != '{') return null
        return readDelimited(src, i, '{', '}')
    }

    private fun readDelimited(src: String, start: Int, open: Char, close: Char): Pair<String, Int>? {
        var depth = 0
        var i = start
        while (i < src.length) {
            when (src[i]) {
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return src.substring(start + 1, i) to i + 1
                }
            }
            i++
        }
        return null
    }
}

object FunctionGraphSampler {

    private const val DEFAULT_SAMPLES = 900

    /**
     * 采样并断段:定义域错误/NaN/Infinity 立即断开;
     * 相邻点 y 跳变超过视口高度的 1.5 倍也断开,避免渐近线被竖线连起来。
     */
    fun segments(
        spec: FunctionGraphSpec,
        curveIndex: Int,
        samples: Int = DEFAULT_SAMPLES,
    ): List<List<GraphSamplePoint>> {
        val curve = spec.curves.getOrNull(curveIndex) ?: return emptyList()
        val viewport = spec.viewport
        val steps = samples.coerceIn(2, 8000)
        val dx = (viewport.xMax - viewport.xMin) / steps
        val breakJump = (viewport.yMax - viewport.yMin) * 1.5
        val result = ArrayList<List<GraphSamplePoint>>()
        var current = ArrayList<GraphSamplePoint>()
        var previousY: Double? = null

        fun flush() {
            if (current.size >= 2) result += current
            current = ArrayList()
            previousY = null
        }

        for (i in 0..steps) {
            val x = viewport.xMin + dx * i
            val y = try {
                MathEvaluator.evaluateAt(curve.expression, "x", x).value
            } catch (_: MathEvaluator.EvaluationException) {
                Double.NaN
            } catch (_: RuntimeException) {
                Double.NaN
            }
            if (!y.isFinite()) {
                flush()
                continue
            }
            val prev = previousY
            if (prev != null && kotlin.math.abs(y - prev) > breakJump) flush()
            current.add(GraphSamplePoint(x, y))
            previousY = y
        }
        flush()
        return result
    }
}
