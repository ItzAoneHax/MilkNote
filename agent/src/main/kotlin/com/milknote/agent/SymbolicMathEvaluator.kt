package com.milknote.agent

import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.abs

/**
 * 符号数学引擎(纯 Kotlin:不执行系统命令、不联网、不引入第三方依赖)。
 *
 * 与 [MathEvaluator] 的分工:
 * - [MathEvaluator] 继续负责 `evaluate` 的数值计算,行为完全不变;
 * - 本引擎负责 `simplify` / `solve` / `derivative` / `integrate` / `definite_integral`。
 *
 * 系数使用任意精度有理数 [Rat],二次方程判别式与高斯消元都不靠 Double 判等。
 * 所有输入先做长度/嵌套/节点数校验,展开项数、多项式次数、方程组规模均有上限。
 */
object SymbolicMathEvaluator {

    class SymbolicException(message: String) : IllegalArgumentException(message)

    private const val MAX_INPUT_CHARS = 2000
    private const val MAX_NODES = 20_000
    private const val MAX_DEPTH = 64
    private const val MAX_EXPAND_TERMS = 200
    private const val MAX_POLY_DEGREE = 20
    private const val MAX_SYSTEM_VARS = 12
    private const val MAX_SYSTEM_EQS = 12

    // ==================== 公开入口 ====================

    /** 符号化简(展开简单乘积并合并同类项),返回可再次输入的纯文本表达式 */
    fun simplify(expression: String): String {
        val expr = parseSingle(expression)
        return render(simplifyExpr(expr))
    }

    /** 求导并化简,例如 x^3+sin(x) → 3*x^2+cos(x) */
    fun derivative(expression: String, variable: String): String {
        val v = validVariable(variable)
        val expr = parseSingle(expression)
        return render(simplifyExpr(deriv(expr, v)))
    }

    /** 不定积分(带 + C),只做符号积分,不做数值近似 */
    fun integrate(expression: String, variable: String): String {
        val v = validVariable(variable)
        val expr = parseSingle(expression)
        val anti = integrateExpr(expr, v) ?: throw SymbolicException(unsupportedIntegralMessage())
        val text = render(simplifyExpr(anti))
        return if (text == "0") "C" else "$text+C"
    }

    /** 定积分:先求原函数再代入上下限,优先输出精确值,必要时附数值 */
    fun definiteIntegral(
        expression: String,
        variable: String,
        lower: String,
        upper: String,
        precision: Int,
    ): String {
        val v = validVariable(variable)
        val expr = parseSingle(expression)
        val lo = constantExpr(lower, "下限")
        val hi = constantExpr(upper, "上限")
        checkIntegralDomain(expr, v, lo, hi)
        val anti = integrateExpr(expr, v) ?: throw SymbolicException(unsupportedIntegralMessage())
        val atHi = simplifyExpr(substitute(anti, v, hi))
        val atLo = simplifyExpr(substitute(anti, v, lo))
        val diff = simplifyExpr(
            Expr.Add(listOf(atHi, Expr.Mul(listOf(Expr.Num(Rat.MINUS_ONE), atLo)))),
        )
        if (diff is Expr.Num) return diff.v.toString()
        val symbolic = render(diff)
        val numeric = runCatching { evalDouble(diff) }.getOrNull()
        return if (numeric != null && numeric.isFinite()) {
            "$symbolic ≈ ${MathEvaluator.formatNumber(numeric, precision.coerceIn(0, 15))}"
        } else {
            symbolic
        }
    }

    /**
     * 解方程:
     * - 单方程用 [variable](默认 x),支持一元一次/二次;
     * - 方程组在 expression 内用分号/换行分隔,必须给 [variables](例如 x,y),高斯消元求线性方程组。
     */
    fun solve(expression: String, variable: String?, variables: List<String>?): String {
        val equations = parseEquations(expression)
        equations.forEach { (lhs, rhs) ->
            ensureSize(lhs)
            rhs?.let { ensureSize(it) }
        }
        if (equations.size > 1) {
            val names = variables
                ?: throw SymbolicException("方程组求解需要 variables 参数(逗号分隔,例如 \"x,y\")")
            return solveSystem(equations, names)
        }
        if (variables != null && variables.isEmpty()) throw SymbolicException("variables 不能为空")
        val target = when {
            variables != null && variables.size > 1 ->
                throw SymbolicException("单个方程请用 variable 指定一个变量;多条方程请用分号分隔")
            variables != null && variables.size == 1 -> variables[0]
            !variable.isNullOrBlank() -> variable
            else -> "x"
        }
        val v = validVariable(target)
        val (lhs, rhs) = equations[0]
        val expr = simplifyExpr(
            Expr.Add(listOf(lhs, Expr.Mul(listOf(Expr.Num(Rat.MINUS_ONE), rhs ?: Expr.Num(Rat.ZERO))))),
        )
        return solveSingle(expr, v)
    }

    // ==================== 方程求解 ====================

    private fun solveSingle(expr: Expr, v: String): String {
        val others = freeVars(expr) - v
        if (others.isNotEmpty()) {
            throw SymbolicException(
                "方程含其他变量:${others.joinToString(", ")};一元方程请只保留 $v",
            )
        }
        val coeffs = polyCoeffs(expr, v)
            ?: throw SymbolicException(
                "暂不支持该方程:需要关于 $v 的有理系数多项式(例如 2*x+3=7、x^2-5*x+6=0)",
            )
        return when (coeffs.lastIndex) {
            0 -> if (coeffs[0].isZero) {
                "方程有无数个解($v 可任意取值)"
            } else {
                "方程无解"
            }
            1 -> {
                val a = coeffs[1]
                val b = coeffs[0]
                val root = (-b) / a
                "$v = $root"
            }
            2 -> quadratic(coeffs[0], coeffs[1], coeffs[2], v)
            else -> throw SymbolicException("只支持一元一次/二次方程,当前次数为 ${coeffs.lastIndex}")
        }
    }

    private fun quadratic(c0: Rat, c1: Rat, c2: Rat, v: String): String {
        val a = c2
        val b = c1
        val c = c0
        val disc = b * b - Rat.of(4) * a * c
        val twoA = Rat.of(2) * a
        if (disc.isZero) {
            return "$v = ${(-b) / twoA}(重根)"
        }
        if (disc.signum > 0) {
            val sqrtDisc = exactSqrt(disc)
            if (sqrtDisc != null) {
                val r1 = (-b - sqrtDisc) / twoA
                val r2 = (-b + sqrtDisc) / twoA
                val low = if (r1 <= r2) r1 else r2
                val high = if (r1 <= r2) r2 else r1
                return "$v = $low 或 $v = $high"
            }
            val root: Expr = Expr.Pow(Expr.Num(disc), Expr.Num(Rat.of(1, 2)))
            val first = simplifyExpr(
                Expr.Mul(
                    listOf(
                        Expr.Add(listOf(Expr.Num(-b), Expr.Mul(listOf(Expr.Num(Rat.MINUS_ONE), root)))),
                        Expr.Num(Rat.ONE / twoA),
                    ),
                ),
            )
            val second = simplifyExpr(
                Expr.Mul(listOf(Expr.Add(listOf(Expr.Num(-b), root)), Expr.Num(Rat.ONE / twoA))),
            )
            val n1 = runCatching { evalDouble(first) }.getOrNull()
            val n2 = runCatching { evalDouble(second) }.getOrNull()
            val (lo, hi) = if (n1 != null && n2 != null && n1 > n2) second to first else first to second
            return "$v = ${render(lo)} 或 $v = ${render(hi)}"
        }
        // 共轭复根:a x^2+b x+c=0 → re ± im*i
        val re = (-b) / twoA
        val imSq = -disc
        val imExact = exactSqrt(imSq)
        val im: Expr = if (imExact != null) {
            Expr.Num(imExact / (Rat.of(2) * a.absValue()))
        } else {
            simplifyExpr(
                Expr.Mul(
                    listOf(
                        Expr.Pow(Expr.Num(imSq), Expr.Num(Rat.of(1, 2))),
                        Expr.Num(Rat.ONE / (Rat.of(2) * a.absValue())),
                    ),
                ),
            )
        }
        val imText = renderImaginary(im)
        return if (re.isZero) {
            "$v = ±$imText"
        } else {
            "$v = $re ± $imText"
        }
    }

    private fun renderImaginary(im: Expr): String =
        if (im is Expr.Num && im.v == Rat.ONE) "i" else "${render(im)}*i"

    private fun solveSystem(equations: List<Pair<Expr, Expr?>>, variables: List<String>): String {
        if (equations.size > MAX_SYSTEM_EQS) {
            throw SymbolicException("方程组最多 $MAX_SYSTEM_EQS 个方程")
        }
        val vars = variables.map { validVariable(it) }
        if (vars.isEmpty()) throw SymbolicException("variables 不能为空")
        if (vars.size > MAX_SYSTEM_VARS) throw SymbolicException("变量最多 $MAX_SYSTEM_VARS 个")
        if (vars.toSet().size != vars.size) throw SymbolicException("variables 中有重复变量")

        val n = vars.size
        val rows = ArrayList<Array<Rat>>()
        for ((lhs, rhs) in equations) {
            val expr = simplifyExpr(
                Expr.Add(listOf(lhs, Expr.Mul(listOf(Expr.Num(Rat.MINUS_ONE), rhs ?: Expr.Num(Rat.ZERO))))),
            )
            val linear = linearForm(expr, vars)
                ?: throw SymbolicException(
                    "方程组只支持关于 ${vars.joinToString(",")} 的线性方程;请先展开化简",
                )
            rows += Array(n + 1) { i -> if (i < n) linear.first[i] else linear.second.negate() }
        }

        // 高斯-约当消元
        var rank = 0
        val pivotCol = IntArray(rows.size) { -1 }
        for (col in 0 until n) {
            var pivotRow = -1
            for (i in rank until rows.size) {
                if (rows[i][col].signum != 0) {
                    pivotRow = i
                    break
                }
            }
            if (pivotRow < 0) continue
            val swap = rows[rank]
            rows[rank] = rows[pivotRow]
            rows[pivotRow] = swap
            val pivot = rows[rank][col]
            for (j in col..n) rows[rank][j] = rows[rank][j] / pivot
            for (i in rows.indices) {
                if (i == rank) continue
                val factor = rows[i][col]
                if (factor.signum == 0) continue
                for (j in col..n) rows[i][j] = rows[i][j] - factor * rows[rank][j]
            }
            pivotCol[rank] = col
            rank++
        }

        for (row in rows) {
            val allZero = (0 until n).all { row[it].signum == 0 }
            if (allZero && row[n].signum != 0) return "方程组无解"
        }
        if (rank < n) return "方程组有无数个解"

        val values = Array(n) { Rat.ZERO }
        for (r in 0 until rank) values[pivotCol[r]] = rows[r][n]
        return vars.indices.joinToString(", ") { "${vars[it]} = ${values[it]}" }
    }

    // ==================== 多项式 ====================

    /** 关于 [v] 的有理系数多项式,下标为次数;不是多项式时返回 null */
    private fun polyCoeffs(e: Expr, v: String, depth: Int = 0): List<Rat>? {
        if (depth > MAX_DEPTH) return null
        val result = when (e) {
            is Expr.Num -> listOf(e.v)
            is Expr.Const -> return null
            is Expr.Var -> if (e.name == v) listOf(Rat.ZERO, Rat.ONE) else return null
            is Expr.Add -> {
                var acc = listOf(Rat.ZERO)
                for (term in e.terms) {
                    val part = polyCoeffs(term, v, depth + 1) ?: return null
                    acc = addPoly(acc, part)
                    if (acc.size > MAX_POLY_DEGREE + 1) return null
                }
                acc
            }
            is Expr.Mul -> {
                var acc = listOf(Rat.ONE)
                for (factor in e.factors) {
                    val part = polyCoeffs(factor, v, depth + 1) ?: return null
                    acc = mulPoly(acc, part)
                    if (acc.size > MAX_POLY_DEGREE + 1) return null
                }
                acc
            }
            is Expr.Pow -> {
                val exp = e.exp
                if (exp !is Expr.Num || !exp.v.isInteger || exp.v.signum < 0) return null
                if (exp.v.n.bitLength() >= 31) return null
                val k = exp.v.n.toInt()
                if (k > MAX_POLY_DEGREE) return null
                val base = polyCoeffs(e.base, v, depth + 1) ?: return null
                var acc = listOf(Rat.ONE)
                repeat(k) {
                    acc = mulPoly(acc, base)
                    if (acc.size > MAX_POLY_DEGREE + 1) return null
                }
                acc
            }
            is Expr.Fun -> return null
        }
        return result.trimZeros()
    }

    private fun addPoly(a: List<Rat>, b: List<Rat>): List<Rat> {
        val size = maxOf(a.size, b.size)
        return List(size) { i ->
            (if (i < a.size) a[i] else Rat.ZERO) + (if (i < b.size) b[i] else Rat.ZERO)
        }
    }

    private fun mulPoly(a: List<Rat>, b: List<Rat>): List<Rat> {
        val out = MutableList(a.size + b.size - 1) { Rat.ZERO }
        for (i in a.indices) {
            if (a[i].isZero) continue
            for (j in b.indices) {
                if (b[j].isZero) continue
                out[i + j] += a[i] * b[j]
            }
        }
        return out
    }

    private fun List<Rat>.trimZeros(): List<Rat> {
        var end = size
        while (end > 1 && this[end - 1].isZero) end--
        return subList(0, end).toList()
    }

    /** 线性形式:expr = Σ coeffs[i]*vars[i] + constant;含非线性项时返回 null */
    private fun linearForm(e: Expr, vars: List<String>): Pair<List<Rat>, Rat>? {
        val coeffs = MutableList(vars.size) { Rat.ZERO }
        var constant = Rat.ZERO
        val terms = if (e is Expr.Add) e.terms else listOf(e)
        for (term in terms) {
            when (term) {
                is Expr.Num -> constant += term.v
                is Expr.Var -> {
                    val idx = vars.indexOf(term.name)
                    if (idx < 0) return null
                    coeffs[idx] += Rat.ONE
                }
                is Expr.Mul -> {
                    var coef = Rat.ONE
                    var varIdx = -1
                    for (factor in term.factors) {
                        when (factor) {
                            is Expr.Num -> coef *= factor.v
                            is Expr.Var -> {
                                val idx = vars.indexOf(factor.name)
                                if (idx < 0 || varIdx >= 0) return null
                                varIdx = idx
                            }
                            else -> return null
                        }
                    }
                    if (varIdx < 0) constant += coef else coeffs[varIdx] += coef
                }
                else -> return null
            }
        }
        return coeffs to constant
    }

    // ==================== 求导 ====================

    private fun deriv(e: Expr, v: String): Expr = when (e) {
        is Expr.Num, is Expr.Const -> Expr.Num(Rat.ZERO)
        is Expr.Var -> Expr.Num(if (e.name == v) Rat.ONE else Rat.ZERO)
        is Expr.Add -> simplifyAdd(e.terms.map { deriv(it, v) })
        is Expr.Mul -> simplifyAdd(
            e.factors.mapIndexed { i, _ ->
                simplifyMul(
                    e.factors.mapIndexed { j, factor -> if (i == j) deriv(factor, v) else factor },
                )
            },
        )
        is Expr.Pow -> derivPow(e.base, e.exp, v)
        is Expr.Fun -> derivFun(e.name, e.args, v)
    }

    private fun derivPow(base: Expr, exp: Expr, v: String): Expr {
        if (!containsVar(exp, v)) {
            val dBase = deriv(base, v)
            if (dBase.isZeroNum()) return Expr.Num(Rat.ZERO)
            return simplifyMul(
                listOf(exp, simplifyPow(base, simplifyAdd(listOf(exp, Expr.Num(Rat.MINUS_ONE)))), dBase),
            )
        }
        if (!containsVar(base, v)) {
            return simplifyMul(
                listOf(simplifyPow(base, exp), simplifyFun("ln", listOf(base)), deriv(exp, v)),
            )
        }
        val dExp = deriv(exp, v)
        val dBase = deriv(base, v)
        val inner = simplifyAdd(
            listOf(
                simplifyMul(listOf(dExp, simplifyFun("ln", listOf(base)))),
                simplifyMul(listOf(exp, dBase, simplifyPow(base, Expr.Num(Rat.MINUS_ONE)))),
            ),
        )
        return simplifyMul(listOf(simplifyPow(base, exp), inner))
    }

    private fun derivFun(name: String, args: List<Expr>, v: String): Expr {
        if (args.size != 1) throw SymbolicException("暂不支持对多元函数 $name 求导")
        val u = args[0]
        val du = deriv(u, v)
        return when (name) {
            "sin" -> simplifyMul(listOf(simplifyFun("cos", listOf(u)), du))
            "cos" -> simplifyMul(
                listOf(Expr.Num(Rat.MINUS_ONE), simplifyFun("sin", listOf(u)), du),
            )
            "tan" -> simplifyMul(
                listOf(du, simplifyPow(simplifyFun("cos", listOf(u)), Expr.Num(Rat.of(-2)))),
            )
            "exp" -> simplifyMul(listOf(simplifyFun("exp", listOf(u)), du))
            "ln" -> simplifyMul(listOf(du, simplifyPow(u, Expr.Num(Rat.MINUS_ONE))))
            "log" -> simplifyMul(
                listOf(
                    du,
                    simplifyPow(u, Expr.Num(Rat.MINUS_ONE)),
                    simplifyPow(
                        simplifyFun("ln", listOf(Expr.Num(Rat.of(10)))),
                        Expr.Num(Rat.MINUS_ONE),
                    ),
                ),
            )
            "abs" -> simplifyMul(listOf(du, Expr.Fun("sign", listOf(u))))
            "sign" -> Expr.Num(Rat.ZERO)
            "asin" -> simplifyMul(
                listOf(
                    du,
                    simplifyPow(
                        simplifyAdd(
                            listOf(Expr.Num(Rat.ONE), simplifyMul(listOf(Expr.Num(Rat.MINUS_ONE), simplifyPow(u, Expr.Num(Rat.of(2)))))),
                        ),
                        Expr.Num(Rat.of(-1, 2)),
                    ),
                ),
            )
            "acos" -> simplifyMul(
                listOf(
                    Expr.Num(Rat.MINUS_ONE),
                    simplifyMul(
                        listOf(
                            du,
                            simplifyPow(
                                simplifyAdd(
                                    listOf(Expr.Num(Rat.ONE), simplifyMul(listOf(Expr.Num(Rat.MINUS_ONE), simplifyPow(u, Expr.Num(Rat.of(2)))))),
                                ),
                                Expr.Num(Rat.of(-1, 2)),
                            ),
                        ),
                    ),
                ),
            )
            "atan" -> simplifyMul(
                listOf(
                    du,
                    simplifyPow(
                        simplifyAdd(listOf(Expr.Num(Rat.ONE), simplifyPow(u, Expr.Num(Rat.of(2))))),
                        Expr.Num(Rat.MINUS_ONE),
                    ),
                ),
            )
            "sinh" -> simplifyMul(listOf(simplifyFun("cosh", listOf(u)), du))
            "cosh" -> simplifyMul(listOf(simplifyFun("sinh", listOf(u)), du))
            "tanh" -> simplifyMul(
                listOf(du, simplifyPow(simplifyFun("cosh", listOf(u)), Expr.Num(Rat.of(-2)))),
            )
            else -> throw SymbolicException("暂不支持对函数 $name 求导")
        }
    }

    // ==================== 积分 ====================

    private fun integrateExpr(e: Expr, v: String): Expr? {
        if (!containsVar(e, v)) return simplifyMul(listOf(e, Expr.Var(v)))
        return when (e) {
            is Expr.Add -> {
                var acc: Expr = Expr.Num(Rat.ZERO)
                for (term in e.terms) {
                    val part = integrateExpr(term, v) ?: return null
                    acc = simplifyAdd(listOf(acc, part))
                }
                acc
            }
            is Expr.Mul -> {
                val constant = ArrayList<Expr>()
                val varying = ArrayList<Expr>()
                for (factor in e.factors) {
                    if (containsVar(factor, v)) varying += factor else constant += factor
                }
                val coef: Expr = when {
                    constant.isEmpty() -> Expr.Num(Rat.ONE)
                    constant.size == 1 -> constant[0]
                    else -> Expr.Mul(constant)
                }
                when {
                    varying.isEmpty() -> simplifyMul(listOf(coef, Expr.Var(v)))
                    varying.size == 1 -> {
                        val inner = integrateFactor(varying[0], v) ?: return null
                        simplifyMul(listOf(coef, inner))
                    }
                    else -> null
                }
            }
            is Expr.Var -> simplifyMul(
                listOf(Expr.Num(Rat.of(1, 2)), simplifyPow(e, Expr.Num(Rat.of(2)))),
            )
            is Expr.Pow -> integrateFactor(e, v)
            is Expr.Fun -> integrateFactor(e, v)
            is Expr.Num, is Expr.Const -> null
        }
    }

    private fun integrateFactor(e: Expr, v: String): Expr? = when (e) {
        is Expr.Var -> if (e.name == v) {
            simplifyMul(listOf(Expr.Num(Rat.of(1, 2)), simplifyPow(e, Expr.Num(Rat.of(2)))))
        } else {
            null
        }
        is Expr.Pow -> integratePow(e.base, e.exp, v)
        is Expr.Fun -> integrateFun(e.name, e.args, v)
        else -> null
    }

    private fun integratePow(base: Expr, exp: Expr, v: String): Expr? {
        if (containsVar(exp, v)) return null
        val expNum = exp as? Expr.Num
        if (expNum != null) {
            val linear = linearIn(base, v) ?: return null
            val a = linear.first
            if (a.isZero) return null
            if (expNum.v == Rat.MINUS_ONE) {
                return simplifyMul(
                    listOf(
                        Expr.Num(Rat.ONE / a),
                        simplifyFun("ln", listOf(simplifyFun("abs", listOf(base)))),
                    ),
                )
            }
            val newExp = expNum.v + Rat.ONE
            return simplifyMul(
                listOf(
                    Expr.Num(Rat.ONE / (newExp * a)),
                    simplifyPow(base, Expr.Num(newExp)),
                ),
            )
        }
        // a^u(u 线性):∫a^u du = a^u / (u' * ln a)
        val linear = linearIn(exp, v) ?: return null
        val a = linear.first
        if (a.isZero) return null
        return simplifyMul(
            listOf(
                Expr.Num(Rat.ONE / a),
                simplifyPow(base, exp),
                simplifyPow(simplifyFun("ln", listOf(base)), Expr.Num(Rat.MINUS_ONE)),
            ),
        )
    }

    private fun integrateFun(name: String, args: List<Expr>, v: String): Expr? {
        if (args.size != 1) return null
        val u = args[0]
        val linear = linearIn(u, v) ?: return null
        val a = linear.first
        if (a.isZero) return null
        val invA = Expr.Num(Rat.ONE / a)
        return when (name) {
            "sin" -> simplifyMul(listOf(Expr.Num(-Rat.ONE / a), simplifyFun("cos", listOf(u))))
            "cos" -> simplifyMul(listOf(invA, simplifyFun("sin", listOf(u))))
            "exp" -> simplifyMul(listOf(invA, simplifyFun("exp", listOf(u))))
            "ln" -> {
                val absU = simplifyFun("abs", listOf(u))
                simplifyMul(
                    listOf(
                        invA,
                        simplifyAdd(
                            listOf(
                                simplifyMul(listOf(u, simplifyFun("ln", listOf(absU)))),
                                simplifyMul(listOf(Expr.Num(Rat.MINUS_ONE), u)),
                            ),
                        ),
                    ),
                )
            }
            "tan" -> simplifyMul(
                listOf(
                    Expr.Num(-Rat.ONE / a),
                    simplifyFun(
                        "ln",
                        listOf(simplifyFun("abs", listOf(simplifyFun("cos", listOf(u))))),
                    ),
                ),
            )
            else -> null
        }
    }

    /** expr = a*var + b(a 有理、b 与 var 无关);不是一次式时返回 null */
    private fun linearIn(e: Expr, v: String): Pair<Rat, Expr>? {
        if (!containsVar(e, v)) return Rat.ZERO to e
        val terms = if (e is Expr.Add) e.terms else listOf(e)
        var a = Rat.ZERO
        var b: Expr = Expr.Num(Rat.ZERO)
        for (term in terms) {
            if (!containsVar(term, v)) {
                b = simplifyAdd(listOf(b, term))
                continue
            }
            val (coef, rest) = splitCoeff(term)
            if (rest !is Expr.Var || rest.name != v) return null
            a += coef
        }
        if (a.isZero) return null
        return a to b
    }

    private fun unsupportedIntegralMessage(): String =
        "暂不支持符号积分:只能处理多项式、基本初等函数及其线性复合(如 x^2、sin(2*x)、1/x、exp(x))"

    // ==================== 定积分定义域(奇点/无定义点) ====================

    private class IntegralIssue(val detail: String)

    private const val SINGULARITY_EPS = 1e-9

    /**
     * 仅当上下限都能数值求值时才做结构检查:针对当前可符号积分的被积式,
     * 拒绝区间内(含端点)的极点、tan 极点、偶次根号与对数的非定义区间。
     * 不可数值判定的上下限保持原有行为,不做武断拒绝;非线性被积式不在此处判定。
     */
    private fun checkIntegralDomain(integrand: Expr, v: String, lower: Expr, upper: Expr) {
        val lowerValue = constDouble(lower) ?: return
        val upperValue = constDouble(upper) ?: return
        if (!lowerValue.isFinite() || !upperValue.isFinite()) return
        val from = minOf(lowerValue, upperValue)
        val to = maxOf(lowerValue, upperValue)
        val exactLower = exactValue(lower)
        val exactUpper = exactValue(upper)
        val exactFrom = if (exactLower != null && exactUpper != null) minOf(exactLower, exactUpper) else null
        val exactTo = if (exactLower != null && exactUpper != null) maxOf(exactLower, exactUpper) else null
        val issue = findIntegralIssue(integrand, v, from, to, exactFrom, exactTo) ?: return
        val fromText = MathEvaluator.formatNumber(from, 10)
        val toText = MathEvaluator.formatNumber(to, 10)
        throw SymbolicException(
            "定积分区间 [$fromText, $toText] 内存在奇点或无定义点:${issue.detail},不能按常义定积分计算",
        )
    }

    private fun findIntegralIssue(
        e: Expr,
        v: String,
        from: Double,
        to: Double,
        exactFrom: Rat?,
        exactTo: Rat?,
    ): IntegralIssue? {
        when (e) {
            is Expr.Add -> {
                for (term in e.terms) {
                    findIntegralIssue(term, v, from, to, exactFrom, exactTo)?.let { return it }
                }
            }
            is Expr.Mul -> {
                for (factor in e.factors) {
                    findIntegralIssue(factor, v, from, to, exactFrom, exactTo)?.let { return it }
                }
            }
            is Expr.Pow -> {
                findIntegralIssue(e.base, v, from, to, exactFrom, exactTo)?.let { return it }
                findIntegralIssue(e.exp, v, from, to, exactFrom, exactTo)?.let { return it }
                return powIssue(e, v, from, to, exactFrom, exactTo)
            }
            is Expr.Fun -> {
                funIssue(e, v, from, to, exactFrom, exactTo)?.let { return it }
                for (arg in e.args) {
                    findIntegralIssue(arg, v, from, to, exactFrom, exactTo)?.let { return it }
                }
            }
            else -> Unit
        }
        return null
    }

    /** 负幂线性底数的极点 + 偶次根号的非负要求 */
    private fun powIssue(
        e: Expr.Pow,
        v: String,
        from: Double,
        to: Double,
        exactFrom: Rat?,
        exactTo: Rat?,
    ): IntegralIssue? {
        val exp = e.exp as? Expr.Num ?: return null
        val linear = linearIn(e.base, v) ?: return null
        val a = linear.first
        if (a.isZero) return null
        val b = linear.second
        val bValue = constDouble(b) ?: return null
        val rootExact = (b as? Expr.Num)?.let { (-it.v) / a }
        val rootValue = rootExact?.toDouble() ?: (-bValue / a.toDouble())
        if (exp.v.signum < 0 && containsPoint(rootExact, rootValue, exactFrom, exactTo, from, to)) {
            return IntegralIssue("${pointText(v, rootValue)} 处分母/负幂底数为零")
        }
        if (exp.v.d.bitLength() >= 31) return null
        val degree = exp.v.d.toInt()
        if (degree != 1 && degree % 2 == 0) {
            val exactLow = exactLinearLow(a, b, exactFrom, exactTo)
            if (exactLow != null) {
                if (exactLow.signum < 0) {
                    return IntegralIssue("偶次根号内存在负值,被积函数在该区间内无实数值")
                }
            } else {
                val low = minOf(linearValueAt(a, bValue, from), linearValueAt(a, bValue, to))
                if (low < -SINGULARITY_EPS) {
                    return IntegralIssue("偶次根号内存在负值,被积函数在该区间内无实数值")
                }
            }
        }
        return null
    }

    /** tan 的极点(cos 为零)与 ln/log 的非正参数 */
    private fun funIssue(
        e: Expr.Fun,
        v: String,
        from: Double,
        to: Double,
        exactFrom: Rat?,
        exactTo: Rat?,
    ): IntegralIssue? {
        if (e.args.size != 1) return null
        if (e.name != "tan" && e.name != "ln" && e.name != "log") return null
        val linear = linearIn(e.args[0], v) ?: return null
        val a = linear.first
        if (a.isZero) return null
        val b = linear.second
        val bValue = constDouble(b) ?: return null
        if (e.name == "tan") {
            val uFrom = linearValueAt(a, bValue, from)
            val uTo = linearValueAt(a, bValue, to)
            if (!uFrom.isFinite() || !uTo.isFinite()) return null
            val low = minOf(uFrom, uTo)
            val high = maxOf(uFrom, uTo)
            val halfPi = Math.PI / 2
            val kMin = Math.ceil((low - halfPi) / Math.PI - SINGULARITY_EPS)
            val kMax = Math.floor((high - halfPi) / Math.PI + SINGULARITY_EPS)
            if (kMax >= kMin) {
                val x = (halfPi + kMin * Math.PI - bValue) / a.toDouble()
                return IntegralIssue("${pointText(v, x)} 处 tan 无定义(cos 为零)")
            }
            return null
        }
        val exactLow = exactLinearLow(a, b, exactFrom, exactTo)
        if (exactLow != null) {
            if (exactLow.signum <= 0) {
                return IntegralIssue("对数参数在积分区间内出现非正值,被积函数无定义")
            }
        } else {
            val low = minOf(linearValueAt(a, bValue, from), linearValueAt(a, bValue, to))
            if (low <= 0.0) {
                return IntegralIssue("对数参数在积分区间内出现非正值,被积函数无定义")
            }
        }
        return null
    }

    private fun containsPoint(
        exactPoint: Rat?,
        point: Double,
        exactFrom: Rat?,
        exactTo: Rat?,
        from: Double,
        to: Double,
    ): Boolean {
        if (exactPoint != null && exactFrom != null && exactTo != null) {
            return exactPoint >= exactFrom && exactPoint <= exactTo
        }
        if (!point.isFinite()) return false
        val tolerance = SINGULARITY_EPS * maxOf(1.0, abs(from), abs(to), abs(point))
        return point >= from - tolerance && point <= to + tolerance
    }

    /** 上下限/常数项的精确有理值;含 pi、函数等无法精确表示时返回 null */
    private fun exactValue(e: Expr): Rat? {
        return when (e) {
            is Expr.Num -> e.v
            is Expr.Add -> {
                var sum = Rat.ZERO
                for (term in e.terms) {
                    sum += exactValue(term) ?: return null
                }
                sum
            }
            is Expr.Mul -> {
                var product = Rat.ONE
                for (factor in e.factors) {
                    product *= exactValue(factor) ?: return null
                }
                product
            }
            is Expr.Pow -> {
                val base = exactValue(e.base) ?: return null
                val exp = exactValue(e.exp) ?: return null
                ratPow(base, exp)
            }
            else -> null
        }
    }

    /** 线性式 a*x+b 在区间两端的最小值(仅在 a、b 与端点都精确时使用) */
    private fun exactLinearLow(a: Rat, b: Expr, from: Rat?, to: Rat?): Rat? {
        val bValue = (b as? Expr.Num)?.v ?: return null
        if (from == null || to == null) return null
        return minOf(a * from + bValue, a * to + bValue)
    }

    private fun linearValueAt(a: Rat, bValue: Double, x: Double): Double = a.toDouble() * x + bValue

    private fun constDouble(e: Expr): Double? = runCatching { evalDouble(e) }.getOrNull()

    private fun pointText(v: String, value: Double): String =
        "$v = ${MathEvaluator.formatNumber(value, 10)}"

    // ==================== 化简 ====================

    private fun simplifyExpr(e: Expr): Expr = when (e) {
        is Expr.Num, is Expr.Var, is Expr.Const -> e
        is Expr.Add -> simplifyAdd(e.terms.map { simplifyExpr(it) })
        is Expr.Mul -> simplifyMul(e.factors.map { simplifyExpr(it) })
        is Expr.Pow -> simplifyPow(simplifyExpr(e.base), simplifyExpr(e.exp))
        is Expr.Fun -> simplifyFun(e.name, e.args.map { simplifyExpr(it) })
    }

    private fun simplifyAdd(raw: List<Expr>): Expr {
        val terms = ArrayList<Expr>()
        fun flatten(list: List<Expr>) {
            for (term in list) if (term is Expr.Add) flatten(term.terms) else terms += term
        }
        flatten(raw)

        var constant = Rat.ZERO
        val groups = LinkedHashMap<String, Pair<Expr, Rat>>()
        for (term in terms) {
            val (coef, rest) = splitCoeff(term)
            if (rest == null) {
                constant += coef
                continue
            }
            val key = render(rest)
            val prev = groups[key]
            groups[key] = if (prev == null) rest to coef else prev.first to (prev.second + coef)
        }

        val built = ArrayList<Expr>()
        for ((_, entry) in groups) {
            val (rest, coef) = entry
            if (coef.isZero) continue
            built += if (coef == Rat.ONE) rest else Expr.Mul(listOf(Expr.Num(coef), rest))
        }
        if (!constant.isZero) built += Expr.Num(constant)
        if (built.isEmpty()) return Expr.Num(Rat.ZERO)
        if (built.size == 1) return built[0]
        built.sortWith(TERM_ORDER)
        return Expr.Add(built)
    }

    private fun simplifyMul(raw: List<Expr>): Expr {
        val factors = ArrayList<Expr>()
        fun flatten(list: List<Expr>) {
            for (factor in list) if (factor is Expr.Mul) flatten(factor.factors) else factors += factor
        }
        flatten(raw)
        if (factors.isEmpty()) return Expr.Num(Rat.ONE)

        var coef = Rat.ONE
        val rest = ArrayList<Expr>()
        for (factor in factors) {
            if (factor is Expr.Num) coef *= factor.v else rest += factor
        }
        if (coef.isZero) return Expr.Num(Rat.ZERO)
        if (rest.isEmpty()) return Expr.Num(coef)

        // 展开:存在多项式因子时按分配律展开(项数有上限,超限保留因式形式)
        if (rest.any { it is Expr.Add }) {
            var productCount = 1L
            for (factor in rest) {
                productCount *= if (factor is Expr.Add) factor.terms.size else 1
                if (productCount > MAX_EXPAND_TERMS) break
            }
            if (productCount <= MAX_EXPAND_TERMS) {
                var products: List<List<Expr>> = listOf(emptyList())
                for (factor in rest) {
                    val group = if (factor is Expr.Add) factor.terms else listOf(factor)
                    val next = ArrayList<List<Expr>>(products.size * group.size)
                    for (product in products) for (item in group) next += product + item
                    products = next
                }
                return simplifyAdd(products.map { simplifyMul(it + Expr.Num(coef)) })
            }
        }

        // 合并同底数幂
        val groups = LinkedHashMap<String, MutableList<Int>>()
        for ((index, factor) in rest.withIndex()) {
            val base = if (factor is Expr.Pow) factor.base else factor
            groups.getOrPut(render(base)) { ArrayList() } += index
        }
        val merged = ArrayList<Expr>()
        for ((_, indexes) in groups) {
            val exponents = indexes.map { index ->
                (rest[index] as? Expr.Pow)?.exp ?: Expr.Num(Rat.ONE)
            }
            if (exponents.all { it is Expr.Num }) {
                var sum = Rat.ZERO
                for (exponent in exponents) sum += (exponent as Expr.Num).v
                if (sum.isZero) continue
                val base = (rest[indexes[0]] as? Expr.Pow)?.base ?: rest[indexes[0]]
                merged += if (sum == Rat.ONE) base else Expr.Pow(base, Expr.Num(sum))
            } else {
                for (index in indexes) merged += rest[index]
            }
        }
        if (merged.isEmpty()) return Expr.Num(coef)
        merged.sortWith(FACTOR_ORDER)
        if (coef != Rat.ONE) merged.add(0, Expr.Num(coef))
        return if (merged.size == 1) merged[0] else Expr.Mul(merged)
    }

    private fun simplifyPow(base: Expr, exp: Expr): Expr {
        if (exp is Expr.Num) {
            if (exp.v.isZero) return Expr.Num(Rat.ONE)
            if (exp.v == Rat.ONE) return base
            if (base is Expr.Num) {
                if (base.v.isZero && exp.v.signum < 0) throw SymbolicException("除数为零")
                val exact = ratPow(base.v, exp.v)
                return if (exact != null) Expr.Num(exact) else Expr.Pow(base, exp)
            }
            if (base is Expr.Pow && base.exp is Expr.Num) {
                val merged = (base.exp as Expr.Num).v * exp.v
                return simplifyPow(base.base, Expr.Num(merged))
            }
            if (base is Expr.Mul && exp.v.isInteger && exp.v.absValue() <= Rat.of(8)) {
                val k = exp.v.n.toInt()
                val powered = base.factors.map { simplifyPow(it, Expr.Num(Rat.of(k.toLong()))) }
                return simplifyMul(powered)
            }
            if (base is Expr.Add && exp.v.isInteger && exp.v.signum > 0) {
                val k = if (exp.v.n.bitLength() < 31) exp.v.n.toInt() else 0
                if (k in 2..8) {
                    var count = 1L
                    repeat(k) {
                        count *= base.terms.size
                        if (count > MAX_EXPAND_TERMS) return Expr.Pow(base, exp)
                    }
                    var acc: Expr = Expr.Num(Rat.ONE)
                    repeat(k) { acc = simplifyMul(listOf(acc, base)) }
                    return acc
                }
            }
            return Expr.Pow(base, exp)
        }
        if (base is Expr.Num && base.v == Rat.ONE) return Expr.Num(Rat.ONE)
        return Expr.Pow(base, exp)
    }

    private fun simplifyFun(name: String, args: List<Expr>): Expr {
        foldFun(name, args)?.let { return it }
        return Expr.Fun(name, args)
    }

    /** 已知常数角/特殊值折叠;无法精确求值时返回 null(保留符号形式) */
    private fun foldFun(name: String, args: List<Expr>): Expr? {
        if (args.size != 1) return null
        val arg = args[0]
        if (name == "ln") {
            if (arg is Expr.Num && arg.v == Rat.ONE) return Expr.Num(Rat.ZERO)
            if (arg is Expr.Const && arg.name == "e") return Expr.Num(Rat.ONE)
            return null
        }
        if (name == "log") {
            if (arg is Expr.Num && arg.v == Rat.ONE) return Expr.Num(Rat.ZERO)
            if (arg is Expr.Num && arg.v == Rat.of(10)) return Expr.Num(Rat.ONE)
            return null
        }
        if (name == "exp") {
            if (arg is Expr.Num && arg.v.isZero) return Expr.Num(Rat.ONE)
            if (arg is Expr.Fun && arg.name == "ln" && arg.args.size == 1) return arg.args[0]
            return null
        }
        if (name == "abs") {
            if (arg is Expr.Num) return Expr.Num(arg.v.absValue())
            return null
        }
        if (name == "sign") {
            if (arg is Expr.Num) return Expr.Num(Rat.of(arg.v.signum.toLong()))
            return null
        }
        if (name in setOf("sin", "cos", "tan")) {
            val multiple = piMultiple(arg)
            if (multiple != null) {
                val halves = multiple * Rat.of(2)
                if (halves.isInteger && halves.n.bitLength() < 31) {
                    val n = halves.n.toInt()
                    when (name) {
                        "sin" -> return if (n % 2 == 0) {
                            Expr.Num(Rat.ZERO)
                        } else {
                            val quarter = Math.floorDiv(n - 1, 2)
                            Expr.Num(Rat.of(if (Math.floorMod(quarter, 2) == 0) 1L else -1L))
                        }
                        "cos" -> return if (n % 2 != 0) {
                            Expr.Num(Rat.ZERO)
                        } else {
                            val half = n / 2
                            Expr.Num(Rat.of(if (Math.floorMod(half, 2) == 0) 1L else -1L))
                        }
                        else -> return if (n % 2 == 0) Expr.Num(Rat.ZERO) else null
                    }
                }
            }
            if (arg is Expr.Num && arg.v.isZero) {
                return when (name) {
                    "sin", "tan" -> Expr.Num(Rat.ZERO)
                    else -> Expr.Num(Rat.ONE)
                }
            }
            return null
        }
        if (arg is Expr.Num && arg.v.isZero) {
            return when (name) {
                "asin", "atan", "sinh", "tanh" -> Expr.Num(Rat.ZERO)
                "cosh" -> Expr.Num(Rat.ONE)
                "acos" -> null
                else -> null
            }
        }
        if (name == "acos" && arg is Expr.Num && arg.v == Rat.ONE) return Expr.Num(Rat.ZERO)
        return null
    }

    /** 参数是不是 pi 的有理数倍;是则返回倍数 */
    private fun piMultiple(e: Expr): Rat? {
        if (e is Expr.Num && e.v.isZero) return Rat.ZERO
        if (e is Expr.Const && e.name == "pi") return Rat.ONE
        if (e is Expr.Mul) {
            var coef = Rat.ONE
            var hasPi = false
            for (factor in e.factors) {
                when {
                    factor is Expr.Num -> coef *= factor.v
                    factor is Expr.Const && factor.name == "pi" -> {
                        if (hasPi) return null
                        hasPi = true
                    }
                    else -> return null
                }
            }
            if (hasPi) return coef
        }
        return null
    }

    // ==================== 结构工具 ====================

    private fun splitCoeff(e: Expr): Pair<Rat, Expr?> = when (e) {
        is Expr.Num -> e.v to null
        is Expr.Mul -> {
            val numeric = e.factors.filterIsInstance<Expr.Num>()
            if (numeric.isEmpty()) {
                Rat.ONE to e
            } else {
                var coef = Rat.ONE
                for (factor in numeric) coef *= factor.v
                val rest = e.factors.filterNot { it is Expr.Num }
                when {
                    rest.isEmpty() -> coef to null
                    rest.size == 1 -> coef to rest[0]
                    else -> coef to Expr.Mul(rest)
                }
            }
        }
        else -> Rat.ONE to e
    }

    private fun containsVar(e: Expr, v: String): Boolean = when (e) {
        is Expr.Var -> e.name == v
        is Expr.Add -> e.terms.any { containsVar(it, v) }
        is Expr.Mul -> e.factors.any { containsVar(it, v) }
        is Expr.Pow -> containsVar(e.base, v) || containsVar(e.exp, v)
        is Expr.Fun -> e.args.any { containsVar(it, v) }
        else -> false
    }

    private fun freeVars(e: Expr): Set<String> {
        val out = LinkedHashSet<String>()
        fun walk(x: Expr) {
            when (x) {
                is Expr.Var -> out += x.name
                is Expr.Add -> x.terms.forEach(::walk)
                is Expr.Mul -> x.factors.forEach(::walk)
                is Expr.Pow -> {
                    walk(x.base)
                    walk(x.exp)
                }
                is Expr.Fun -> x.args.forEach(::walk)
                else -> Unit
            }
        }
        walk(e)
        return out
    }

    private fun substitute(e: Expr, name: String, value: Expr): Expr = when (e) {
        is Expr.Var -> if (e.name == name) value else e
        is Expr.Add -> Expr.Add(e.terms.map { substitute(it, name, value) })
        is Expr.Mul -> Expr.Mul(e.factors.map { substitute(it, name, value) })
        is Expr.Pow -> Expr.Pow(substitute(e.base, name, value), substitute(e.exp, name, value))
        is Expr.Fun -> Expr.Fun(e.name, e.args.map { substitute(it, name, value) })
        else -> e
    }

    private fun nodeCount(e: Expr): Int = when (e) {
        is Expr.Add -> 1 + e.terms.sumOf { nodeCount(it) }
        is Expr.Mul -> 1 + e.factors.sumOf { nodeCount(it) }
        is Expr.Pow -> 1 + nodeCount(e.base) + nodeCount(e.exp)
        is Expr.Fun -> 1 + e.args.sumOf { nodeCount(it) }
        else -> 1
    }

    private fun ensureSize(e: Expr) {
        if (nodeCount(e) > MAX_NODES) throw SymbolicException("表达式过于复杂(节点数上限 $MAX_NODES)")
    }

    private fun Expr.isZeroNum(): Boolean = this is Expr.Num && v.isZero

    private fun termDegree(e: Expr): Int = when (e) {
        is Expr.Var -> 1
        is Expr.Pow -> {
            val exp = e.exp
            if (exp is Expr.Num && exp.v.isInteger && exp.v.signum > 0 && exp.v.n.bitLength() < 16) {
                (termDegree(e.base).coerceAtMost(1000)) * exp.v.n.toInt()
            } else {
                termDegree(e.base)
            }
        }
        is Expr.Mul -> e.factors.sumOf { termDegree(it) }.coerceAtMost(1000)
        else -> 0
    }

    private val TERM_ORDER = Comparator<Expr> { a, b ->
        val da = termDegree(a)
        val db = termDegree(b)
        when {
            da != db -> db.compareTo(da)
            a is Expr.Num && b !is Expr.Num -> 1
            a !is Expr.Num && b is Expr.Num -> -1
            else -> render(a).compareTo(render(b))
        }
    }

    private val FACTOR_ORDER = Comparator<Expr> { a, b ->
        val ra = factorRank(a)
        val rb = factorRank(b)
        if (ra != rb) ra.compareTo(rb) else render(a).compareTo(render(b))
    }

    private fun factorRank(e: Expr): Int = when (e) {
        is Expr.Var -> 0
        is Expr.Const -> 1
        is Expr.Pow -> if (e.base is Expr.Var) 0 else if (e.base is Expr.Const) 1 else 2
        is Expr.Fun -> 3
        else -> 2
    }

    // ==================== 渲染 ====================

    private fun render(e: Expr): String = when (e) {
        is Expr.Num -> e.v.toString()
        is Expr.Var -> e.name
        is Expr.Const -> e.name
        is Expr.Add -> renderAdd(e.terms)
        is Expr.Mul -> renderMul(e.factors)
        is Expr.Pow -> renderPow(e)
        is Expr.Fun -> "${e.name}(${e.args.joinToString(",") { render(it) }})"
    }

    private fun renderAdd(terms: List<Expr>): String {
        val sb = StringBuilder()
        terms.forEachIndexed { index, term ->
            val text = render(term)
            when {
                index == 0 -> sb.append(text)
                text.startsWith("-") -> sb.append(text)
                else -> sb.append('+').append(text)
            }
        }
        return sb.toString()
    }

    private fun renderMul(factors: List<Expr>): String {
        var coef = Rat.ONE
        val rest = ArrayList<Expr>()
        for (factor in factors) {
            if (factor is Expr.Num) coef *= factor.v else rest += factor
        }
        val numerator = ArrayList<String>()
        val denominator = ArrayList<String>()
        for (factor in rest) {
            if (factor is Expr.Pow && factor.exp is Expr.Num && factor.exp.v.signum < 0) {
                val positive = factor.exp.v.negate()
                denominator += if (positive == Rat.ONE) {
                    renderFactor(factor.base)
                } else {
                    renderFactor(Expr.Pow(factor.base, Expr.Num(positive)))
                }
            } else {
                numerator += renderFactor(factor)
            }
        }
        val sign = if (coef.signum < 0) "-" else ""
        val numerParts = ArrayList<String>()
        if (coef.n.abs() != BigInteger.ONE || numerator.isEmpty()) {
            numerParts += coef.n.abs().toString()
        }
        numerParts += numerator
        val denomParts = ArrayList<String>()
        if (coef.d != BigInteger.ONE) denomParts += coef.d.toString()
        denomParts += denominator
        val numText = if (numerParts.isEmpty()) "1" else numerParts.joinToString("*")
        val denText = when {
            denomParts.isEmpty() -> null
            denomParts.size == 1 -> denomParts[0]
            else -> "(${denomParts.joinToString("*")})"
        }
        return sign + if (denText == null) numText else "$numText/$denText"
    }

    private fun renderFactor(e: Expr): String = when (e) {
        is Expr.Add -> "(${render(e)})"
        is Expr.Mul -> "(${render(e)})"
        is Expr.Num -> if (e.v.isInteger) e.v.toString() else "(${e.v})"
        is Expr.Pow -> renderPow(e)
        else -> render(e)
    }

    private fun renderPow(e: Expr.Pow): String {
        val exp = e.exp
        if (exp is Expr.Num) {
            val value = exp.v
            if (value.signum < 0) {
                val positive = value.negate()
                val inner = if (positive == Rat.ONE) {
                    renderFactor(e.base)
                } else {
                    renderPow(Expr.Pow(e.base, Expr.Num(positive)))
                }
                return "1/$inner"
            }
            if (value == Rat.of(1, 2)) return "sqrt(${render(e.base)})"
            if (value == Rat.of(1, 3)) return "cbrt(${render(e.base)})"
        }
        val baseText = when (e.base) {
            is Expr.Add, is Expr.Mul, is Expr.Num, is Expr.Pow -> "(${render(e.base)})"
            else -> render(e.base)
        }
        val expText = when (exp) {
            is Expr.Num -> if (exp.v.isInteger) exp.v.toString() else "(${exp.v})"
            is Expr.Add, is Expr.Mul -> "(${render(exp)})"
            is Expr.Pow -> "(${render(exp)})"
            else -> render(exp)
        }
        return "$baseText^$expText"
    }

    // ==================== 解析 ====================

    private fun parseSingle(src: String): Expr {
        val equations = parseEquations(src)
        if (equations.size != 1) {
            throw SymbolicException("该操作只接受单个表达式;多条语句请改用 operation=solve")
        }
        val (lhs, rhs) = equations[0]
        if (rhs != null) throw SymbolicException("该操作不接受等式;解方程请用 operation=solve")
        ensureSize(lhs)
        return simplifyExpr(lhs)
    }

    private fun parseEquations(src: String): List<Pair<Expr, Expr?>> {
        val parser = Parser(src)
        val out = ArrayList<Pair<Expr, Expr?>>()
        while (true) {
            parser.skipSeparators()
            if (parser.atEnd()) break
            val lhs = parser.parseExpression()
            val isEquation = parser.consumeEquals()
            val rhs = if (isEquation) parser.parseExpression() else null
            out += lhs to rhs
            if (!parser.atEnd() && !parser.atSeparator()) {
                throw SymbolicException("表达式之间只能用分号、换行或等号连接")
            }
        }
        if (out.isEmpty()) throw SymbolicException("表达式为空")
        return out
    }

    private fun constantExpr(src: String, label: String): Expr {
        val expr = parseSingle(src)
        val vars = freeVars(expr)
        if (vars.isNotEmpty()) {
            throw SymbolicException("积分${label}必须是常数,不能含变量:${vars.joinToString(", ")}")
        }
        return expr
    }

    private fun validVariable(raw: String): String {
        val name = raw.trim()
        if (name.isEmpty()) throw SymbolicException("变量名不能为空")
        if (!Regex("[A-Za-z_][A-Za-z0-9_]*").matches(name)) {
            throw SymbolicException("变量名不合法:$raw")
        }
        if (name.lowercase() in RESERVED_NAMES) {
            throw SymbolicException("不能用常量或函数名作变量:$raw")
        }
        return name
    }

    private val RESERVED_NAMES: Set<String> = setOf(
        "pi", "π", "e", "tau", "phi", "i",
        "sin", "cos", "tan", "asin", "acos", "atan", "sinh", "cosh", "tanh",
        "exp", "ln", "log", "log2", "sqrt", "cbrt", "pow", "abs", "sign",
    )

    private enum class TokKind { NUM, IDENT, OP, SEP, END }

    private data class Tok(val kind: TokKind, val text: String, val num: Rat? = null)

    private fun tokenize(src: String): List<Tok> {
        if (src.length > MAX_INPUT_CHARS) {
            throw SymbolicException("表达式过长(上限 $MAX_INPUT_CHARS 个字符)")
        }
        val out = ArrayList<Tok>()
        var i = 0
        var paren = 0
        while (i < src.length) {
            val c = src[i]
            when {
                c.isWhitespace() -> {
                    if ((c == '\n' || c == '\r') && paren == 0) out += Tok(TokKind.SEP, ";")
                    i++
                }
                c == ';' -> {
                    out += Tok(TokKind.SEP, ";")
                    i++
                }
                c.isDigit() || (c == '.' && i + 1 < src.length && src[i + 1].isDigit()) -> {
                    val start = i
                    while (i < src.length && src[i].isDigit()) i++
                    if (i < src.length && src[i] == '.') {
                        i++
                        while (i < src.length && src[i].isDigit()) i++
                    }
                    // 科学计数法只在 e/E 后确有数字时吞掉,否则 e 仍按常量解析(2e = 2*e)
                    if (i < src.length && (src[i] == 'e' || src[i] == 'E')) {
                        var j = i + 1
                        if (j < src.length && (src[j] == '+' || src[j] == '-')) j++
                        if (j < src.length && src[j].isDigit()) {
                            var digits = 0
                            while (j < src.length && src[j].isDigit()) {
                                j++
                                digits++
                            }
                            if (digits > 20) throw SymbolicException("数字指数过大")
                            i = j
                        }
                    }
                    val text = src.substring(start, i)
                    out += Tok(TokKind.NUM, text, parseDecimal(text))
                }
                c in 'a'..'z' || c in 'A'..'Z' || c == '_' || c == 'π' -> {
                    val start = i
                    while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_' || src[i] == 'π')) i++
                    out += Tok(TokKind.IDENT, src.substring(start, i))
                }
                c in "+-*/^(),=" -> {
                    if (c == '(') paren++ else if (c == ')') paren = (paren - 1).coerceAtLeast(0)
                    out += Tok(TokKind.OP, c.toString())
                    i++
                }
                else -> throw SymbolicException("无法识别的字符:$c")
            }
        }
        out += Tok(TokKind.END, "")
        return out
    }

    private fun parseDecimal(text: String): Rat {
        val decimal = try {
            BigDecimal(text)
        } catch (e: NumberFormatException) {
            throw SymbolicException("无法解析数字:$text")
        }
        val scale = decimal.scale()
        if (scale > 1000) throw SymbolicException("数字精度过高:$text")
        val unscaled = decimal.unscaledValue()
        if (unscaled.bitLength() > 512) throw SymbolicException("数字过大:$text")
        return if (scale <= 0) {
            Rat.of(unscaled.multiply(BigInteger.TEN.pow(-scale)), BigInteger.ONE)
        } else {
            Rat.of(unscaled, BigInteger.TEN.pow(scale))
        }
    }

    private class Parser(src: String) {
        private val tokens: List<Tok> = tokenize(src)
        private var pos = 0
        private var depth = 0

        fun atEnd(): Boolean = peek().kind == TokKind.END
        fun atSeparator(): Boolean = peek().kind == TokKind.SEP

        fun skipSeparators() {
            while (peek().kind == TokKind.SEP) pos++
        }

        fun consumeEquals(): Boolean {
            val token = peek()
            if (token.kind == TokKind.OP && token.text == "=") {
                pos++
                return true
            }
            return false
        }

        fun parseExpression(): Expr {
            depth++
            if (depth > MAX_DEPTH) throw SymbolicException("表达式嵌套过深(上限 $MAX_DEPTH 层)")
            val expr = parseAddSub()
            depth--
            return expr
        }

        private fun parseAddSub(): Expr {
            val terms = ArrayList<Expr>()
            terms += parseMulDiv()
            while (true) {
                val token = peek()
                if (token.kind == TokKind.OP && (token.text == "+" || token.text == "-")) {
                    pos++
                    val rhs = parseMulDiv()
                    terms += if (token.text == "-") {
                        Expr.Mul(listOf(Expr.Num(Rat.MINUS_ONE), rhs))
                    } else {
                        rhs
                    }
                } else {
                    break
                }
            }
            return if (terms.size == 1) terms[0] else Expr.Add(terms)
        }

        private fun parseMulDiv(): Expr {
            val factors = ArrayList<Expr>()
            factors += parseUnary()
            while (true) {
                val token = peek()
                when {
                    token.kind == TokKind.OP && (token.text == "*" || token.text == "/") -> {
                        pos++
                        val rhs = parseUnary()
                        factors += if (token.text == "/") {
                            Expr.Pow(rhs, Expr.Num(Rat.MINUS_ONE))
                        } else {
                            rhs
                        }
                    }
                    startsFactor(token) -> factors += parseUnary()
                    else -> break
                }
            }
            return if (factors.size == 1) factors[0] else Expr.Mul(factors)
        }

        private fun startsFactor(token: Tok): Boolean = when (token.kind) {
            TokKind.NUM, TokKind.IDENT -> true
            TokKind.OP -> token.text == "("
            else -> false
        }

        private fun parseUnary(): Expr {
            val token = peek()
            if (token.kind == TokKind.OP && (token.text == "+" || token.text == "-")) {
                pos++
                val expr = parseUnary()
                return if (token.text == "-") Expr.Mul(listOf(Expr.Num(Rat.MINUS_ONE), expr)) else expr
            }
            return parsePower()
        }

        private fun parsePower(): Expr {
            val base = parsePrimary()
            val token = peek()
            if (token.kind == TokKind.OP && token.text == "^") {
                pos++
                return Expr.Pow(base, parseUnary())
            }
            return base
        }

        private fun parsePrimary(): Expr {
            val token = peek()
            return when {
                token.kind == TokKind.NUM -> {
                    pos++
                    Expr.Num(token.num ?: throw SymbolicException("无法解析数字:${token.text}"))
                }
                token.kind == TokKind.IDENT -> parseIdentifier(token)
                token.kind == TokKind.OP && token.text == "(" -> {
                    pos++
                    val expr = parseExpression()
                    expect(")")
                    expr
                }
                token.kind == TokKind.END -> throw SymbolicException("表达式不完整")
                else -> throw SymbolicException("非法记号:${token.text}")
            }
        }

        private fun parseIdentifier(token: Tok): Expr {
            val name = token.text
            pos++
            val key = name.lowercase()
            val followedByParen = peek().kind == TokKind.OP && peek().text == "("
            if (followedByParen && key !in FUNCTION_NAMES) {
                // 函数调用还是隐式乘法在这里无法区分;拒绝未知函数比静默算出错误结果更安全
                throw SymbolicException("未知函数:$name;若要表示乘法请写成 $name*(...)")
            }
            if (followedByParen) {
                pos++
                val args = ArrayList<Expr>()
                if (!(peek().kind == TokKind.OP && peek().text == ")")) {
                    while (true) {
                        args += parseExpression()
                        if (peek().kind == TokKind.OP && peek().text == ",") {
                            pos++
                        } else {
                            break
                        }
                    }
                }
                expect(")")
                return buildCall(key, name, args)
            }
            return when (key) {
                "pi", "π" -> Expr.Const("pi")
                "e" -> Expr.Const("e")
                "tau" -> Expr.Const("tau")
                "phi" -> Expr.Const("phi")
                "i" -> throw SymbolicException("暂不支持复数符号 i 参与符号运算")
                else -> {
                    if (key in FUNCTION_NAMES) throw SymbolicException("函数 $name 需要参数括号")
                    Expr.Var(name)
                }
            }
        }

        private fun buildCall(key: String, raw: String, args: List<Expr>): Expr {
            fun unary(): Expr {
                if (args.size != 1) throw SymbolicException("$raw 需要 1 个参数")
                return args[0]
            }
            return when (key) {
                "sin", "cos", "tan", "asin", "acos", "atan", "sinh", "cosh", "tanh",
                "exp", "ln", "abs", "sign" -> Expr.Fun(key, listOf(unary()))
                "log" -> when (args.size) {
                    1 -> Expr.Fun("log", listOf(args[0]))
                    2 -> Expr.Mul(
                        listOf(
                            Expr.Fun("ln", listOf(args[0])),
                            Expr.Pow(Expr.Fun("ln", listOf(args[1])), Expr.Num(Rat.MINUS_ONE)),
                        ),
                    )
                    else -> throw SymbolicException("log 需要 1 或 2 个参数")
                }
                "sqrt" -> Expr.Pow(unary(), Expr.Num(Rat.of(1, 2)))
                "cbrt" -> Expr.Pow(unary(), Expr.Num(Rat.of(1, 3)))
                "pow" -> {
                    if (args.size != 2) throw SymbolicException("pow 需要 2 个参数")
                    Expr.Pow(args[0], args[1])
                }
                "log2" -> Expr.Mul(
                    listOf(
                        Expr.Fun("ln", listOf(unary())),
                        Expr.Pow(Expr.Fun("ln", listOf(Expr.Num(Rat.of(2)))), Expr.Num(Rat.MINUS_ONE)),
                    ),
                )
                else -> throw SymbolicException("未知函数:$raw")
            }
        }

        private fun expect(op: String) {
            val token = peek()
            if (token.kind == TokKind.OP && token.text == op) {
                pos++
            } else {
                throw SymbolicException("缺少「$op」")
            }
        }

        private fun peek(offset: Int = 0): Tok = tokens[(pos + offset).coerceAtMost(tokens.lastIndex)]
    }

    private val FUNCTION_NAMES: Set<String> = setOf(
        "sin", "cos", "tan", "asin", "acos", "atan", "sinh", "cosh", "tanh",
        "exp", "ln", "log", "log2", "sqrt", "cbrt", "pow", "abs", "sign",
    )

    // ==================== 数值/精确求值 ====================

    private fun evalDouble(e: Expr): Double = when (e) {
        is Expr.Num -> e.v.toDouble()
        is Expr.Const -> when (e.name) {
            "pi" -> Math.PI
            "e" -> Math.E
            "tau" -> 2 * Math.PI
            "phi" -> (1 + Math.sqrt(5.0)) / 2
            else -> throw SymbolicException("未知常量:${e.name}")
        }
        is Expr.Var -> throw SymbolicException("表达式仍含变量:${e.name}")
        is Expr.Add -> {
            var sum = 0.0
            for (term in e.terms) sum += evalDouble(term)
            sum
        }
        is Expr.Mul -> {
            var product = 1.0
            for (factor in e.factors) product *= evalDouble(factor)
            product
        }
        is Expr.Pow -> Math.pow(evalDouble(e.base), evalDouble(e.exp))
        is Expr.Fun -> {
            if (e.args.size != 1) throw SymbolicException("无法数值求值:${e.name}")
            val x = evalDouble(e.args[0])
            when (e.name) {
                "sin" -> Math.sin(x)
                "cos" -> Math.cos(x)
                "tan" -> Math.tan(x)
                "asin" -> Math.asin(x)
                "acos" -> Math.acos(x)
                "atan" -> Math.atan(x)
                "sinh" -> Math.sinh(x)
                "cosh" -> Math.cosh(x)
                "tanh" -> Math.tanh(x)
                "exp" -> Math.exp(x)
                "ln" -> Math.log(x)
                "log" -> Math.log10(x)
                "abs" -> Math.abs(x)
                "sign" -> Math.signum(x)
                else -> throw SymbolicException("无法数值求值:${e.name}")
            }
        }
    }

    private fun exactSqrt(r: Rat): Rat? {
        if (r.signum < 0) return null
        val num = exactRoot(r.n, 2) ?: return null
        val den = exactRoot(r.d, 2) ?: return null
        return Rat.of(num, den)
    }

    private fun exactRoot(value: BigInteger, degree: Int): BigInteger? {
        if (degree <= 0) return null
        if (value.signum() < 0) return null
        if (degree == 1) return value
        if (value.signum() == 0) return BigInteger.ZERO
        if (value == BigInteger.ONE) return BigInteger.ONE
        if (value.bitLength() > 4096) return null
        val q = BigInteger.valueOf(degree.toLong())
        var root = BigInteger.ONE.shiftLeft((value.bitLength() + degree - 1) / degree)
        while (true) {
            val next = root.multiply(BigInteger.valueOf((degree - 1).toLong()))
                .add(value.divide(root.pow(degree - 1)))
                .divide(q)
            if (next >= root) break
            root = next
        }
        val candidates = listOf(root, root + BigInteger.ONE)
        for (candidate in candidates) {
            if (candidate.pow(degree) == value) return candidate
        }
        return null
    }

    private fun ratPow(base: Rat, exp: Rat): Rat? {
        if (exp.n.bitLength() > 5 || exp.d.bitLength() > 5) return null
        val p = exp.n.toInt()
        val q = exp.d.toInt()
        if (base.isZero) {
            if (p < 0) return null
            return if (p == 0) Rat.ONE else Rat.ZERO
        }
        if (base.n.bitLength() > 512 || base.d.bitLength() > 512) return null
        val negative = base.signum < 0
        if (negative && q % 2 == 0) return null
        val absolute = if (negative) base.negate() else base
        val num = exactRoot(absolute.n, q) ?: return null
        val den = exactRoot(absolute.d, q) ?: return null
        var root = Rat.of(num, den)
        if (negative) root = root.negate()
        val absP = if (p < 0) -p else p
        if (root.n.bitLength() * absP > 8192 || root.d.bitLength() * absP > 8192) return null
        val powered = root.pow(absP)
        return if (p < 0) Rat.ONE / powered else powered
    }
}

// =============================================================================
// 内部类型:有理数与表达式 AST(仅本文件可见)
// =============================================================================

/** 任意精度有理数,始终约分且分母为正 */
private class Rat private constructor(val n: BigInteger, val d: BigInteger) : Comparable<Rat> {

    companion object {
        val ZERO = Rat(BigInteger.ZERO, BigInteger.ONE)
        val ONE = Rat(BigInteger.ONE, BigInteger.ONE)
        val MINUS_ONE = Rat(BigInteger.ONE.negate(), BigInteger.ONE)

        fun of(value: Long): Rat = Rat(BigInteger.valueOf(value), BigInteger.ONE)

        fun of(num: Long, den: Long): Rat = of(BigInteger.valueOf(num), BigInteger.valueOf(den))

        fun of(num: BigInteger, den: BigInteger): Rat {
            if (den.signum() == 0) throw SymbolicMathEvaluator.SymbolicException("除数为零")
            var nn = num
            var dd = den
            if (dd.signum() < 0) {
                nn = nn.negate()
                dd = dd.negate()
            }
            val gcd = nn.gcd(dd)
            val divisor = if (gcd.signum() == 0) BigInteger.ONE else gcd
            return Rat(nn.divide(divisor), dd.divide(divisor))
        }
    }

    val signum: Int get() = n.signum()
    val isZero: Boolean get() = n.signum() == 0
    val isInteger: Boolean get() = d == BigInteger.ONE

    operator fun plus(other: Rat): Rat = of(n * other.d + other.n * d, d * other.d)
    operator fun minus(other: Rat): Rat = of(n * other.d - other.n * d, d * other.d)
    operator fun times(other: Rat): Rat = of(n * other.n, d * other.d)
    operator fun div(other: Rat): Rat = of(n * other.d, d * other.n)
    operator fun unaryMinus(): Rat = Rat(n.negate(), d)
    override fun compareTo(other: Rat): Int = (n * other.d).compareTo(other.n * d)

    fun absValue(): Rat = if (signum < 0) -this else this

    fun negate(): Rat = Rat(n.negate(), d)

    fun pow(k: Int): Rat {
        if (k == 0) return ONE
        if (k < 0) return ONE / pow(-k)
        return of(n.pow(k), d.pow(k))
    }

    fun toDouble(): Double = n.toDouble() / d.toDouble()

    override fun toString(): String = if (d == BigInteger.ONE) n.toString() else "$n/$d"

    override fun equals(other: Any?): Boolean = other is Rat && n == other.n && d == other.d

    override fun hashCode(): Int = 31 * n.hashCode() + d.hashCode()
}

/** 符号表达式:数字 / 变量 / 常量 / 和 / 积 / 幂 / 函数 */
private sealed class Expr {
    data class Num(val v: Rat) : Expr()
    data class Var(val name: String) : Expr()
    data class Const(val name: String) : Expr()
    data class Add(val terms: List<Expr>) : Expr()
    data class Mul(val factors: List<Expr>) : Expr()
    data class Pow(val base: Expr, val exp: Expr) : Expr()
    data class Fun(val name: String, val args: List<Expr>) : Expr()
}
