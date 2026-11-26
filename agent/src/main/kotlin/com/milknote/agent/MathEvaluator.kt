package com.milknote.agent

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * 纯 Kotlin 数学计算引擎(递归下降解析,绝不执行系统命令)。
 *
 * 支持:
 * - 多语句(; 或换行分隔)与变量赋值:`a = 3; b = 4; sqrt(a^2 + b^2)`
 * - 运算符 + - * / % ^(右结合) !(阶乘) 与隐式乘法(`2(3+4)` / `2pi`)
 * - 常量 pi / π / e / tau / phi
 * - 常用函数(sin/cos/tan/asin/acos/atan/atan2/双曲/sqrt/cbrt/abs/ln/log/log2/exp/pow/
 *   floor/ceil/round/min/max/sum/avg/mean/gcd/lcm/comb/nCr/perm/nPr/fact/deg/rad/sign)
 * - 数值格式化去除浮点尾差(`0.1 + 0.2` → `0.3`)
 */
data class MathEvalResult(
    val formatted: String,
    val value: Double,
    val variables: Map<String, Double> = emptyMap(),
)

object MathEvaluator {

    class EvaluationException(message: String) : IllegalArgumentException(message)

    /** precision:保留的最大有效小数位(0..15) */
    fun evaluate(expression: String, precision: Int = 10): MathEvalResult {
        if (expression.isBlank()) throw EvaluationException("表达式为空")
        return Parser(expression, precision.coerceIn(0, 15)).parseProgram()
    }

    // ---- tokenizer ----

    private enum class Kind { NUMBER, IDENT, OP, SEP, END }

    private data class Token(val kind: Kind, val text: String, val number: Double = 0.0)

    private fun tokenize(src: String): List<Token> {
        val out = ArrayList<Token>()
        var i = 0
        var depth = 0
        while (i < src.length) {
            val c = src[i]
            when {
                c.isWhitespace() -> {
                    // 换行在顶层=语句分隔;括号内换行只是空白
                    if ((c == '\n' || c == '\r') && depth == 0) out += Token(Kind.SEP, ";")
                    i++
                }
                c == ';' -> {
                    out += Token(Kind.SEP, ";")
                    i++
                }
                c.isDigit() || (c == '.' && i + 1 < src.length && src[i + 1].isDigit()) -> {
                    val start = i
                    while (i < src.length && src[i].isDigit()) i++
                    if (i < src.length && src[i] == '.') {
                        i++
                        while (i < src.length && src[i].isDigit()) i++
                    }
                    // 科学计数法:仅当 e/E 后确有数字才吞掉,否则 e 仍按常量解析(2e = 2*e)
                    if (i < src.length && (src[i] == 'e' || src[i] == 'E')) {
                        var j = i + 1
                        if (j < src.length && (src[j] == '+' || src[j] == '-')) j++
                        if (j < src.length && src[j].isDigit()) {
                            i = j
                            while (i < src.length && src[i].isDigit()) i++
                        }
                    }
                    val text = src.substring(start, i)
                    val v = text.toDoubleOrNull() ?: throw EvaluationException("无法解析数字:$text")
                    out += Token(Kind.NUMBER, text, v)
                }
                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_')) i++
                    out += Token(Kind.IDENT, src.substring(start, i))
                }
                c in "+-*/%^!(),=" -> {
                    if (c == '(') depth++ else if (c == ')') depth = (depth - 1).coerceAtLeast(0)
                    out += Token(Kind.OP, c.toString())
                    i++
                }
                else -> throw EvaluationException("无法识别的字符:$c")
            }
        }
        out += Token(Kind.END, "")
        return out
    }

    // ---- parser ----

    private class Parser(private val src: String, private val precision: Int) {
        private val tokens = tokenize(src)
        private var pos = 0
        private val vars = LinkedHashMap<String, Double>()

        fun parseProgram(): MathEvalResult {
            var result: MathEvalResult? = null
            while (true) {
                while (peek().kind == Kind.SEP) pos++
                if (peek().kind == Kind.END) break
                result = parseStatement()
            }
            return result ?: throw EvaluationException("表达式为空")
        }

        private fun parseStatement(): MathEvalResult {
            val first = peek()
            if (first.kind == Kind.IDENT && peek(1).let { it.kind == Kind.OP && it.text == "=" }) {
                val name = first.text
                if (name.lowercase() in CONSTANTS || name.lowercase() in FUNCTIONS) {
                    throw EvaluationException("不能给常量或函数赋值:$name")
                }
                pos += 2
                val value = parseExpression()
                ensureFinite(value)
                vars[name] = value
                return MathEvalResult(format(value), value, LinkedHashMap(vars))
            }
            val value = parseExpression()
            ensureFinite(value)
            return MathEvalResult(format(value), value, LinkedHashMap(vars))
        }

        private fun parseExpression(): Double = parseAddSub()

        private fun parseAddSub(): Double {
            var v = parseMulDiv()
            while (true) {
                val t = peek()
                if (t.kind == Kind.OP && (t.text == "+" || t.text == "-")) {
                    pos++
                    val r = parseMulDiv()
                    v = if (t.text == "+") v + r else v - r
                } else {
                    break
                }
            }
            return v
        }

        private fun parseMulDiv(): Double {
            var v = parseUnary()
            while (true) {
                val t = peek()
                if (t.kind == Kind.OP && (t.text == "*" || t.text == "/" || t.text == "%")) {
                    pos++
                    val r = parseUnary()
                    v = when (t.text) {
                        "*" -> v * r
                        "/" -> {
                            if (r == 0.0) throw EvaluationException("除数不能为零")
                            v / r
                        }
                        else -> {
                            if (r == 0.0) throw EvaluationException("取模的除数不能为零")
                            v % r
                        }
                    }
                } else if (startsFactor(t)) {
                    // 隐式乘法:2(3+4) / 2pi / 3!2
                    v *= parseUnary()
                } else {
                    break
                }
            }
            return v
        }

        private fun startsFactor(t: Token): Boolean = when (t.kind) {
            Kind.NUMBER, Kind.IDENT -> true
            Kind.OP -> t.text == "("
            else -> false
        }

        private fun parseUnary(): Double {
            val t = peek()
            if (t.kind == Kind.OP && (t.text == "+" || t.text == "-")) {
                pos++
                val v = parseUnary()
                return if (t.text == "-") -v else v
            }
            return parsePower()
        }

        private fun parsePower(): Double {
            val base = parsePostfix()
            val t = peek()
            if (t.kind == Kind.OP && t.text == "^") {
                pos++
                // 右结合,且允许指数带一元负号:2^-1
                return Math.pow(base, parseUnary())
            }
            return base
        }

        private fun parsePostfix(): Double {
            var v = parsePrimary()
            while (peek().kind == Kind.OP && peek().text == "!") {
                pos++
                v = factorial(v)
            }
            return v
        }

        private fun parsePrimary(): Double {
            val t = peek()
            return when {
                t.kind == Kind.NUMBER -> {
                    pos++
                    t.number
                }
                t.kind == Kind.IDENT -> parseIdentifier(t.text)
                t.kind == Kind.OP && t.text == "(" -> {
                    pos++
                    val v = parseExpression()
                    expect(")")
                    v
                }
                else -> throw EvaluationException(
                    if (t.kind == Kind.END) "表达式不完整" else "非法记号:${t.text}",
                )
            }
        }

        private fun parseIdentifier(name: String): Double {
            pos++
            val key = name.lowercase()
            val fn = FUNCTIONS[key]
            if (fn != null) {
                if (!(peek().kind == Kind.OP && peek().text == "(")) {
                    throw EvaluationException("函数 $name 需要参数括号")
                }
                pos++
                val args = ArrayList<Double>()
                if (!(peek().kind == Kind.OP && peek().text == ")")) {
                    while (true) {
                        args += parseExpression()
                        if (peek().kind == Kind.OP && peek().text == ",") {
                            pos++
                        } else {
                            break
                        }
                    }
                }
                expect(")")
                return fn.invoke(args)
            }
            CONSTANTS[key]?.let { return it }
            vars[name]?.let { return it }
            vars[key]?.let { return it }
            if (peek().kind == Kind.OP && peek().text == "(") {
                throw EvaluationException("未知函数:$name")
            }
            throw EvaluationException("未知变量:$name")
        }

        private fun expect(op: String) {
            val t = peek()
            if (t.kind == Kind.OP && t.text == op) {
                pos++
            } else {
                throw EvaluationException("缺少「$op」")
            }
        }

        private fun peek(offset: Int = 0): Token = tokens[(pos + offset).coerceAtMost(tokens.lastIndex)]

        private fun format(value: Double): String = formatNumber(value, precision)
    }

    // ---- values & functions ----

    private val CONSTANTS: Map<String, Double> = mapOf(
        "pi" to Math.PI,
        "π" to Math.PI,
        "e" to Math.E,
        "tau" to 2 * Math.PI,
        "phi" to (1 + Math.sqrt(5.0)) / 2,
    )

    private const val MAX_FACTORIAL = 170.0

    private fun unary(name: String, f: (Double) -> Double): (List<Double>) -> Double = { args ->
        if (args.size != 1) throw EvaluationException("$name 需要 1 个参数")
        f(args[0])
    }

    private fun binary(name: String, f: (Double, Double) -> Double): (List<Double>) -> Double = { args ->
        if (args.size != 2) throw EvaluationException("$name 需要 2 个参数")
        f(args[0], args[1])
    }

    private fun variadic(
        name: String,
        minArgs: Int,
        f: (List<Double>) -> Double,
    ): (List<Double>) -> Double = { args ->
        if (args.size < minArgs) throw EvaluationException("$name 至少需要 $minArgs 个参数")
        f(args)
    }

    private fun positive(name: String, v: Double): Double {
        if (v <= 0) throw EvaluationException("$name 的参数必须大于 0")
        return v
    }

    private val FUNCTIONS: Map<String, (List<Double>) -> Double> = buildMap {
        put("sin", unary("sin") { Math.sin(it) })
        put("cos", unary("cos") { Math.cos(it) })
        put("tan", unary("tan") { Math.tan(it) })
        put("asin", unary("asin") { v ->
            if (v < -1 || v > 1) throw EvaluationException("asin 的参数必须在 [-1, 1]")
            Math.asin(v)
        })
        put("acos", unary("acos") { v ->
            if (v < -1 || v > 1) throw EvaluationException("acos 的参数必须在 [-1, 1]")
            Math.acos(v)
        })
        put("atan", unary("atan") { Math.atan(it) })
        put("atan2", binary("atan2") { y, x -> Math.atan2(y, x) })
        put("sinh", unary("sinh") { Math.sinh(it) })
        put("cosh", unary("cosh") { Math.cosh(it) })
        put("tanh", unary("tanh") { Math.tanh(it) })
        put("sqrt", unary("sqrt") { v ->
            if (v < 0) throw EvaluationException("sqrt 的参数不能为负数")
            Math.sqrt(v)
        })
        put("cbrt", unary("cbrt") { Math.cbrt(it) })
        put("abs", unary("abs") { Math.abs(it) })
        put("ln", unary("ln") { Math.log(positive("ln", it)) })
        put("log2", unary("log2") { v -> Math.log(positive("log2", v)) / Math.log(2.0) })
        put("exp", unary("exp") { Math.exp(it) })
        put("pow", binary("pow") { a, b -> Math.pow(a, b) })
        put("floor", unary("floor") { Math.floor(it) })
        put("ceil", unary("ceil") { Math.ceil(it) })
        put("round", unary("round") { Math.round(it).toDouble() })
        put("sign", unary("sign") { Math.signum(it) })
        put("deg", unary("deg") { Math.toDegrees(it) })
        put("rad", unary("rad") { Math.toRadians(it) })
        put("fact", unary("fact") { factorial(it) })
        put("log", { args ->
            when (args.size) {
                1 -> Math.log10(positive("log", args[0]))
                2 -> {
                    positive("log", args[0])
                    val base = args[1]
                    if (base <= 0 || base == 1.0) throw EvaluationException("log 的底数必须大于 0 且不等于 1")
                    Math.log(args[0]) / Math.log(base)
                }
                else -> throw EvaluationException("log 需要 1 或 2 个参数")
            }
        })
        put("min", variadic("min", 1) { it.min() })
        put("max", variadic("max", 1) { it.max() })
        put("sum", variadic("sum", 1) { it.sum() })
        put("avg", variadic("avg", 1) { it.average() })
        put("mean", variadic("mean", 1) { it.average() })
        put("gcd", variadic("gcd", 1) { gcdOf(it) })
        put("lcm", variadic("lcm", 1) { lcmOf(it) })
        put("comb", binary("comb") { n, k -> combOf(n, k) })
        put("ncr", binary("nCr") { n, k -> combOf(n, k) })
        put("perm", binary("perm") { n, k -> permOf(n, k) })
        put("npr", binary("nPr") { n, k -> permOf(n, k) })
    }

    private fun factorial(v: Double): Double {
        if (!v.isFinite() || v < 0 || v != Math.floor(v)) {
            throw EvaluationException("阶乘只支持非负整数")
        }
        if (v > MAX_FACTORIAL) throw EvaluationException("阶乘参数过大(最大 170)")
        var r = 1.0
        var i = 2
        val n = v.toInt()
        while (i <= n) {
            r *= i
            i++
        }
        return r
    }

    private fun intOf(name: String, v: Double): Long {
        if (!v.isFinite() || v != Math.floor(v) || abs(v) > 9.007199254740992E15) {
            throw EvaluationException("$name 只支持整数")
        }
        return v.toLong()
    }

    private fun gcdOf(values: List<Double>): Double {
        var g = abs(intOf("gcd", values[0]))
        for (i in 1 until values.size) {
            var a = g
            var b = abs(intOf("gcd", values[i]))
            while (b != 0L) {
                val t = a % b
                a = b
                b = t
            }
            g = a
        }
        return g.toDouble()
    }

    private fun lcmOf(values: List<Double>): Double {
        var l = abs(intOf("lcm", values[0]))
        for (i in 1 until values.size) {
            val b = abs(intOf("lcm", values[i]))
            if (l == 0L || b == 0L) {
                l = 0L
                continue
            }
            var a = l
            var c = b
            while (c != 0L) {
                val t = a % c
                a = c
                c = t
            }
            l = l / a * b
        }
        return l.toDouble()
    }

    private fun combOf(nv: Double, kv: Double): Double {
        val n = intOf("comb", nv)
        val k = intOf("comb", kv)
        if (n < 0 || k < 0 || k > n) throw EvaluationException("comb/nCr 要求 0 ≤ k ≤ n")
        val kk = minOf(k, n - k)
        var r = 1.0
        var i = 1L
        while (i <= kk) {
            r = r * (n - kk + i) / i
            i++
        }
        return if (r < 9.007199254740992E15) Math.round(r).toDouble() else r
    }

    private fun permOf(nv: Double, kv: Double): Double {
        val n = intOf("perm", nv)
        val k = intOf("perm", kv)
        if (n < 0 || k < 0 || k > n) throw EvaluationException("perm/nPr 要求 0 ≤ k ≤ n")
        var r = 1.0
        var i = 0L
        while (i < k) {
            r *= (n - i)
            i++
        }
        return r
    }

    private fun ensureFinite(v: Double) {
        if (!v.isFinite()) throw EvaluationException("计算结果不是有限数值")
    }

    /** 去除浮点尾差:整数直出;小数按 precision 四舍五入后去尾随零 */
    internal fun formatNumber(value: Double, precision: Int): String {
        ensureFinite(value)
        if (value == 0.0) return "0"
        if (value == Math.rint(value) && abs(value) < 9.007199254740992E15) {
            return value.toLong().toString()
        }
        val p = precision.coerceIn(0, 15)
        val rounded = BigDecimal.valueOf(value).setScale(p, RoundingMode.HALF_UP).stripTrailingZeros()
        return if (rounded.scale() < 0) rounded.toBigInteger().toString() else rounded.toPlainString()
    }
}
