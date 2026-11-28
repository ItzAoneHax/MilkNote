package com.milknote.agent

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 函数图像共享模型:表达式规范化、多曲线配色、逐点断段与工具 schema/执行 */
class FunctionGraphTest {

    @Test
    fun `常量与抛物线可采样`() {
        val spec = FunctionGraph.build(listOf("2", "x^2"))
        assertEquals("2", spec.curves[0].expression)
        assertEquals("x^2", spec.curves[1].expression)
        assertNotEquals(spec.curves[0].color, spec.curves[1].color)

        val constant = FunctionGraphSampler.segments(spec, 0, samples = 40)
        assertEquals(1, constant.size)
        assertTrue(constant.first().all { kotlin.math.abs(it.y - 2.0) < 1e-9 })

        val parabola = FunctionGraphSampler.segments(spec, 1, samples = 80)
        assertEquals(1, parabola.size)
        val mid = parabola.first().firstOrNull { kotlin.math.abs(it.x) < 0.2 }
        assertTrue(mid != null && mid.y >= 0.0)
    }

    @Test
    fun `LaTeX 分数根式三角与绝对值`() {
        assertEquals("((1)/(x))", FunctionGraph.normalizeExpression("\\frac{1}{x}"))
        assertEquals("sqrt(x)", FunctionGraph.normalizeExpression("\\sqrt{x}"))
        assertEquals("((x)^(1/(3)))", FunctionGraph.normalizeExpression("\\sqrt[3]{x}"))
        assertEquals("sin(x)", FunctionGraph.normalizeExpression("\\sin(x)"))
        assertEquals("x^(2)", FunctionGraph.normalizeExpression("x^{2}"))
        assertEquals("abs(x)", FunctionGraph.normalizeExpression("\\left|x\\right|"))
        assertEquals("abs(x)", FunctionGraph.normalizeExpression("|x|"))
        assertEquals("x^2", FunctionGraph.normalizeExpression("y = x^2"))
        assertEquals("x^2", FunctionGraph.normalizeExpression("f(x)=x^2"))
        assertEquals("2*pi x", FunctionGraph.normalizeExpression("2\\cdot\\pi x"))
    }

    @Test
    fun `六条曲线按调色板配色且超限报中文错误`() {
        val expressions = (1..6).map { "x^$it" }
        val spec = FunctionGraph.build(expressions)
        assertEquals(6, spec.curves.size)
        assertEquals(6, spec.curves.map { it.color }.distinct().size)
        assertEquals(FunctionGraph.colorAt(0), spec.curves.first().color)

        val tooMany = runCatching { FunctionGraph.build((1..7).map { "x^$it" }) }
        assertTrue(tooMany.isFailure)
        assertTrue(tooMany.exceptionOrNull()?.message!!.contains("最多支持"))

        val tooFew = runCatching { FunctionGraph.build(emptyList()) }
        assertTrue(tooFew.isFailure)
        assertTrue(tooFew.exceptionOrNull()?.message!!.contains("至少"))
    }

    @Test
    fun `正弦分数根式可采样`() {
        val sinSpec = FunctionGraph.build(
            listOf("\\sin(x)"),
            viewport = GraphViewport(-1.0, 4.0, -2.0, 2.0),
        )
        val sinSegments = FunctionGraphSampler.segments(sinSpec, 0, samples = 500)
        val sinPoint = sinSegments.flatten().minByOrNull { kotlin.math.abs(it.x - Math.PI / 2) }
        assertTrue(sinPoint != null && kotlin.math.abs(sinPoint.y - 1.0) < 1e-3)

        val fracSpec = FunctionGraph.build(
            listOf("\\frac{1}{x}"),
            viewport = GraphViewport(0.5, 2.0, 0.0, 2.5),
        )
        val fracSegments = FunctionGraphSampler.segments(fracSpec, 0, samples = 160)
        val fracPoint = fracSegments.flatten().minByOrNull { kotlin.math.abs(it.x - 1.0) }
        assertTrue(fracPoint != null && kotlin.math.abs(fracPoint.y - 1.0) < 0.05)

        val rootSpec = FunctionGraph.build(
            listOf("\\sqrt{x}"),
            viewport = GraphViewport(-1.0, 4.0, -1.0, 3.0),
        )
        val rootPoints = FunctionGraphSampler.segments(rootSpec, 0, samples = 200).flatten()
        assertTrue(rootPoints.isNotEmpty())
        assertTrue("sqrt 的负半轴不应有点", rootPoints.all { it.x >= -1e-9 })
        val rootPoint = rootPoints.minByOrNull { kotlin.math.abs(it.x - 4.0) }
        assertTrue(rootPoint != null && kotlin.math.abs(rootPoint.y - 2.0) < 0.05)
    }

    @Test
    fun `非法表达式与视口被拒绝`() {
        listOf(
            "" to "为空",
            "a + 1" to "未知变量",
            "x_1" to "下标",
            "x; y" to "一次只能",
            "x=" to "等号",
            "\\frac{1}" to "\\frac",
            "\\unknown{x}" to "不支持",
            "\\square" to "占位符",
        ).forEach { (raw, message) ->
            val result = runCatching { FunctionGraph.normalizeExpression(raw) }
            assertTrue("应拒绝:$raw", result.isFailure)
            assertTrue(
                "错误应包含「$message」,实际:${result.exceptionOrNull()?.message}",
                result.exceptionOrNull()?.message!!.contains(message),
            )
        }

        listOf(
            GraphViewport(1.0, 1.0, -1.0, 1.0),
            GraphViewport(-1.0, 1.0, 2.0, 2.0),
            GraphViewport(Double.NaN, 1.0, -1.0, 1.0),
            GraphViewport(-1001.0, 1000.0, -1.0, 1.0),
        ).forEach { vp ->
            assertTrue(runCatching { FunctionGraph.validateViewport(vp) }.isFailure)
        }
    }

    @Test
    fun `一分之x在零附近断开且不连线`() {
        val spec = FunctionGraph.build(listOf("1/x"), viewport = GraphViewport(-1.0, 1.0, -10.0, 10.0))
        val segments = FunctionGraphSampler.segments(spec, 0, samples = 400)
        assertTrue("1/x 应至少分成两支", segments.size >= 2)
        assertTrue(segments.all { seg -> seg.all { it.x < 0 } || seg.all { it.x > 0 } })
        assertTrue(segments.any { seg -> seg.all { it.x < 0 } })
        assertTrue(segments.any { seg -> seg.all { it.x > 0 } })
    }

    @Test
    fun `归一化裁剪与最小尺寸`() {
        val crop = GraphCrop.fromDrag(0.8, 0.7, 0.2, 0.1)
        assertEquals(0.2, crop.left, 1e-9)
        assertEquals(0.1, crop.top, 1e-9)
        assertEquals(0.8, crop.right, 1e-9)
        assertEquals(0.7, crop.bottom, 1e-9)
        assertTrue(runCatching { FunctionGraph.validateCrop(crop) }.isSuccess)
        assertTrue(
            runCatching {
                FunctionGraph.validateCrop(GraphCrop(0.1, 0.1, 0.105, 0.105))
            }.isFailure,
        )
    }

    @Test
    fun `工具 schema 生成数组 items 并容错读取数组参数`() {
        val registry = BuiltinTools.createDefaultRegistry()
        val tool = registry.get(BuiltinTools.INSERT_FUNCTION_GRAPH_TOOL_ID)!!
        assertFalse(tool.spec.defaultRequireConfirmation)
        val schema = toolParameterSchema(tool.spec.parameters)
        val expressions = schema["properties"]!!.let {
            (it as kotlinx.serialization.json.JsonObject)["expressions"] as kotlinx.serialization.json.JsonObject
        }
        assertEquals("array", expressions["type"]!!.let { (it as kotlinx.serialization.json.JsonPrimitive).content })
        val items = expressions["items"] as kotlinx.serialization.json.JsonObject
        assertEquals("string", (items["type"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertTrue(tool.spec.parameters.first { it.name == "expressions" }.required)

        val args = ToolArgs.parse(
            """{"expressions":["x^2","\\sin(x)"],"colors":["#FF0000",65280]}""",
        )
        assertEquals(listOf("x^2", "\\sin(x)"), ToolArgs.stringList(args, "expressions"))
        val colors = ToolArgs.colorList(args, "colors")
        assertEquals(0xFFFF0000L, colors[0])
        assertEquals(0xFF00FF00L, colors[1])

        val fallback = ToolArgs.parse("""{"expressions":"x^2\n\\sqrt{x}"}""")
        val fallbackList = ToolArgs.stringList(fallback, "expressions")
        assertEquals(2, fallbackList.size)
        assertEquals("\\sqrt{x}", fallbackList[1])

        val semicolonArray = ToolArgs.parse("""{"expressions":["\\sin\\;(x)","x^2"]}""")
        assertEquals(
            listOf("\\sin\\;(x)", "x^2"),
            ToolArgs.stringList(semicolonArray, "expressions"),
        )
        val jsonArrayString = ToolArgs.parse("""{"expressions":"[\"x^2\",\"sin(x)\"]"}""")
        assertEquals(
            listOf("x^2", "sin(x)"),
            ToolArgs.stringList(jsonArrayString, "expressions"),
        )
        val singleFallback = ToolArgs.parse("""{"expressions":"x^2; sin(x)"}""")
        assertEquals(
            listOf("x^2", "sin(x)"),
            ToolArgs.stringList(singleFallback, "expressions"),
        )
    }

    @Test
    fun `工具一次调用只插一张图并包含全部曲线`() = runTest {
        val workspace = FakeGraphWorkspace()
        val tool = BuiltinTools.createDefaultRegistry()
            .get(BuiltinTools.INSERT_FUNCTION_GRAPH_TOOL_ID)!!
        val ctx = ToolContext(
            boundPage = PageRef("nb1", "p0001"),
            config = ToolConfig(BuiltinTools.INSERT_FUNCTION_GRAPH_TOOL_ID),
            workspace = workspace,
            skillStore = NoSkills(),
            webSearch = NoSearch(),
        )
        val result = tool.execute(
            ToolArgs.parse(
                """{"expressions":["x^2","\\sin(x)"],"colors":["#FF0000"],"x_min":-2,"x_max":2}""",
            ),
            ctx,
        )
        assertTrue(result.output, result.ok)
        assertTrue(result.output.contains("2 条曲线"))
        assertTrue(result.output.contains("视口"))
        assertEquals("一次调用只插入一张图", 1, workspace.calls.size)
        val (page, spec, x, y, width) = workspace.calls.single()
        assertEquals(PageRef("nb1", "p0001"), page)
        assertEquals(2, spec.curves.size)
        assertEquals(0xFFFF0000L, spec.curves[0].color)
        assertEquals(FunctionGraph.colorAt(1), spec.curves[1].color)
        assertEquals(-2.0, spec.viewport.xMin, 1e-9)
        assertEquals(2.0, spec.viewport.xMax, 1e-9)
        assertEquals(80f, x, 1e-3f)
        assertEquals(168f, y, 1e-3f)
        assertEquals(550f, width, 1e-3f)

        val failed = tool.execute(
            ToolArgs.parse("""{"expressions":["x^2","y"]}"""),
            ctx,
        )
        assertFalse(failed.ok)
        assertEquals("失败不得插入", 1, workspace.calls.size)
    }
}

private data class GraphInsertCall(
    val page: PageRef,
    val spec: FunctionGraphSpec,
    val x: Float,
    val y: Float,
    val width: Float,
)

private class FakeGraphWorkspace : NotebookWorkspacePort {
    val calls = ArrayList<GraphInsertCall>()

    override suspend fun listPages(notebookId: String): List<PageInfo> = listOf(PageInfo("p0001", 1, true))

    override suspend fun pageDimensions(page: PageRef): PageDimensions = PageDimensions(1000f, 1400f)

    override suspend fun addPage(
        notebookId: String,
        position: String,
        anchorPageId: String?,
        switchTo: Boolean,
    ): PageInfo? = null

    override suspend fun deletePage(notebookId: String, pageId: String): Boolean = false

    override suspend fun insertMarkdownCard(
        page: PageRef,
        markdown: String,
        xPx: Float,
        yPx: Float,
        widthPx: Float,
    ): String? = null

    override suspend fun insertFunctionGraph(
        page: PageRef,
        spec: FunctionGraphSpec,
        xPx: Float,
        yPx: Float,
        widthPx: Float,
    ): String {
        calls += GraphInsertCall(page, spec, xPx, yPx, widthPx)
        return "img_${calls.size}"
    }

    override suspend fun listElements(page: PageRef): List<PageElementSummary> = emptyList()

    override suspend fun moveElement(
        page: PageRef,
        elementId: String,
        elementKind: String?,
        targetX: Float?,
        targetY: Float?,
        dx: Float?,
        dy: Float?,
    ): Boolean = false

    override suspend fun deleteElement(page: PageRef, elementId: String, elementKind: String?): Boolean = false
}

private class NoSkills : SkillManagerPort {
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

private class NoSearch : WebSearchPort {
    override suspend fun search(
        query: String,
        maxResults: Int,
        settings: Map<String, String>,
    ): Result<List<WebSearchItem>> = Result.success(emptyList())
}
