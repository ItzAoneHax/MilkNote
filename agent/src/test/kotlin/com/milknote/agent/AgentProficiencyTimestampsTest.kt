package com.milknote.agent

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

// =============================================================================
// B:内置 Skill/工具只读 + 熟练度分析授权 + get_element_timestamps
// =============================================================================

/** 旧文件里的内置定义/覆盖必须被忽略,自定义能力保持可编辑可运行 */
class BuiltinCapabilityReadOnlyTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun newStore(files: File = tmp.newFolder()) = AgentStore(files, tmp.newFolder())

    @Test
    fun `旧skills文件里的内置改动被忽略且以规范定义补齐`() {
        val files = tmp.newFolder()
        File(files, "agent").mkdirs()
        val legacy = AgentStore.SkillBook(
            listOf(
                AgentSkill(
                    id = "cornell_notes",
                    name = "用户改过的名字",
                    summary = "用户改过的摘要",
                    content = "用户改过的正文",
                    enabled = false,
                    builtin = false,
                ),
                AgentSkill(id = "my_skill", name = "我的技能", summary = "摘要", content = "正文"),
            ),
        )
        File(files, "agent/skills.json").writeText(
            json.encodeToString(AgentStore.SkillBook.serializer(), legacy),
        )
        val before = File(files, "agent/skills.json").readText()

        val skills = newStore(files).loadSkills()
        val cornell = skills.first { it.id == "cornell_notes" }

        assertEquals("康奈尔笔记整理", cornell.name)
        assertEquals(BuiltinSkills.cornellNotes.summary, cornell.summary)
        assertEquals(BuiltinSkills.cornellNotes.content, cornell.content)
        assertTrue("内置技能不可关闭", cornell.enabled)
        assertTrue(cornell.builtin)
        assertEquals(3, skills.count { it.builtin })
        val mine = skills.first { it.id == "my_skill" }
        assertFalse("自定义技能不得伪装成内置", mine.builtin)
        assertEquals("正文", mine.content)
        assertEquals("读取不得改写旧文件", before, File(files, "agent/skills.json").readText())
    }

    @Test
    fun `保存内置改动也会被规范回内置定义`() {
        val files = tmp.newFolder()
        val store = newStore(files)
        val edited = store.loadSkills().map {
            if (it.id == "formula_sheet") it.copy(name = "改名", content = "被改", enabled = false) else it
        }
        store.saveSkills(edited)

        val reopened = AgentStore(files, tmp.newFolder()).loadSkills()
        val formula = reopened.first { it.id == "formula_sheet" }
        assertEquals(BuiltinSkills.formulaSheet.name, formula.name)
        assertEquals(BuiltinSkills.formulaSheet.content, formula.content)
        assertTrue(formula.enabled)
        assertFalse(
            "落盘文件也不保留内置改动",
            File(files, "agent/skills.json").readText().contains("被改"),
        )
        val stored = json.decodeFromString<AgentStore.SkillBook>(
            File(files, "agent/skills.json").readText(),
        ).skills
        assertTrue("缺失的内置记录不新建", stored.none { it.id == "formula_sheet" })
    }

    @Test
    fun `旧tools文件里的内置覆盖被忽略而自定义工具配置保留`() {
        val files = tmp.newFolder()
        File(files, "agent").mkdirs()
        val legacy = AgentStore.ToolBook(
            listOf(
                ToolConfig("delete_page", enabled = false, requireConfirmation = false, displayName = "别删"),
                ToolConfig("calculate", enabled = false, displayName = "算一算"),
                ToolConfig("my_tool", enabled = false, requireConfirmation = true),
            ),
        )
        File(files, "agent/tools.json").writeText(
            json.encodeToString(AgentStore.ToolBook.serializer(), legacy),
        )
        val before = File(files, "agent/tools.json").readText()

        val configs = newStore(files).loadToolConfigs()

        assertFalse(configs.containsKey("delete_page"))
        assertFalse(configs.containsKey("calculate"))
        assertEquals(false, configs["my_tool"]!!.enabled)
        assertTrue(configs["my_tool"]!!.requireConfirmation == true)
        val preserved = File(files, "agent/tools.json").readText()
        assertEquals("读取不得改写旧文件", before, preserved)
        assertTrue("旧内置覆盖记录仍保留在文件里", preserved.contains("delete_page") && preserved.contains("calculate"))
        assertTrue(preserved.contains("my_tool"))
    }

    @Test
    fun `保存时内置工具覆盖也会被剔除`() {
        val files = tmp.newFolder()
        val store = newStore(files)
        store.saveToolConfigs(
            mapOf(
                "modify_page_element" to ToolConfig("modify_page_element", enabled = false, requireConfirmation = false),
                "my_tool" to ToolConfig("my_tool", enabled = false),
            ),
        )

        val configs = AgentStore(files, tmp.newFolder()).loadToolConfigs()
        assertEquals(setOf("my_tool"), configs.keys)
        val raw = json.decodeFromString<AgentStore.ToolBook>(
            File(files, "agent/tools.json").readText(),
        ).configs
        assertTrue("缺失的内置记录不新建", raw.none { it.id == "modify_page_element" })
    }

    @Test
    fun `工具注册表忽略内置覆盖且危险确认恒为开`() {
        val registry = BuiltinTools.createDefaultRegistry()
        val delete = registry.get("delete_page")!!.spec
        val modify = registry.get("modify_page_element")!!.spec
        val overrides = mapOf(
            "delete_page" to ToolConfig("delete_page", enabled = false, requireConfirmation = false),
            "modify_page_element" to ToolConfig("modify_page_element", requireConfirmation = false),
            "web_search" to ToolConfig(
                "web_search",
                enabled = false,
                displayName = "改名",
                customDescription = "改说明",
                settings = mapOf("engine" to "bing"),
            ),
            "calculate" to ToolConfig("calculate", enabled = false),
        )

        val deleteConfig = registry.effectiveConfig(delete, overrides)
        assertTrue("内置工具不可关闭", deleteConfig.enabled)
        assertTrue("危险工具默认确认不可关", deleteConfig.requireConfirmation == true)
        assertTrue(registry.requiresConfirmation(modify, registry.effectiveConfig(modify, overrides)))

        val search = registry.effectiveConfig(registry.get("web_search")!!.spec, overrides)
        assertTrue(search.enabled)
        assertNull("内置工具不可改显示名", search.displayName)
        assertNull(search.customDescription)
        assertEquals("auto", search.settings["engine"])

        val wire = registry.effectiveWireTools(overrides).map { it.name }
        assertTrue("内置工具始终对模型可见", "calculate" in wire && "web_search" in wire)
    }

    @Test
    fun `自定义工具仍受配置驱动`() {
        val registry = BuiltinTools.createDefaultRegistry()
        registry.register(FakeCustomTool())
        val overrides = mapOf(
            "my_tool" to ToolConfig("my_tool", enabled = false, displayName = "我的工具"),
        )

        val spec = registry.get("my_tool")!!.spec
        val config = registry.effectiveConfig(spec, overrides)
        assertFalse(config.enabled)
        assertEquals("我的工具", config.displayName)
        assertFalse(registry.effectiveWireTools(overrides).any { it.name == "my_tool" })
    }

    @Test
    fun `create_skill不能覆盖内置技能`() = kotlinx.coroutines.test.runTest {
        val registry = BuiltinTools.createDefaultRegistry()
        val skills = TimestampsSkillStore()
        val tool = registry.get("create_skill")!!

        val byId = tool.execute(
            ToolArgs.parse("""{"id":"cornell_notes","name":"改名","summary":"改摘要","content":"改正文"}"""),
            tsCtx(skills = skills),
        )
        assertFalse(byId.ok)
        assertTrue(byId.output.contains("内置"))

        val byName = tool.execute(
            ToolArgs.parse("""{"name":"Cornell Notes","summary":"s","content":"c"}"""),
            tsCtx(skills = skills),
        )
        assertFalse(byName.ok)

        val cornell = skills.skills.first { it.id == "cornell_notes" }
        assertEquals(BuiltinSkills.cornellNotes.name, cornell.name)
        assertEquals(BuiltinSkills.cornellNotes.content, cornell.content)

        val user = tool.execute(
            ToolArgs.parse("""{"name":"My Skill","summary":"s","content":"c"}"""),
            tsCtx(skills = skills),
        )
        assertTrue("自定义技能仍可新增", user.ok)
        assertTrue(skills.skills.any { it.id == "my_skill" && !it.builtin })
    }
}

/** 工具参数/范围/分页与输出字段 */
class ElementTimestampsToolTest {

    private val registry = BuiltinTools.createDefaultRegistry()
    private val tool get() = registry.get(BuiltinTools.ELEMENT_TIMESTAMPS_TOOL_ID)!!
    private val page = PageRef("nb1", "p0001")

    @Test
    fun `注册表包含新工具且默认可见但强制确认`() {
        assertNotNull(registry.get(BuiltinTools.ELEMENT_TIMESTAMPS_TOOL_ID))
        val spec = tool.spec
        assertTrue("内置确认不可被用户关闭", spec.defaultRequireConfirmation)
        assertTrue(spec.builtin)
        assertFalse(spec.recordOutputInTrace)
        assertTrue(
            "普通请求也必须能看到工具,由执行前确认取得授权",
            registry.effectiveWireTools(emptyMap()).any { it.name == spec.id },
        )
    }

    @Test
    fun `直接执行缺少授权也拒绝`() = kotlinx.coroutines.test.runTest {
        val ws = TimestampWorkspace(sampleElements(2))
        val result = tool.execute(
            ToolArgs.parse("""{"x1":10,"y1":20}"""),
            tsCtx(ws, grant = null),
        )
        assertFalse(result.ok)
        assertTrue(result.output, result.output.contains("授权"))
        assertTrue(ws.timestampCalls.isEmpty())
    }

    @Test
    fun `严格参数校验`() = kotlinx.coroutines.test.runTest {
        val ws = TimestampWorkspace(sampleElements(1))

        val missing = tool.execute(ToolArgs.parse("""{"x1":10}"""), tsCtx(ws))
        assertFalse(missing.ok)

        val halfPair = tool.execute(ToolArgs.parse("""{"x1":10,"y1":20,"x2":30}"""), tsCtx(ws))
        assertFalse(halfPair.ok)
        assertTrue(halfPair.output.contains("x2"))

        val nan = tool.execute(ToolArgs.parse("""{"x1":"NaN","y1":20}"""), tsCtx(ws))
        assertFalse(nan.ok)
        assertTrue(nan.output.contains("有限"))

        val outOfRange = tool.execute(ToolArgs.parse("""{"x1":1001,"y1":20}"""), tsCtx(ws))
        assertFalse(outOfRange.ok)
        assertTrue(outOfRange.output.contains("范围"))

        val negative = tool.execute(ToolArgs.parse("""{"x1":-1,"y1":20}"""), tsCtx(ws))
        assertFalse(negative.ok)

        val badOffset = tool.execute(ToolArgs.parse("""{"x1":10,"y1":20,"offset":-1}"""), tsCtx(ws))
        assertFalse(badOffset.ok)

        val badBool = tool.execute(
            ToolArgs.parse("""{"x1":10,"y1":20,"include_erased":"maybe"}"""),
            tsCtx(ws),
        )
        assertFalse(badBool.ok)

        val objectX = tool.execute(ToolArgs.parse("""{"x1":{},"y1":20}"""), tsCtx(ws))
        assertFalse(objectX.ok)

        assertTrue("非法参数不得触达页面", ws.timestampCalls.isEmpty())
    }

    @Test
    fun `双无效顶点按已提供字段拒绝而不是退化为点选`() = kotlinx.coroutines.test.runTest {
        val ws = TimestampWorkspace(sampleElements(2))

        val bothStrings = tool.execute(
            ToolArgs.parse("""{"x1":10,"y1":20,"x2":"abc","y2":"abc"}"""),
            tsCtx(ws),
        )
        assertFalse("两个非法顶点不得当成无参点选", bothStrings.ok)
        assertTrue(bothStrings.output, bothStrings.output.contains("x2") && bothStrings.output.contains("有限"))

        val bothObjects = tool.execute(
            ToolArgs.parse("""{"x1":10,"y1":20,"x2":{},"y2":{}}"""),
            tsCtx(ws),
        )
        assertFalse(bothObjects.ok)
        assertTrue(bothObjects.output.contains("x2"))

        val mixedValidInvalid = tool.execute(
            ToolArgs.parse("""{"x1":10,"y1":20,"x2":30,"y2":"abc"}"""),
            tsCtx(ws),
        )
        assertFalse(mixedValidInvalid.ok)
        assertTrue(mixedValidInvalid.output.contains("y2"))

        val explicitNulls = tool.execute(
            ToolArgs.parse("""{"x1":10,"y1":20,"x2":null,"y2":null}"""),
            tsCtx(ws),
        )
        assertFalse("显式 null 也算已提供字段,不能当点选", explicitNulls.ok)

        assertTrue("非法顶点不得触达页面", ws.timestampCalls.isEmpty())
    }

    @Test
    fun `分页参数拒绝小数与结构错误且不截断`() = kotlinx.coroutines.test.runTest {
        val ws = TimestampWorkspace(sampleElements(5))

        val fractionalOffset = tool.execute(
            ToolArgs.parse("""{"x1":10,"y1":20,"offset":1.5}"""),
            tsCtx(ws),
        )
        assertFalse("1.5 不能被截断成 1", fractionalOffset.ok)
        assertTrue(fractionalOffset.output.contains("整数"))

        val fractionalLimit = tool.execute(
            ToolArgs.parse("""{"x1":10,"y1":20,"limit":2.5}"""),
            tsCtx(ws),
        )
        assertFalse(fractionalLimit.ok)

        val booleanOffset = tool.execute(
            ToolArgs.parse("""{"x1":10,"y1":20,"offset":true}"""),
            tsCtx(ws),
        )
        assertFalse(booleanOffset.ok)

        val objectLimit = tool.execute(
            ToolArgs.parse("""{"x1":10,"y1":20,"limit":{}}"""),
            tsCtx(ws),
        )
        assertFalse(objectLimit.ok)

        assertTrue("非法分页参数不得触达页面", ws.timestampCalls.isEmpty())

        val numericString = tool.execute(
            ToolArgs.parse("""{"x1":10,"y1":20,"offset":"2","limit":"2"}"""),
            tsCtx(ws),
        )
        assertTrue(numericString.ok)
        assertTrue(numericString.output, numericString.output.contains("本次返回 3-4"))
        assertEquals(2, numericString.output.lineSequence().count { it.contains("kind=") })

        val wholeDecimal = tool.execute(
            ToolArgs.parse("""{"x1":10,"y1":20,"offset":2.0,"limit":2.0}"""),
            tsCtx(ws),
        )
        assertTrue(wholeDecimal.ok)
        assertTrue(wholeDecimal.output.contains("本次返回 3-4"))
        assertEquals(2, ws.timestampCalls.size)
    }

    @Test
    fun `布尔参数结构错误被拒绝`() = kotlinx.coroutines.test.runTest {
        val ws = TimestampWorkspace(sampleElements(1))

        listOf(
            """{"x1":10,"y1":20,"include_erased":{}}""",
            """{"x1":10,"y1":20,"include_erased":1}""",
            """{"x1":10,"y1":20,"include_erased":"yes"}""",
            """{"x1":10,"y1":20,"include_erased":null}""",
        ).forEach { args ->
            val result = tool.execute(ToolArgs.parse(args), tsCtx(ws))
            assertFalse("include_erased 结构错误必须拒绝:$args", result.ok)
        }
        assertTrue("非法布尔不得触达页面", ws.timestampCalls.isEmpty())

        val stringTrue = tool.execute(
            ToolArgs.parse("""{"x1":10,"y1":20,"include_erased":"true"}"""),
            tsCtx(ws),
        )
        assertTrue(stringTrue.ok)
        assertTrue(ws.timestampCalls.last().third)
    }

    @Test
    fun `页面不存在或不可用时报错`() = kotlinx.coroutines.test.runTest {
        val ws = TimestampWorkspace(sampleElements(1))
        val missing = tool.execute(
            ToolArgs.parse("""{"x1":10,"y1":20}"""),
            tsCtx(ws, grant = PageRef("nb1", "p9999")),
        )
        assertFalse(missing.ok)
        assertTrue(missing.output.contains("不存在") || missing.output.contains("不可用"))

        val noPort = tool.execute(
            ToolArgs.parse("""{"x1":10,"y1":20}"""),
            tsCtx(ws, grant = page).copy(workspace = null),
        )
        assertFalse(noPort.ok)
    }

    @Test
    fun `点选与任意顶点顺序都归一化为页面矩形`() = kotlinx.coroutines.test.runTest {
        val ws = TimestampWorkspace(sampleElements(1))

        val point = tool.execute(ToolArgs.parse("""{"x1":120,"y1":340}"""), tsCtx(ws))
        assertTrue(point.ok)
        assertEquals(Rect4(120f, 340f, 120f, 340f), ws.timestampCalls.last().second)
        assertTrue(point.output.contains("点"))

        val reversed = tool.execute(
            ToolArgs.parse("""{"x1":800,"y1":900,"x2":200,"y2":100}"""),
            tsCtx(ws),
        )
        assertTrue(reversed.ok)
        assertEquals(Rect4(200f, 100f, 800f, 900f), ws.timestampCalls.last().second)
    }

    @Test
    fun `include_erased透传且默认false`() = kotlinx.coroutines.test.runTest {
        val ws = TimestampWorkspace(sampleElements(1))

        tool.execute(ToolArgs.parse("""{"x1":10,"y1":20}"""), tsCtx(ws))
        assertFalse(ws.timestampCalls.last().third)

        tool.execute(ToolArgs.parse("""{"x1":10,"y1":20,"include_erased":true}"""), tsCtx(ws))
        assertTrue(ws.timestampCalls.last().third)
    }

    @Test
    fun `输出字段与未知值处理`() = kotlinx.coroutines.test.runTest {
        val ws = TimestampWorkspace(
            listOf(
                ElementTimestamp("s1", "stroke", 1700000000000L, 1520L, false),
                ElementTimestamp("s2", "stroke", null, null, true),
                ElementTimestamp("c1", "card", 1700000000100L, null, false),
                ElementTimestamp("i1", "image", null, null, false),
            ),
        )
        val result = tool.execute(
            ToolArgs.parse("""{"x1":0,"y1":0,"x2":1000,"y2":1400,"include_erased":true}"""),
            tsCtx(ws),
        )
        assertTrue(result.ok)
        assertTrue(result.output.contains("id=s1 kind=stroke createdAtMs=1700000000000 durationMs=1520 erased=false"))
        assertTrue(result.output.contains("id=s2 kind=stroke createdAtMs=unknown durationMs=unknown erased=true"))
        assertTrue(result.output.contains("id=c1 kind=card createdAtMs=1700000000100 erased=false"))
        assertFalse("非笔迹不算时长", result.output.contains("id=c1 kind=card createdAtMs=1700000000100 durationMs"))
        assertTrue(result.output.contains("id=i1 kind=image createdAtMs=unknown erased=false"))
        assertFalse(result.output.contains("secret note"))
        assertFalse(result.output.contains("agent/att/"))
        assertTrue("结果摘要只给计数", result.summary.contains("4"))
    }

    @Test
    fun `分页可遍历全部叠层且limit封顶30`() = kotlinx.coroutines.test.runTest {
        val elements = (1..75).map { i ->
            ElementTimestamp("e%02d".format(i), "stroke", 1700000000000L + i, 100L, false)
        }
        val ws = TimestampWorkspace(elements)

        val first = tool.execute(
            ToolArgs.parse("""{"x1":0,"y1":0,"x2":1000,"y2":1400,"limit":30}"""),
            tsCtx(ws),
        )
        assertTrue(first.ok)
        assertTrue(first.output.contains("共 75"))
        assertTrue(first.output.contains("nextOffset=30"))
        assertEquals(30, first.output.lineSequence().count { it.contains("kind=") })

        val second = tool.execute(
            ToolArgs.parse("""{"x1":0,"y1":0,"x2":1000,"y2":1400,"limit":30,"offset":30}"""),
            tsCtx(ws),
        )
        assertTrue(second.output.contains("nextOffset=60"))

        val third = tool.execute(
            ToolArgs.parse("""{"x1":0,"y1":0,"x2":1000,"y2":1400,"limit":30,"offset":60}"""),
            tsCtx(ws),
        )
        assertTrue(third.ok)
        assertFalse("最后一页不再给 nextOffset", third.output.contains("nextOffset="))
        assertEquals(15, third.output.lineSequence().count { it.contains("kind=") })

        val clamped = tool.execute(
            ToolArgs.parse("""{"x1":0,"y1":0,"limit":999}"""),
            tsCtx(ws),
        )
        assertEquals(30, clamped.output.lineSequence().count { it.contains("kind=") })

        val beyond = tool.execute(
            ToolArgs.parse("""{"x1":0,"y1":0,"offset":900}"""),
            tsCtx(ws),
        )
        assertTrue(beyond.ok)
        assertEquals(0, beyond.output.lineSequence().count { it.contains("kind=") })
        assertFalse(beyond.output.contains("nextOffset="))
    }

    @Test
    fun `空区域返回空计数`() = kotlinx.coroutines.test.runTest {
        val ws = TimestampWorkspace(emptyList())
        val result = tool.execute(ToolArgs.parse("""{"x1":10,"y1":20}"""), tsCtx(ws))
        assertTrue(result.ok)
        assertTrue(result.output.contains("0"))
        assertEquals("0 个元素时序", result.summary)
    }
}

// ==================== 测试替身 ====================

private fun sampleElements(n: Int): List<ElementTimestamp> = (1..n).map { i ->
    ElementTimestamp("e$i", if (i % 2 == 0) "card" else "stroke", 1700000000000L + i, 100L + i, false)
}

private class TimestampWorkspace(
    private val elements: List<ElementTimestamp>,
) : NotebookWorkspacePort {
    val pages = arrayListOf(PageInfo("p0001", 1, true), PageInfo("p0002", 2, false))
    val timestampCalls = ArrayList<Triple<PageRef, Rect4, Boolean>>()

    override suspend fun listPages(notebookId: String): List<PageInfo> = pages.toList()

    override suspend fun pageDimensions(page: PageRef): PageDimensions? =
        if (pages.any { it.pageId == page.pageId }) PageDimensions(1000f, 1400f) else null

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

    override suspend fun elementTimestamps(
        page: PageRef,
        rect: Rect4,
        includeErased: Boolean,
    ): List<ElementTimestamp>? {
        timestampCalls += Triple(page, rect, includeErased)
        if (pages.none { it.pageId == page.pageId }) return null
        return elements
    }
}

private class FakeCustomTool : AgentTool {
    override val spec = ToolSpec(
        id = "my_tool",
        defaultDisplayName = "我的工具",
        defaultDescription = "自定义工具",
        category = ToolCategory.COMPUTE,
        builtin = false,
    )

    override suspend fun execute(args: kotlinx.serialization.json.JsonObject, ctx: ToolContext): ToolExecResult =
        ToolExecResult(true, "ok")
}

private class TimestampsSkillStore(initial: List<AgentSkill> = BuiltinSkills.all) : SkillManagerPort {
    val skills = initial.toMutableList()

    override fun listSkills(): List<AgentSkill> = skills.toList()

    override fun findEnabledSkill(idOrName: String): AgentSkill? =
        skills.firstOrNull { it.enabled && (it.id == idOrName || it.name == idOrName) }

    override fun createOrUpdateSkill(
        id: String?,
        name: String,
        summary: String,
        content: String,
        enabled: Boolean,
    ): AgentSkill {
        val skill = AgentSkill(id ?: "auto", name, summary, content, enabled)
        skills.removeAll { it.id == skill.id }
        skills += skill
        return skill
    }
}

private class NullWebSearch : WebSearchPort {
    override suspend fun search(
        query: String,
        maxResults: Int,
        settings: Map<String, String>,
    ): Result<List<WebSearchItem>> = Result.success(emptyList())
}

private fun tsCtx(
    workspace: TimestampWorkspace? = null,
    grant: PageRef? = PageRef("nb1", "p0001"),
    skills: SkillManagerPort = TimestampsSkillStore(),
): ToolContext = ToolContext(
    boundPage = PageRef("nb1", "p0001"),
    config = ToolConfig("get_element_timestamps"),
    workspace = workspace,
    skillStore = skills,
    webSearch = NullWebSearch(),
    elementTimestampsPage = grant,
)
