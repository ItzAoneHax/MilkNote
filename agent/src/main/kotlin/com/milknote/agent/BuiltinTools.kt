package com.milknote.agent

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

/**
 * 9 个内置工具:
 * - SEARCH  web_search
 * - COMPUTE calculate
 * - PAGE    add_page / delete_page(确认)
 * - CANVAS  insert_markdown / list_page_elements / modify_page_element(确认)
 * - SKILL   read_skill / create_skill
 *
 * 只依赖 Ports.kt 的纯 Kotlin 端口,装配在 app 层。
 */
object BuiltinTools {

    fun createDefaultRegistry(): ToolRegistry = ToolRegistry().apply {
        register(WebSearchTool())
        register(CalculateTool())
        register(AddPageTool())
        register(DeletePageTool())
        register(InsertMarkdownTool())
        register(ListPageElementsTool())
        register(ModifyPageElementTool())
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
            defaultDescription = "精确计算数学表达式。支持变量、常用函数、阶乘与多语句(分号分隔)。" +
                "复杂算式请用它验算,不要心算。",
            category = ToolCategory.COMPUTE,
            defaultEnabled = true,
            defaultRequireConfirmation = false,
            parameters = listOf(
                ToolParameter("expression", "string", "数学表达式,例如 sqrt(3^2+4^2) 或 a=3; a*2", required = true),
                ToolParameter("precision", "integer", "保留的小数位数,默认 10,范围 0-15", required = false),
            ),
        )

        override fun describeAction(args: JsonObject, ctx: ToolContext): String =
            "计算 ${ToolArgs.string(args, "expression") ?: "表达式"}"

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolExecResult {
            val expression = ToolArgs.string(args, "expression")
                ?: return ToolExecResult(false, "缺少 expression 参数")
            val precision = ToolArgs.int(args, "precision") ?: 10
            return try {
                val r = MathEvaluator.evaluate(expression, precision)
                val output = buildString {
                    append("结果: ").append(r.formatted)
                    if (r.variables.isNotEmpty()) {
                        append('\n').append(
                            r.variables.entries.joinToString(", ") { (k, v) ->
                                "$k = ${MathEvaluator.formatNumber(v, precision)}"
                            },
                        )
                    }
                }
                ToolExecResult(true, output, r.formatted)
            } catch (e: MathEvaluator.EvaluationException) {
                ToolExecResult(false, e.message ?: "表达式无法计算", "计算失败")
            }
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
            val id = if (requested != null) {
                sanitizeId(requested)
            } else {
                uniqueId(slugify(name), existing.map { it.id }.toSet())
            }
            if (id.isBlank()) return ToolExecResult(false, "无法生成合法的技能 id")
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
