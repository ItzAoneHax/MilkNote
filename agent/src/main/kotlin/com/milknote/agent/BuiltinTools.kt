package com.milknote.agent

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 11 个内置工具(定义只读,用户不能改开关/确认/名称/说明/设置):
 * - SEARCH  web_search
 * - COMPUTE calculate
 * - PAGE    add_page / delete_page(确认)
 * - CANVAS  insert_markdown / insert_function_graph / list_page_elements / modify_page_element(确认)
 * - TIME    get_element_timestamps(内置强制确认;用户同意后只读本次调用页)
 * - SKILL   read_skill / create_skill(内置技能不可覆盖)
 *
 * 只依赖 Ports.kt 的纯 Kotlin 端口,装配在 app 层。
 */
object BuiltinTools {

    /** 仅在用户逐次确认授权后读取页面时序的工具 id(工具层与控制器共用) */
    const val ELEMENT_TIMESTAMPS_TOOL_ID = "get_element_timestamps"

    /** 一次插入一张多曲线函数图像 */
    const val INSERT_FUNCTION_GRAPH_TOOL_ID = "insert_function_graph"

    private const val TIMESTAMP_PAGE_DEFAULT = 20
    private const val TIMESTAMP_PAGE_MAX = 30

    /** 应用自带工具 id(AgentStore 据此忽略旧覆盖) */
    val builtinToolIds: Set<String> by lazy { createDefaultRegistry().allSpecs().map { it.id }.toSet() }

    fun createDefaultRegistry(): ToolRegistry = ToolRegistry().apply {
        register(WebSearchTool())
        register(CalculateTool())
        register(AddPageTool())
        register(DeletePageTool())
        register(InsertMarkdownTool())
        register(InsertFunctionGraphTool())
        register(ListPageElementsTool())
        register(ModifyPageElementTool())
        register(GetElementTimestampsTool())
        register(ReadSkillTool())
        register(CreateSkillTool())
    }

    // ==================== SEARCH ====================

    private class WebSearchTool : AgentTool {
        override val spec = ToolSpec(
            id = "web_search",
            defaultDisplayName = "联网搜索",
            defaultDescription = "搜索互联网获取最新信息。只在需要外部资料或时效性内容时使用;" +
                "返回标题、链接与摘要。",
            category = ToolCategory.SEARCH,
            defaultEnabled = true,
            defaultRequireConfirmation = false,
            parameters = listOf(
                ToolParameter("query", "string", "搜索关键词,尽量具体", required = true),
                ToolParameter("max_results", "integer", "返回条数,默认 5,范围 1-10", required = false),
            ),
            settingOptions = listOf(
                ToolSettingOption(
                    key = "engine",
                    label = "搜索引擎",
                    defaultValue = "auto",
                    choices = listOf(
                        "auto" to "自动",
                        "bing" to "Bing",
                        "duckduckgo" to "DuckDuckGo",
                        "custom" to "自定义接口",
                    ),
                ),
                ToolSettingOption("max_results", "默认返回条数", "5", placeholder = "1-10"),
                ToolSettingOption(
                    key = "custom_url",
                    label = "自定义搜索地址",
                    defaultValue = "",
                    placeholder = "https://example.com/search?q={query}",
                ),
            ),
        )

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolExecResult {
            val query = ToolArgs.string(args, "query")
                ?: return ToolExecResult(false, "缺少 query 参数")
            val limit = (ToolArgs.int(args, "max_results")
                ?: ctx.config.settings["max_results"]?.toIntOrNull()
                ?: 5).coerceIn(1, 10)
            val result = ctx.webSearch.search(query, limit, ctx.config.settings)
            val items = result.getOrElse { t ->
                return ToolExecResult(false, "搜索失败:${t.message ?: t::class.simpleName}")
            }
            if (items.isEmpty()) {
                return ToolExecResult(true, "未找到与「$query」相关的结果。", "0 条结果")
            }
            val output = buildString {
                append("搜索「").append(query).append("」的结果:\n")
                items.take(limit).forEachIndexed { i, item ->
                    append(i + 1).append(". ").append(item.title).append('\n')
                    append("   ").append(item.url).append('\n')
                    if (item.snippet.isNotBlank()) append("   ").append(item.snippet).append('\n')
                }
            }.trim()
            return ToolExecResult(true, output, "找到 ${items.size} 条结果")
        }
    }

    // ==================== COMPUTE ====================

    private class CalculateTool : AgentTool {
        override val spec = ToolSpec(
            id = "calculate",
            defaultDisplayName = "数学计算",
            defaultDescription = "计算、化简、解方程与微积分。operation=evaluate(默认)时精确计算表达式,支持变量、" +
                "常用函数、阶乘与多语句(分号分隔);simplify 化简表达式;solve 解一元一次/二次方程或线性方程组" +
                "(方程组在 expression 内用分号或换行分隔,并给 variables 例如 x,y);derivative 求导;" +
                "integrate 求不定积分;definite_integral 求定积分(需 lower/upper)。复杂算式请用它验算,不要心算。",
            category = ToolCategory.COMPUTE,
            defaultEnabled = true,
            defaultRequireConfirmation = false,
            parameters = listOf(
                ToolParameter("expression", "string", "数学表达式或方程,例如 sqrt(3^2+4^2) 或 x+y=5; x-y=1", required = true),
                ToolParameter("precision", "integer", "保留的小数位数,默认 10,范围 0-15", required = false),
                ToolParameter(
                    "operation", "string", "操作类型,默认 evaluate",
                    enumValues = listOf(
                        "evaluate", "solve", "derivative", "integrate", "definite_integral", "simplify",
                    ),
                ),
                ToolParameter("variable", "string", "求解/求导/积分的目标变量,默认 x"),
                ToolParameter("variables", "string", "线性方程组的变量列表(逗号分隔),例如 x,y"),
                ToolParameter("lower", "string", "定积分下限(expression 为被积函数)"),
                ToolParameter("upper", "string", "定积分上限"),
            ),
        )

        override fun describeAction(args: JsonObject, ctx: ToolContext): String {
            val expression = ToolArgs.string(args, "expression") ?: "表达式"
            return when ((ToolArgs.string(args, "operation") ?: "evaluate").lowercase()) {
                "solve" -> "求解方程 $expression"
                "derivative" -> "求导 $expression"
                "integrate" -> "求不定积分 $expression"
                "definite_integral" -> "求定积分 $expression"
                "simplify" -> "化简 $expression"
                else -> "计算 $expression"
            }
        }

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolExecResult {
            val expression = ToolArgs.string(args, "expression")
                ?: return ToolExecResult(false, "缺少 expression 参数")
            val precision = (ToolArgs.int(args, "precision") ?: 10).coerceIn(0, 15)
            val operation = (ToolArgs.string(args, "operation") ?: "evaluate").trim().lowercase()
            return try {
                when (operation) {
                    "evaluate" -> evaluateNumeric(expression, precision)
                    "simplify" -> symbolic { SymbolicMathEvaluator.simplify(expression) }
                    "derivative" -> symbolic {
                        SymbolicMathEvaluator.derivative(expression, variableArg(args))
                    }
                    "integrate" -> symbolic {
                        SymbolicMathEvaluator.integrate(expression, variableArg(args))
                    }
                    "definite_integral" -> {
                        val lower = ToolArgs.string(args, "lower")
                        val upper = ToolArgs.string(args, "upper")
                        if (lower == null || upper == null) {
                            return ToolExecResult(
                                false,
                                "definite_integral 需要 lower 与 upper 参数(积分上下限)",
                                "缺少积分上下限",
                            )
                        }
                        symbolic {
                            SymbolicMathEvaluator.definiteIntegral(
                                expression,
                                variableArg(args),
                                lower,
                                upper,
                                precision,
                            )
                        }
                    }
                    "solve" -> {
                        val variables = parseVariables(ToolArgs.string(args, "variables"))
                        symbolic {
                            SymbolicMathEvaluator.solve(
                                expression,
                                ToolArgs.string(args, "variable"),
                                variables,
                            )
                        }
                    }
                    else -> ToolExecResult(
                        false,
                        "operation 只能是 evaluate/solve/derivative/integrate/definite_integral/simplify",
                        "操作类型无效",
                    )
                }
            } catch (e: SymbolicMathEvaluator.SymbolicException) {
                ToolExecResult(false, e.message ?: "符号运算失败", "计算失败")
            }
        }

        private fun evaluateNumeric(expression: String, precision: Int): ToolExecResult {
            val result = try {
                MathEvaluator.evaluate(expression, precision)
            } catch (e: MathEvaluator.EvaluationException) {
                return ToolExecResult(false, e.message ?: "表达式无法计算", "计算失败")
            }
            val output = buildString {
                append("结果: ").append(result.formatted)
                if (result.variables.isNotEmpty()) {
                    append('\n').append(
                        result.variables.entries.joinToString(", ") { (k, v) ->
                            "$k = ${MathEvaluator.formatNumber(v, precision)}"
                        },
                    )
                }
            }
            return ToolExecResult(true, output, result.formatted)
        }

        private fun variableArg(args: JsonObject): String =
            ToolArgs.string(args, "variable") ?: "x"

        private fun parseVariables(raw: String?): List<String>? {
            if (raw.isNullOrBlank()) return null
            return raw.split(',', '，', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        }

        private inline fun symbolic(block: () -> String): ToolExecResult {
            val text = block()
            val summary = if (text.length <= 60) text else text.take(57) + "..."
            return ToolExecResult(true, "结果: $text", summary)
        }
    }

    // ==================== PAGE ====================

    private class AddPageTool : AgentTool {
        override val spec = ToolSpec(
            id = "add_page",
            defaultDisplayName = "新增页面",
            defaultDescription = "在当前笔记本中新增一页。position=after/before 需要锚点页(默认当前页)," +
                "position=end 追加到末尾。",
            category = ToolCategory.PAGE,
            defaultEnabled = true,
            defaultRequireConfirmation = false,
            parameters = listOf(
                ToolParameter(
                    "position", "string", "插入位置:after(默认)/before/end",
                    enumValues = listOf("after", "before", "end"),
                ),
                ToolParameter("anchor_page_id", "string", "锚点页 id,默认当前页"),
                ToolParameter("switch_to", "boolean", "是否切换到新页,默认 true"),
            ),
        )

        override fun describeAction(args: JsonObject, ctx: ToolContext): String {
            val position = ToolArgs.string(args, "position") ?: "after"
            val anchor = ToolArgs.string(args, "anchor_page_id") ?: ctx.boundPage?.pageId
            return when (position.lowercase()) {
                "end" -> "在笔记本末尾新增页面"
                "before" -> "在第 ${anchor ?: "当前"} 页之前新增页面"
                else -> "在第 ${anchor ?: "当前"} 页之后新增页面"
            }
        }

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolExecResult {
            val workspace = ctx.workspace
                ?: return ToolExecResult(false, "当前未接入笔记本工作区")
            val notebookId = ctx.boundPage?.notebookId
                ?: return ToolExecResult(false, "未绑定笔记本,无法新增页面")
            val position = (ToolArgs.string(args, "position") ?: "after").lowercase()
            if (position !in setOf("after", "before", "end")) {
                return ToolExecResult(false, "position 只能是 after/before/end")
            }
            val anchor = ToolArgs.string(args, "anchor_page_id") ?: ctx.boundPage?.pageId
            if (position != "end" && anchor.isNullOrBlank()) {
                return ToolExecResult(false, "after/before 需要锚点页:请传 anchor_page_id 或先绑定当前页")
            }
            val switchTo = ToolArgs.bool(args, "switch_to") ?: true
            val page = workspace.addPage(notebookId, position, anchor, switchTo)
                ?: return ToolExecResult(false, "新增页面失败")
            return ToolExecResult(
                true,
                "已新增第 ${page.index} 页(${page.pageId})",
                "新增第 ${page.index} 页",
            )
        }
    }

    private class DeletePageTool : AgentTool {
        override val spec = ToolSpec(
            id = "delete_page",
            defaultDisplayName = "删除页面",
            defaultDescription = "删除指定页面或当前页。这是不可撤销的高危操作,会先请求用户确认。",
            category = ToolCategory.PAGE,
            defaultEnabled = true,
            defaultRequireConfirmation = true,
            parameters = listOf(
                ToolParameter("page_id", "string", "要删除的页面 id,默认当前页"),
                ToolParameter("page_number", "integer", "要删除的页码(从 1 开始),与 page_id 二选一"),
            ),
        )

        override fun describeAction(args: JsonObject, ctx: ToolContext): String {
            val pageId = ToolArgs.string(args, "page_id")
            val number = ToolArgs.int(args, "page_number")
            val currentId = ctx.boundPage?.pageId
            return when {
                pageId != null && pageId == currentId -> "删除当前页 ($pageId)"
                pageId != null -> "删除页面 $pageId"
                number != null -> "删除第 $number 页"
                currentId != null -> "删除当前页 ($currentId)"
                else -> "删除当前页"
            }
        }

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolExecResult {
            val workspace = ctx.workspace
                ?: return ToolExecResult(false, "当前未接入笔记本工作区")
            val notebookId = ctx.boundPage?.notebookId
                ?: return ToolExecResult(false, "未绑定笔记本,无法删除页面")
            val pages = runCatching { workspace.listPages(notebookId) }.getOrDefault(emptyList())
            val pageId = ToolArgs.string(args, "page_id")
                ?: ToolArgs.int(args, "page_number")?.let { n -> pages.firstOrNull { it.index == n }?.pageId }
                ?: ctx.boundPage?.pageId
                ?: return ToolExecResult(false, "未指定要删除的页面")
            if (pages.isNotEmpty() && pages.none { it.pageId == pageId }) {
                return ToolExecResult(
                    false,
                    "页面 $pageId 不存在;当前页:${pages.joinToString(", ") { "${it.index}:${it.pageId}" }}",
                )
            }
            val info = pages.firstOrNull { it.pageId == pageId }
            val ok = workspace.deletePage(notebookId, pageId)
            return if (ok) {
                ToolExecResult(
                    true,
                    if (info != null) "已删除第 ${info.index} 页($pageId)" else "已删除页面 $pageId",
                    if (info != null) "已删除第 ${info.index} 页" else "已删除页面",
                )
            } else {
                ToolExecResult(false, "删除页面失败(页面可能不可删除)")
            }
        }
    }

    // ==================== CANVAS ====================

    private class InsertMarkdownTool : AgentTool {
        override val spec = ToolSpec(
            id = "insert_markdown",
            defaultDisplayName = "插入 Markdown 卡片",
            defaultDescription = "在当前页插入一张 Markdown 卡片。x/y/width 取 0..1 时按页面比例," +
                "大于 1 时按页面像素;默认放在页面左上区域。",
            category = ToolCategory.CANVAS,
            defaultEnabled = true,
            defaultRequireConfirmation = false,
            parameters = listOf(
                ToolParameter("markdown", "string", "卡片正文(Markdown/LaTeX)", required = true),
                ToolParameter("x", "number", "水平位置:0..1 比例或 >1 像素,默认 0.08"),
                ToolParameter("y", "number", "垂直位置:0..1 比例或 >1 像素,默认 0.12"),
                ToolParameter("width", "number", "卡片宽度:0.15..0.95 比例或 >1 像素,默认 0.48"),
                ToolParameter("page_id", "string", "目标页 id,默认当前页"),
            ),
        )

        override fun describeAction(args: JsonObject, ctx: ToolContext): String {
            val page = ToolArgs.string(args, "page_id") ?: ctx.boundPage?.pageId
            return if (page != null) "在第 $page 页插入 Markdown 卡片" else "插入 Markdown 卡片"
        }

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolExecResult {
            val markdown = ToolArgs.string(args, "markdown")
                ?: return ToolExecResult(false, "缺少 markdown 参数")
            val workspace = ctx.workspace
                ?: return ToolExecResult(false, "当前未接入笔记本工作区")
            val page = targetPage(args, ctx)
                ?: return ToolExecResult(false, "未绑定页面,无法插入卡片")
            val dims = workspace.pageDimensions(page)
                ?: return ToolExecResult(false, "无法读取页面尺寸")
            val pageW = dims.widthPx.coerceAtLeast(1f)
            val pageH = dims.heightPx.coerceAtLeast(1f)

            val widthPx = resolveLength(ToolArgs.number(args, "width") ?: 0.48, pageW)
                .coerceIn(0f, (pageW * 0.95f).coerceAtLeast(minOf(40f, pageW)))
            val xPx = resolveCoordinate(ToolArgs.number(args, "x") ?: 0.08, pageW)
                .coerceIn(0f, (pageW - widthPx).coerceAtLeast(0f))
            val yPx = resolveCoordinate(ToolArgs.number(args, "y") ?: 0.12, pageH)
                .coerceIn(0f, (pageH - 40f).coerceAtLeast(0f))

            val id = workspace.insertMarkdownCard(page, markdown.take(MAX_MARKDOWN_CHARS), xPx, yPx, widthPx)
                ?: return ToolExecResult(false, "插入卡片失败(页面不可达)")
            return ToolExecResult(
                true,
                "已插入 Markdown 卡片(id=$id);位置 (${round(xPx)}, ${round(yPx)}) px,宽 ${round(widthPx)} px",
                "已插入卡片",
            )
        }

        private fun resolveCoordinate(value: Double, dimension: Float): Float =
            if (value > 1.0) value.toFloat() else (value * dimension).toFloat()

        private fun resolveLength(value: Double, dimension: Float): Float =
            if (value > 1.0) value.toFloat() else (value * dimension).toFloat()

        private fun round(v: Float): String = MathEvaluator.formatNumber(v.toDouble(), 1)
    }

    private class InsertFunctionGraphTool : AgentTool {
        override val spec = ToolSpec(
            id = INSERT_FUNCTION_GRAPH_TOOL_ID,
            defaultDisplayName = "插入函数图像",
            defaultDescription = "在当前页插入一张函数图像,一次包含全部 1-6 条函数曲线。" +
                "expressions 填一个或多个函数式(普通式或常用 LaTeX,如 x^2、sin(x)、\\frac{1}{x});" +
                "colors 可选,与曲线一一对应。x/y/width 与 insert_markdown 相同;只在用户要求画图或直观图像明显有帮助时使用。",
            category = ToolCategory.CANVAS,
            defaultEnabled = true,
            defaultRequireConfirmation = false,
            parameters = listOf(
                ToolParameter(
                    "expressions", "array", "1-6 个函数表达式;每个元素一个函数", required = true,
                    itemType = "string",
                ),
                ToolParameter(
                    "colors", "array", "可选,每曲线一个 #RRGGBB 颜色;缺省使用内置 6 色调色板",
                    itemType = "string",
                ),
                ToolParameter("x_min", "number", "x 轴最小值,默认 -10"),
                ToolParameter("x_max", "number", "x 轴最大值,默认 10"),
                ToolParameter("y_min", "number", "y 轴最小值,默认 -7"),
                ToolParameter("y_max", "number", "y 轴最大值,默认 7"),
                ToolParameter("x", "number", "水平位置:0..1 比例或 >1 像素,默认 0.08"),
                ToolParameter("y", "number", "垂直位置:0..1 比例或 >1 像素,默认 0.12"),
                ToolParameter("width", "number", "图像宽度:0.15..0.95 比例或 >1 像素,默认 0.55"),
                ToolParameter("page_id", "string", "目标页 id,默认当前页"),
            ),
        )

        override fun describeAction(args: JsonObject, ctx: ToolContext): String {
            val count = ToolArgs.stringList(args, "expressions").size
            val page = ToolArgs.string(args, "page_id") ?: ctx.boundPage?.pageId
            val suffix = if (count > 0) "($count 条曲线)" else ""
            return if (page != null) "在第 $page 页插入函数图像$suffix" else "插入函数图像$suffix"
        }

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolExecResult {
            val expressions = ToolArgs.stringList(args, "expressions")
            if (expressions.isEmpty()) return ToolExecResult(false, "缺少 expressions 参数(1-6 个函数表达式)")
            val colors = ToolArgs.colorList(args, "colors")
            val defaults = GraphViewport()
            val viewport = GraphViewport(
                xMin = ToolArgs.number(args, "x_min") ?: defaults.xMin,
                xMax = ToolArgs.number(args, "x_max") ?: defaults.xMax,
                yMin = ToolArgs.number(args, "y_min") ?: defaults.yMin,
                yMax = ToolArgs.number(args, "y_max") ?: defaults.yMax,
            )
            val spec = try {
                FunctionGraph.build(expressions, colors, viewport)
            } catch (e: FunctionGraphException) {
                return ToolExecResult(false, e.message ?: "函数表达式不合法")
            }
            val workspace = ctx.workspace
                ?: return ToolExecResult(false, "当前未接入笔记本工作区")
            val page = targetPage(args, ctx)
                ?: return ToolExecResult(false, "未绑定页面,无法插入函数图像")
            val dims = workspace.pageDimensions(page)
                ?: return ToolExecResult(false, "无法读取页面尺寸")
            val pageW = dims.widthPx.coerceAtLeast(1f)
            val pageH = dims.heightPx.coerceAtLeast(1f)

            val widthPx = resolveLength(ToolArgs.number(args, "width") ?: 0.55, pageW)
                .coerceIn(0f, (pageW * 0.95f).coerceAtLeast(minOf(40f, pageW)))
            val xPx = resolveCoordinate(ToolArgs.number(args, "x") ?: 0.08, pageW)
                .coerceIn(0f, (pageW - widthPx).coerceAtLeast(0f))
            val yPx = resolveCoordinate(ToolArgs.number(args, "y") ?: 0.12, pageH)
                .coerceIn(0f, (pageH - 40f).coerceAtLeast(0f))

            val id = workspace.insertFunctionGraph(page, spec, xPx, yPx, widthPx)
                ?: return ToolExecResult(false, "插入函数图像失败(页面不可达或渲染失败)")
            val vp = spec.viewport
            val summary = "x[${fmt(vp.xMin)}, ${fmt(vp.xMax)}] y[${fmt(vp.yMin)}, ${fmt(vp.yMax)}]"
            return ToolExecResult(
                true,
                "已插入函数图像(id=$id);${spec.curves.size} 条曲线;视口 $summary",
                "已插入函数图像",
            )
        }

        private fun resolveCoordinate(value: Double, dimension: Float): Float =
            if (value > 1.0) value.toFloat() else (value * dimension).toFloat()

        private fun resolveLength(value: Double, dimension: Float): Float =
            if (value > 1.0) value.toFloat() else (value * dimension).toFloat()

        private fun fmt(v: Double): String = MathEvaluator.formatNumber(v, 2)
    }

    private class ListPageElementsTool : AgentTool {
        override val spec = ToolSpec(
            id = "list_page_elements",
            defaultDisplayName = "查看页面元素",
            defaultDescription = "列出当前页(或指定页)的尺寸与所有元素,包含 id、类型、位置、尺寸与预览;" +
                "移动/删除元素前应先用它确认 element_id。",
            category = ToolCategory.CANVAS,
            defaultEnabled = true,
            defaultRequireConfirmation = false,
            parameters = listOf(
                ToolParameter("page_id", "string", "目标页 id,默认当前页"),
            ),
        )

        override fun describeAction(args: JsonObject, ctx: ToolContext): String {
            val page = ToolArgs.string(args, "page_id") ?: ctx.boundPage?.pageId
            return if (page != null) "查看第 $page 页的元素" else "查看当前页的元素"
        }

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolExecResult {
            val workspace = ctx.workspace
                ?: return ToolExecResult(false, "当前未接入笔记本工作区")
            val page = targetPage(args, ctx)
                ?: return ToolExecResult(false, "未绑定页面,无法查看元素")
            val dims = workspace.pageDimensions(page)
            val elements = runCatching { workspace.listElements(page) }.getOrDefault(emptyList())
            val output = buildString {
                append("页面 ").append(page.pageId)
                if (dims != null) append(" 尺寸 ").append(round(dims.widthPx)).append("×").append(round(dims.heightPx))
                append('\n')
                if (elements.isEmpty()) {
                    append("(当前页没有可操作的元素)")
                } else {
                    elements.forEachIndexed { i, e ->
                        append(i + 1).append(". ").append(e.kind).append(' ').append(e.id)
                        append(" | 位置 (").append(round(e.x)).append(", ").append(round(e.y)).append(")")
                        append(" | 尺寸 ").append(round(e.width)).append("×").append(round(e.height))
                        append(" | 比例 (").append(round(e.xRatio, 3)).append(", ")
                            .append(round(e.yRatio, 3)).append(")")
                        if (e.preview.isNotBlank()) {
                            append(" | ").append(e.preview.replace('\n', ' ').take(80))
                        }
                        append('\n')
                    }
                }
            }.trim()
            return ToolExecResult(true, output, "${elements.size} 个元素")
        }

        private fun round(v: Float, digits: Int = 1): String =
            MathEvaluator.formatNumber(v.toDouble(), digits)
    }

    private class ModifyPageElementTool : AgentTool {
        override val spec = ToolSpec(
            id = "modify_page_element",
            defaultDisplayName = "移动或删除页面元素",
            defaultDescription = "移动或删除页面上的卡片/图片/媒体/笔迹。移动可给绝对坐标(x/y)或相对偏移(dx/dy)。" +
                "这是高危操作,会先请求用户确认。",
            category = ToolCategory.CANVAS,
            defaultEnabled = true,
            defaultRequireConfirmation = true,
            parameters = listOf(
                ToolParameter(
                    "action", "string", "操作类型:move 或 delete", required = true,
                    enumValues = listOf("move", "delete"),
                ),
                ToolParameter("element_id", "string", "元素 id(先用 list_page_elements 查询)", required = true),
                ToolParameter(
                    "element_kind", "string", "元素类型 card/image/media/stroke",
                    enumValues = listOf("card", "image", "media", "stroke"),
                ),
                ToolParameter("page_id", "string", "元素所在页,默认当前页"),
                ToolParameter("x", "number", "目标水平位置:0..1 比例或 >1 像素"),
                ToolParameter("y", "number", "目标垂直位置:0..1 比例或 >1 像素"),
                ToolParameter("dx", "number", "水平偏移(像素)"),
                ToolParameter("dy", "number", "垂直偏移(像素)"),
            ),
        )

        override fun describeAction(args: JsonObject, ctx: ToolContext): String {
            val action = (ToolArgs.string(args, "action") ?: "move").lowercase()
            val elementId = ToolArgs.string(args, "element_id") ?: "?"
            return when {
                action == "delete" -> "删除页面元素 $elementId"
                ToolArgs.number(args, "dx") != null || ToolArgs.number(args, "dy") != null -> {
                    val dx = ToolArgs.number(args, "dx") ?: 0.0
                    val dy = ToolArgs.number(args, "dy") ?: 0.0
                    "移动页面元素 $elementId 偏移 (${numText(dx)}, ${numText(dy)}) px"
                }
                ToolArgs.number(args, "x") != null || ToolArgs.number(args, "y") != null -> {
                    val x = ToolArgs.number(args, "x")
                    val y = ToolArgs.number(args, "y")
                    "移动页面元素 $elementId 到 (${x?.let { numText(it) } ?: "-"}, ${y?.let { numText(it) } ?: "-"})"
                }
                else -> "移动页面元素 $elementId"
            }
        }

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolExecResult {
            val action = (ToolArgs.string(args, "action") ?: "move").lowercase()
            if (action != "move" && action != "delete") {
                return ToolExecResult(false, "action 只能是 move 或 delete")
            }
            val elementId = ToolArgs.string(args, "element_id")
                ?: return ToolExecResult(false, "缺少 element_id 参数")
            val workspace = ctx.workspace
                ?: return ToolExecResult(false, "当前未接入笔记本工作区")
            val page = targetPage(args, ctx)
                ?: return ToolExecResult(false, "未绑定页面,无法修改元素")
            val kind = ToolArgs.string(args, "element_kind")

            if (action == "delete") {
                val ok = workspace.deleteElement(page, elementId, kind)
                return if (ok) {
                    ToolExecResult(true, "已删除页面元素 $elementId", "已删除元素")
                } else {
                    ToolExecResult(false, "删除元素失败:元素不存在或不可删除")
                }
            }

            val xArg = ToolArgs.number(args, "x")
            val yArg = ToolArgs.number(args, "y")
            val dx = ToolArgs.number(args, "dx")?.toFloat()
            val dy = ToolArgs.number(args, "dy")?.toFloat()
            if (xArg == null && yArg == null && dx == null && dy == null) {
                return ToolExecResult(false, "移动元素需要提供 x/y 目标坐标或 dx/dy 偏移")
            }
            val dims = if (xArg != null || yArg != null) {
                workspace.pageDimensions(page)
                    ?: return ToolExecResult(false, "无法读取页面尺寸,不能按比例移动")
            } else {
                null
            }
            val targetX = xArg?.let { v ->
                val d = dims ?: return@let null
                (if (v > 1.0) v.toFloat() else (v * d.widthPx).toFloat()).coerceIn(0f, d.widthPx)
            }
            val targetY = yArg?.let { v ->
                val d = dims ?: return@let null
                (if (v > 1.0) v.toFloat() else (v * d.heightPx).toFloat()).coerceIn(0f, d.heightPx)
            }
            val ok = workspace.moveElement(page, elementId, kind, targetX, targetY, dx, dy)
            return if (ok) {
                ToolExecResult(true, "已移动页面元素 $elementId", "已移动元素")
            } else {
                ToolExecResult(false, "移动元素失败:元素不存在或不可移动")
            }
        }

        private fun numText(v: Double): String = MathEvaluator.formatNumber(v, 2)
    }

    // ==================== TIMESTAMP(逐次确认授权) ====================

    private class GetElementTimestampsTool : AgentTool {
        override val spec = ToolSpec(
            id = ELEMENT_TIMESTAMPS_TOOL_ID,
            defaultDisplayName = "读取页面元素时序",
            defaultDescription = "读取当前页指定区域内的元素时间信息。调用前会先向用户申请授权,只有用户同意后才执行。" +
                "坐标为页面原始像素:x1/y1 必填,x2/y2 需同时提供(都不给表示点选),两顶点顺序任意。" +
                "返回按顶层到最底层的 id、类型、创建时间、笔迹时长与是否已擦除,不含笔记原文与资产路径。" +
                "时间只是弱证据,不得据此推断思考耗时、熟练度或心理状态;叠层多时用 offset/limit 分页遍历。",
            category = ToolCategory.CANVAS,
            builtin = true,
            defaultRequireConfirmation = true,
            recordOutputInTrace = false,
            parameters = listOf(
                ToolParameter("x1", "number", "矩形一角 X(页面原始像素)", required = true),
                ToolParameter("y1", "number", "矩形一角 Y(页面原始像素)", required = true),
                ToolParameter("x2", "number", "对角点 X(与 y2 同时提供;都不给表示点选)"),
                ToolParameter("y2", "number", "对角点 Y(与 x2 同时提供;都不给表示点选)"),
                ToolParameter("include_erased", "boolean", "是否包含已擦除元素,默认 false"),
                ToolParameter("offset", "integer", "跳过条数,默认 0"),
                ToolParameter("limit", "integer", "返回上限 1-$TIMESTAMP_PAGE_MAX,默认 $TIMESTAMP_PAGE_DEFAULT"),
            ),
        )

        override fun describeAction(args: JsonObject, ctx: ToolContext): String =
            "读取页面 ${ctx.boundPage?.pageId ?: "?"} 的元素时序"

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolExecResult {
            val page = ctx.elementTimestampsPage
                ?: return ToolExecResult(
                    false,
                    "未获得用户本次读取授权,拒绝读取页面时间数据。" +
                        "不要重复调用;请基于已有内容回答,或说明需要用户同意后才能读取时序。",
                )
            val workspace = ctx.workspace
                ?: return ToolExecResult(false, "当前未接入笔记本工作区")

            // 参数严格校验:key 是否存在决定「点选/矩形/可选字段」,已提供的值必须解析成功;
            // 不复用 ToolArgs 的宽松/截断语义(那是给其他旧工具的兼容行为)。
            if (!provided(args, "x1") || !provided(args, "y1")) {
                return ToolExecResult(false, "缺少 x1/y1 参数(页面原始像素坐标)")
            }
            val x1 = numberArg(args, "x1")
                ?: return ToolExecResult(false, "x1 必须是有限数值")
            val y1 = numberArg(args, "y1")
                ?: return ToolExecResult(false, "y1 必须是有限数值")
            val hasX2 = provided(args, "x2")
            val hasY2 = provided(args, "y2")
            if (hasX2 != hasY2) {
                return ToolExecResult(false, "x2 与 y2 必须同时提供,或都不提供表示点选")
            }
            val x2 = if (hasX2) {
                numberArg(args, "x2") ?: return ToolExecResult(false, "x2 必须是有限数值")
            } else {
                null
            }
            val y2 = if (hasY2) {
                numberArg(args, "y2") ?: return ToolExecResult(false, "y2 必须是有限数值")
            } else {
                null
            }
            if (listOfNotNull(x1, y1, x2, y2).any { !it.isFinite() }) {
                return ToolExecResult(false, "坐标必须是有限数值")
            }

            val includeErased = if (!provided(args, "include_erased")) {
                false
            } else {
                boolArg(args, "include_erased")
                    ?: return ToolExecResult(false, "include_erased 只能是 true/false")
            }
            val offset = if (!provided(args, "offset")) {
                0
            } else {
                intArg(args, "offset")
                    ?: return ToolExecResult(false, "offset 必须是整数(不接受小数)")
            }
            if (offset < 0) return ToolExecResult(false, "offset 不能为负数")
            val limit = if (!provided(args, "limit")) {
                TIMESTAMP_PAGE_DEFAULT
            } else {
                (intArg(args, "limit")
                    ?: return ToolExecResult(false, "limit 必须是整数(不接受小数)"))
                    .coerceIn(1, TIMESTAMP_PAGE_MAX)
            }

            val pages = runCatching { workspace.listPages(page.notebookId) }.getOrDefault(emptyList())
            if (pages.isNotEmpty() && pages.none { it.pageId == page.pageId }) {
                return ToolExecResult(false, "页面 ${page.pageId} 不存在")
            }
            val dims = workspace.pageDimensions(page)
                ?: return ToolExecResult(false, "页面 ${page.pageId} 不可用")
            val rect = Rect4.fromDrag(x1.toFloat(), y1.toFloat(), (x2 ?: x1).toFloat(), (y2 ?: y1).toFloat())
            if (rect.l < 0f || rect.t < 0f || rect.r > dims.widthPx || rect.b > dims.heightPx) {
                return ToolExecResult(
                    false,
                    "选区超出页面范围(页面 ${num(dims.widthPx)}×${num(dims.heightPx)}):" +
                        "(${num(rect.l)}, ${num(rect.t)})-(${num(rect.r)}, ${num(rect.b)})",
                )
            }

            val all = workspace.elementTimestamps(page, rect, includeErased)
                ?: return ToolExecResult(false, "页面 ${page.pageId} 不可用(无法读取元素时序)")

            val total = all.size
            val from = offset.coerceAtMost(total)
            val items = if (from >= total) emptyList() else all.subList(from, minOf(total, from + limit))
            val header = buildString {
                append("页面 ").append(page.pageId).append(" · ")
                if (rect.width == 0f && rect.height == 0f) {
                    append("点 (").append(num(rect.l)).append(", ").append(num(rect.t)).append(")")
                } else {
                    append("选区 (").append(num(rect.l)).append(", ").append(num(rect.t)).append(")-(")
                        .append(num(rect.r)).append(", ").append(num(rect.b)).append(")")
                }
                append('\n')
                append("共 ").append(total).append(" 个元素(含已擦除=").append(includeErased)
                append("),offset=").append(offset).append(" limit=").append(limit)
                if (items.isEmpty()) {
                    append(",没有可返回的元素")
                } else {
                    append(",本次返回 ").append(from + 1).append('-').append(from + items.size)
                }
            }
            val body = items.mapIndexed { index, element ->
                buildString {
                    append(from + index + 1).append(". id=").append(element.id)
                    append(" kind=").append(element.kind)
                    append(" createdAtMs=").append(element.createdAtMs?.toString() ?: "unknown")
                    if (element.kind.equals("stroke", ignoreCase = true)) {
                        append(" durationMs=").append(element.durationMs?.toString() ?: "unknown")
                    }
                    append(" erased=").append(element.erased)
                }
            }
            val nextOffset = if (from + items.size < total) from + items.size else null
            val output = buildString {
                append(header)
                if (body.isNotEmpty()) append('\n').append(body.joinToString("\n"))
                if (nextOffset != null) {
                    append("\nnextOffset=").append(nextOffset)
                        .append("(再次调用并传 offset=").append(nextOffset).append(" 继续)")
                }
                append("\n提示:时序只是弱证据,结论以答题/笔记/知识点的正确性为主要依据;")
                append("不得据此推断思考耗时、熟练度或心理状态,数据不足时明确说明。")
            }
            return ToolExecResult(true, output, "${items.size} 个元素时序")
        }

        private fun num(v: Float): String = MathEvaluator.formatNumber(v.toDouble(), 1)

        /** 模型是否显式提供了该字段(含 null;null 会在解析阶段被拒绝) */
        private fun provided(args: JsonObject, name: String): Boolean = args.containsKey(name)

        /** 严格数值:只接受 JSON number 或数字字符串;object/array/boolean/null 均返回 null */
        private fun numberArg(args: JsonObject, name: String): Double? {
            val primitive = args[name] as? JsonPrimitive ?: return null
            return primitive.content.toDoubleOrNull()
        }

        /** 严格整数:2、2.0、"2" 均可;1.5/NaN/越界/非数值类型返回 null(不截断) */
        private fun intArg(args: JsonObject, name: String): Int? {
            val value = numberArg(args, name) ?: return null
            if (!value.isFinite() || value != Math.floor(value)) return null
            if (value < Int.MIN_VALUE || value > Int.MAX_VALUE) return null
            return value.toInt()
        }

        /** 严格布尔:只接受 true/false(JSON 布尔或字符串形式) */
        private fun boolArg(args: JsonObject, name: String): Boolean? =
            when ((args[name] as? JsonPrimitive)?.content) {
                "true" -> true
                "false" -> false
                else -> null
            }
    }

    // ==================== SKILL ====================

    private class ReadSkillTool : AgentTool {
        override val spec = ToolSpec(
            id = "read_skill",
            defaultDisplayName = "阅读技能",
            defaultDescription = "读取一个已启用技能的完整内容。先看系统提示里的技能目录,需要时再调用本工具。",
            category = ToolCategory.SKILL,
            defaultEnabled = true,
            defaultRequireConfirmation = false,
            parameters = listOf(
                ToolParameter("skill_id", "string", "技能 id(也可传技能名称)", required = true),
            ),
        )

        override fun describeAction(args: JsonObject, ctx: ToolContext): String =
            "阅读技能 ${ToolArgs.string(args, "skill_id") ?: "?"}"

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolExecResult {
            val id = ToolArgs.string(args, "skill_id")
                ?: return ToolExecResult(false, "缺少 skill_id 参数")
            val skill = ctx.skillStore.findEnabledSkill(id)
            if (skill == null) {
                val all = ctx.skillStore.listSkills()
                val disabled = all.firstOrNull { it.id.equals(id, true) || it.name == id }
                return if (disabled != null) {
                    ToolExecResult(false, "技能「${disabled.name}」已被关闭,不能使用")
                } else {
                    ToolExecResult(
                        false,
                        "未找到技能 $id;可用技能:${all.filter { it.enabled }.joinToString(", ") { it.id }}",
                    )
                }
            }
            val output = buildString {
                append("# ").append(skill.name).append('\n')
                if (skill.summary.isNotBlank()) append(skill.summary).append('\n')
                append('\n').append(skill.content.trim())
            }
            return ToolExecResult(true, output, "已读取技能:${skill.name}")
        }
    }

    private class CreateSkillTool : AgentTool {
        override val spec = ToolSpec(
            id = "create_skill",
            defaultDisplayName = "新增技能",
            defaultDescription = "把一套可复用的方法固化为新技能,后续可按需 read_skill 调用。" +
                "需要 name、summary 与完整 content。",
            category = ToolCategory.SKILL,
            defaultEnabled = true,
            defaultRequireConfirmation = false,
            parameters = listOf(
                ToolParameter("name", "string", "技能名称", required = true),
                ToolParameter("summary", "string", "一句话说明适用场景", required = true),
                ToolParameter("content", "string", "技能正文(Markdown,可含公式规范)", required = true),
                ToolParameter("id", "string", "技能 id,默认按名称生成安全 slug"),
                ToolParameter("enabled", "boolean", "是否启用,默认 true"),
            ),
        )

        override fun describeAction(args: JsonObject, ctx: ToolContext): String =
            "新增技能「${ToolArgs.string(args, "name") ?: "未命名"}」"

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolExecResult {
            val name = ToolArgs.string(args, "name")
                ?: return ToolExecResult(false, "缺少 name 参数")
            val summary = ToolArgs.string(args, "summary")
                ?: return ToolExecResult(false, "缺少 summary 参数")
            val content = ToolArgs.string(args, "content")
                ?: return ToolExecResult(false, "缺少 content 参数")
            val enabled = ToolArgs.bool(args, "enabled") ?: true
            val existing = ctx.skillStore.listSkills()
            val requested = ToolArgs.string(args, "id")
            val builtinIds = BuiltinSkills.all.map { it.id }.toSet()
            if (slugify(name) in builtinIds) {
                return ToolExecResult(
                    false,
                    "「$name」与内置技能冲突,内置技能内容只读;请换一个名称或按内置技能规范作答。",
                )
            }
            val id = if (requested != null) {
                sanitizeId(requested)
            } else {
                uniqueId(slugify(name), existing.map { it.id }.toSet())
            }
            if (id.isBlank()) return ToolExecResult(false, "无法生成合法的技能 id")
            if (id in builtinIds) {
                return ToolExecResult(
                    false,
                    "「$id」是应用内置技能,内容只读,不能覆盖;请换一个名称/id,或按内置技能规范作答。",
                )
            }
            val skill = ctx.skillStore.createOrUpdateSkill(id, name, summary, content, enabled)
            val action = if (existing.any { it.id == skill.id }) "已更新" else "已新增"
            return ToolExecResult(
                true,
                "${action}技能「${skill.name}」(id=${skill.id},${if (skill.enabled) "已启用" else "已关闭"})",
                "${action}技能:${skill.name}",
            )
        }

        private fun sanitizeId(raw: String): String =
            raw.trim().lowercase().map { c ->
                if (c in 'a'..'z' || c in '0'..'9' || c == '_') c else '_'
            }.joinToString("").trim('_')

        private fun slugify(name: String): String {
            val base = sanitizeId(name)
            return when {
                base.isBlank() -> "skill"
                base.first().isDigit() -> "skill_$base"
                else -> base
            }
        }

        private fun uniqueId(base: String, used: Set<String>): String {
            if (base !in used) return base
            var n = 2
            while ("${base}_$n" in used) n++
            return "${base}_$n"
        }
    }

    // ==================== helpers ====================

    private const val MAX_MARKDOWN_CHARS = 20_000

    private fun targetPage(args: JsonObject, ctx: ToolContext): PageRef? {
        val notebookId = ctx.boundPage?.notebookId ?: return null
        val pageId = ToolArgs.string(args, "page_id") ?: ctx.boundPage?.pageId ?: return null
        return PageRef(notebookId, pageId)
    }
}
