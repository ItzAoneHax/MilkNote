package com.milknote.agent

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// =============================================================================
// 符号数学引擎:simplify / solve / derivative / integrate / definite_integral
// =============================================================================

class SymbolicMathEvaluatorTest {

    private val engine = SymbolicMathEvaluator

    @Test
    fun `化简-展开乘积并合并同类项`() {
        assertEquals("2*x^2+2*x", engine.simplify("(x+x)*(x+1)"))
        assertEquals("x^2+2*x+1", engine.simplify("(x+1)^2"))
        assertEquals("x^2-1", engine.simplify("(x+1)*(x-1)"))
        assertEquals("3*x^2", engine.simplify("x^2+2*x^2"))
        assertEquals("2*x^2", engine.simplify("2*x*x"))
    }

    @Test
    fun `化简-零与一恒等式`() {
        assertEquals("0", engine.simplify("x-x"))
        assertEquals("x", engine.simplify("x*1+0"))
        assertEquals("x", engine.simplify("1*x^2/x"))
        assertEquals("1", engine.simplify("x^0"))
        assertEquals("x", engine.simplify("x^1"))
    }

    @Test
    fun `化简-隐式乘法与常量折叠`() {
        assertEquals("2*x^2+2*x", engine.simplify("2*x*(x+1)"))
        assertEquals("2*x+2", engine.simplify("2(x+1)"))
        assertEquals("8", engine.simplify("2^3"))
        assertEquals("1/4", engine.simplify("2^-2"))
        assertEquals("sqrt(8)", engine.simplify("sqrt(8)"))
        assertEquals("3", engine.simplify("sqrt(9)"))
    }

    @Test
    fun `求导-多项式与初等函数`() {
        assertEquals("3*x^2+cos(x)", engine.derivative("x^3+sin(x)", "x"))
        assertEquals("2*x*cos(x^2)", engine.derivative("sin(x^2)", "x"))
        assertEquals("2", engine.derivative("2*x+3", "x"))
        assertEquals("0", engine.derivative("7", "x"))
        assertEquals("exp(x)", engine.derivative("exp(x)", "x"))
        assertEquals("1/x", engine.derivative("ln(x)", "x"))
        assertEquals("1/(2*sqrt(x))", engine.derivative("sqrt(x)", "x"))
        assertEquals("-sin(x)", engine.derivative("cos(x)", "x"))
    }

    @Test
    fun `求导-商法则与链式法则`() {
        // (x^2)/ (x+1) → (2x(x+1)-x^2)/(x+1)^2
        val d = engine.derivative("x^2/(x+1)", "x")
        assertFalse("不应保留无意义的 1* 或 0+", d.contains("1*") || d.contains("0+"))
        assertTrue(d, d.contains("(x+1)^2") || d.contains("x+1"))
    }

    @Test
    fun `不定积分-多项式与基本函数`() {
        assertEquals("x^3+x^2+x+C", engine.integrate("3*x^2+2*x+1", "x"))
        assertEquals("-cos(2*x)/2+C", engine.integrate("sin(2*x)", "x"))
        assertEquals("sin(x)+C", engine.integrate("cos(x)", "x"))
        assertEquals("exp(x)+C", engine.integrate("exp(x)", "x"))
        assertEquals("ln(abs(x))+C", engine.integrate("1/x", "x"))
        assertEquals("2*x^(3/2)/3+C", engine.integrate("sqrt(x)", "x"))
        assertEquals("-1/x+C", engine.integrate("x^-2", "x"))
    }

    @Test
    fun `不定积分-不支持的被积式明确报错`() {
        val e1 = assertThrowsSymbolic { engine.integrate("sin(x^2)", "x") }
        assertTrue(e1.contains("暂不支持符号积分"))
        val e2 = assertThrowsSymbolic { engine.integrate("x*sin(x)", "x") }
        assertTrue(e2.contains("暂不支持符号积分"))
    }

    @Test
    fun `定积分-精确值与数值附注`() {
        assertEquals("9", engine.definiteIntegral("x^2", "x", "0", "3", 10))
        assertEquals("7/3", engine.definiteIntegral("x^2", "x", "1", "2", 10))
        assertEquals("1", engine.definiteIntegral("cos(x)", "x", "0", "pi/2", 10))
        assertEquals("2", engine.definiteIntegral("sin(x)", "x", "0", "pi", 10))
        val ln2 = engine.definiteIntegral("1/x", "x", "1", "2", 10)
        assertTrue(ln2, ln2.contains("ln(2)"))
        assertTrue(ln2, ln2.contains("≈"))
    }

    @Test
    fun `定积分-区间内奇点与端点奇点明确拒绝`() {
        val inner = assertThrowsSymbolic { engine.definiteIntegral("1/x", "x", "-1", "1", 10) }
        assertTrue(inner, inner.contains("奇点") || inner.contains("无定义"))
        assertTrue(inner, inner.contains("x = 0"))

        val shifted = assertThrowsSymbolic { engine.definiteIntegral("1/(x-1)", "x", "0", "2", 10) }
        assertTrue(shifted, shifted.contains("x = 1"))

        val endpoint = assertThrowsSymbolic { engine.definiteIntegral("1/x", "x", "0", "1", 10) }
        assertTrue(endpoint, endpoint.contains("x = 0"))

        val tangent = assertThrowsSymbolic { engine.definiteIntegral("tan(x)", "x", "0", "pi", 10) }
        assertTrue(tangent, tangent.contains("tan"))

        val evenRoot = assertThrowsSymbolic { engine.definiteIntegral("sqrt(x)", "x", "-1", "1", 10) }
        assertTrue(evenRoot, evenRoot.contains("偶次根号"))

        val logarithm = assertThrowsSymbolic { engine.definiteIntegral("ln(x)", "x", "0", "1", 10) }
        assertTrue(logarithm, logarithm.contains("对数参数"))
    }

    @Test
    fun `定积分-正常区间与非线性被积式不被误伤`() {
        assertEquals("9", engine.definiteIntegral("x^2", "x", "0", "3", 10))
        assertEquals("2", engine.definiteIntegral("sin(x)", "x", "0", "pi", 10))
        val fromOne = engine.definiteIntegral("1/x", "x", "1", "2", 10)
        assertTrue(fromOne, fromOne.contains("ln(2)"))
        val halved = engine.definiteIntegral("1/(2*x)", "x", "1", "2", 10)
        assertTrue(halved, halved.contains("ln(2)/2"))
        // 非线性底数不在结构检查范围内,仍按"暂不支持"报错而不是误报奇点
        val unsupported = assertThrowsSymbolic { engine.definiteIntegral("1/(x^2+1)", "x", "0", "1", 10) }
        assertTrue(unsupported, unsupported.contains("暂不支持符号积分"))
    }

    @Test
    fun `解析优先级-除号右侧取完整因子`() {
        assertEquals("2*x/3", engine.simplify("2*x/3"))
        assertEquals("2/3", engine.derivative("2*x/3", "x"))
        assertEquals("1/2", engine.simplify("x/(2*x)"))
        assertEquals("0", engine.derivative("x/(2*x)", "x"))
        assertEquals("2", engine.simplify("(2*x)/x"))
    }

    @Test
    fun `定积分-上下限必须是常数`() {
        val message = assertThrowsSymbolic { engine.definiteIntegral("x^2", "x", "a", "3", 10) }
        assertTrue(message, message.contains("常数"))
    }

    @Test
    fun `求解-一元一次与一元二次`() {
        assertEquals("x = 2", engine.solve("2*x+3=7", null, null))
        assertEquals("x = 2", engine.solve("2x+3=7", null, null))
        assertEquals("x = 2 或 x = 3", engine.solve("x^2-5*x+6=0", null, null))
        assertEquals("x = 2 或 x = 3", engine.solve("x^2-5x+6=0", null, null))
        assertEquals("x = 1(重根)", engine.solve("x^2-2*x+1=0", null, null))
        assertEquals("x = ±i", engine.solve("x^2+1=0", null, null))
        assertEquals("x = -1 ± 2*i", engine.solve("x^2+2*x+5=0", null, null))
        assertEquals("x = 1/2 或 x = 1", engine.solve("2*x^2-3*x+1=0", null, null))
        // 无相等判别式的无理根仍以精确符号输出
        val irrational = engine.solve("x^2-2=0", null, null)
        assertTrue(irrational, irrational.contains("sqrt(8)"))
        assertTrue(irrational, irrational.contains("或"))
    }

    @Test
    fun `求解-指定变量与无解无数解`() {
        assertEquals("t = 2", engine.solve("2*t+3=7", "t", null))
        assertEquals("方程无解", engine.solve("x+1=x+2", null, null))
        assertTrue(engine.solve("x+1=x+1", null, null).contains("无数个解"))
        assertEquals("x = 0", engine.solve("x", null, null))
    }

    @Test
    fun `求解-线性方程组唯一解无解无穷多解`() {
        assertEquals("x = 3, y = 2", engine.solve("x+y=5; x-y=1", null, listOf("x", "y")))
        assertEquals(
            "a = 2, b = 1, c = 3",
            engine.solve("a+b+c=6; a-b=1; c=3", null, listOf("a", "b", "c")),
        )
        assertEquals("方程组无解", engine.solve("x+y=1; x+y=2", null, listOf("x", "y")))
        assertEquals("方程组有无数个解", engine.solve("x+y=1; 2*x+2*y=2", null, listOf("x", "y")))
    }

    @Test
    fun `求解-方程组缺少variables或含非线性项报错`() {
        val missing = assertThrowsSymbolic { engine.solve("x+y=5; x-y=1", null, null) }
        assertTrue(missing, missing.contains("variables"))

        val nonlinear = assertThrowsSymbolic { engine.solve("x*y=1; x+y=2", null, listOf("x", "y")) }
        assertTrue(nonlinear, nonlinear.contains("线性方程"))
    }

    @Test
    fun `求解-单方程带多个variables报错`() {
        val message = assertThrowsSymbolic { engine.solve("x+y=3", null, listOf("x", "y")) }
        assertTrue(message, message.contains("variable"))
    }

    @Test
    fun `求解-三次方程明确拒绝`() {
        val message = assertThrowsSymbolic { engine.solve("x^3-1=0", null, null) }
        assertTrue(message, message.contains("次数"))
    }

    @Test
    fun `边界-超长与过深输入被拒绝`() {
        val longInput = "1+" + "1+".repeat(1200) + "1"
        assertTrue(longInput.length > 2000)
        val longMessage = assertThrowsSymbolic { engine.simplify(longInput) }
        assertTrue(longMessage, longMessage.contains("过长"))

        val deep = "(".repeat(80) + "x" + ")".repeat(80)
        val deepMessage = assertThrowsSymbolic { engine.simplify(deep) }
        assertTrue(deepMessage, deepMessage.contains("嵌套过深"))
    }

    @Test
    fun `边界-非法变量与未知函数`() {
        assertTrue(
            assertThrowsSymbolic { engine.derivative("x^2", "sin") }.contains("变量"),
        )
        assertTrue(
            assertThrowsSymbolic { engine.simplify("foo(2)") }.contains("未知函数"),
        )
        assertTrue(
            assertThrowsSymbolic { engine.simplify("x=") }.contains("不完整"),
        )
    }

    private fun assertThrowsSymbolic(block: () -> Unit): String {
        try {
            block()
        } catch (e: SymbolicMathEvaluator.SymbolicException) {
            return e.message ?: ""
        }
        throw AssertionError("应当抛出 SymbolicException")
    }
}

// =============================================================================
// calculate 工具层:必须经 ToolRegistry 真实执行
// =============================================================================

class CalculateToolSymbolicTest {

    private val registry = BuiltinTools.createDefaultRegistry()

    @Test
    fun `evaluate 保持原有数值行为与变量回显`() = runBlocking {
        val plain = exec("""{"expression":"1+2*3"}""")
        assertTrue(plain.ok)
        assertEquals("结果: 7", plain.output)

        val withVars = exec("""{"expression":"a=3; b=4; sqrt(a^2+b^2)"}""")
        assertTrue(withVars.ok)
        assertTrue(withVars.output, withVars.output.startsWith("结果: 5"))
        assertTrue(withVars.output, withVars.output.contains("a = 3"))
        assertTrue(withVars.output, withVars.output.contains("b = 4"))

        val bad = exec("""{"expression":"1+"}""")
        assertFalse(bad.ok)
    }

    @Test
    fun `simplify-通过工具执行`() = runBlocking {
        val result = exec("""{"operation":"simplify","expression":"(x+x)*(x+1)"}""")
        assertTrue(result.ok)
        assertEquals("结果: 2*x^2+2*x", result.output)
        assertEquals("2*x^2+2*x", result.summary)
    }

    @Test
    fun `solve-单元方程与方程组`() = runBlocking {
        val quadratic = exec("""{"operation":"solve","expression":"x^2-5*x+6=0"}""")
        assertTrue(quadratic.ok)
        assertEquals("结果: x = 2 或 x = 3", quadratic.output)

        val complex = exec("""{"operation":"solve","expression":"x^2+1=0"}""")
        assertTrue(complex.ok)
        assertEquals("结果: x = ±i", complex.output)

        val system = exec(
            """{"operation":"solve","expression":"x+y=5; x-y=1","variables":"x,y"}""",
        )
        assertTrue(system.ok)
        assertEquals("结果: x = 3, y = 2", system.output)

        val customVar = exec("""{"operation":"solve","expression":"2*t+3=7","variable":"t"}""")
        assertTrue(customVar.ok)
        assertEquals("结果: t = 2", customVar.output)
    }

    @Test
    fun `derivative-通过工具执行`() = runBlocking {
        val result = exec("""{"operation":"derivative","expression":"x^3+sin(x)"}""")
        assertTrue(result.ok)
        assertEquals("结果: 3*x^2+cos(x)", result.output)

        val chain = exec("""{"operation":"derivative","expression":"sin(x^2)"}""")
        assertTrue(chain.ok)
        assertEquals("结果: 2*x*cos(x^2)", chain.output)
    }

    @Test
    fun `integrate-不定积分与定积分`() = runBlocking {
        val indefinite = exec("""{"operation":"integrate","expression":"3*x^2+2*x+1"}""")
        assertTrue(indefinite.ok)
        assertEquals("结果: x^3+x^2+x+C", indefinite.output)

        val sinDouble = exec("""{"operation":"integrate","expression":"sin(2*x)"}""")
        assertTrue(sinDouble.ok)
        assertEquals("结果: -cos(2*x)/2+C", sinDouble.output)

        val reciprocal = exec("""{"operation":"integrate","expression":"1/x"}""")
        assertTrue(reciprocal.ok)
        assertEquals("结果: ln(abs(x))+C", reciprocal.output)

        val definite = exec(
            """{"operation":"definite_integral","expression":"x^2","lower":"0","upper":"3"}""",
        )
        assertTrue(definite.ok)
        assertEquals("结果: 9", definite.output)
    }

    @Test
    fun `错误路径-非法operation缺上下限缺variables与不支持积分`() = runBlocking {
        val invalid = exec("""{"operation":"unknown","expression":"x+1"}""")
        assertFalse(invalid.ok)
        assertTrue(invalid.output, invalid.output.contains("operation"))

        val missingLimits = exec("""{"operation":"definite_integral","expression":"x^2","lower":"0"}""")
        assertFalse(missingLimits.ok)
        assertTrue(missingLimits.output, missingLimits.output.contains("lower"))

        val missingVars = exec("""{"operation":"solve","expression":"x+y=5; x-y=1"}""")
        assertFalse(missingVars.ok)
        assertTrue(missingVars.output, missingVars.output.contains("variables"))

        val unsupported = exec("""{"operation":"integrate","expression":"sin(x^2)"}""")
        assertFalse(unsupported.ok)
        assertTrue(unsupported.output, unsupported.output.contains("暂不支持符号积分"))
    }

    @Test
    fun `错误路径-定积分区间内奇点返回失败`() = runBlocking {
        val result = exec(
            """{"operation":"definite_integral","expression":"1/x","lower":"-1","upper":"1"}""",
        )
        assertFalse(result.ok)
        assertTrue(result.output, result.output.contains("奇点") || result.output.contains("无定义"))
        assertTrue(result.output, result.output.contains("x = 0"))
    }

    @Test
    fun `schema 声明 operation 枚举与新参数且仅 expression 必填`() {
        val spec = registry.get("calculate")!!.spec
        val schema = toolParameterSchema(spec.parameters)
        val properties = schema["properties"]!!.jsonObject

        val operation = properties["operation"]!!.jsonObject
        assertEquals("string", operation["type"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("evaluate", "solve", "derivative", "integrate", "definite_integral", "simplify"),
            operation["enum"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        for (name in listOf("variable", "variables", "lower", "upper", "precision")) {
            assertTrue("缺少参数 $name", properties.containsKey(name))
        }
        assertEquals(
            listOf("expression"),
            schema["required"]!!.jsonArray.map { it.jsonPrimitive.content },
        )

        // 三协议共用同一份 WireToolDef 声明
        val wire = registry.effectiveWireTools(emptyMap()).first { it.name == "calculate" }
        assertEquals(spec.parameters.map { it.name }, wire.parameters.map { it.name })
        assertTrue(wire.parameters.first { it.name == "operation" }.enumValues.contains("solve"))
    }

    private fun exec(json: String): ToolExecResult = runBlocking {
        registry.get("calculate")!!.execute(ToolArgs.parse(json), context())
    }

    private fun context(): ToolContext = ToolContext(
        boundPage = PageRef("nb1", "p0001"),
        config = ToolConfig("calculate"),
        workspace = null,
        skillStore = EmptySkillStore,
        webSearch = EmptyWebSearch,
    )
}

private object EmptySkillStore : SkillManagerPort {
    override fun listSkills(): List<AgentSkill> = emptyList()

    override fun findEnabledSkill(idOrName: String): AgentSkill? = null

    override fun createOrUpdateSkill(
        id: String?,
        name: String,
        summary: String,
        content: String,
        enabled: Boolean,
    ): AgentSkill = AgentSkill(id ?: "auto", name, summary, content, enabled)
}

private object EmptyWebSearch : WebSearchPort {
    override suspend fun search(
        query: String,
        maxResults: Int,
        settings: Map<String, String>,
    ): Result<List<WebSearchItem>> = Result.success(emptyList())
}
