package com.milknote.agent

/**
 * 意图注册表(AGENT-BACKEND §6.1,六意图可扩展)+ PromptEngine(§6.2)。
 * 输出契约与 UI 渲染器对齐:完整 Markdown(标题/列表/任务列表/表格/代码块/引用/链接)、
 * 完整 LaTeX(行内 $…$ 与独立 $$…$$)、可生成 milkdoc 文档块。
 */
object Intents {

    enum class Id { EXPLAIN, SUMMARIZE, TRANSLATE, QUIZ, CONTINUE, LATEX, PROFICIENCY }
    // PROFICIENCY 仅用于读取旧会话数据;不再出现在 [all] / UI / slash,发送时安全降级为 EXPLAIN

    data class Spec(
        val id: Id,
        val label: String,
        val systemContract: String,          // 追加在基础前缀后的意图契约
        val userTemplate: String,            // "{{TEXT}}" 为用户补充文字
        val latex: Boolean = false,          // 贴回默认动作是公式卡
    )

    /** 基础 system 前缀(§6.1 六意图共用;公式输出格式是硬约束) */
    const val BASE_SYSTEM =
        "你在平板手写笔记 App 内工作;用户发来的是手写笔记截图或文档,可能含公式/图示/中文。" +
            "语气简洁,面向大学生。\n" +
            "【数学公式输出规范】行内公式一律用 \$...\$;独立公式必须独占行,用 \$\$...\$\$ 包裹。" +
            "严禁使用 ```latex、```tex、```math 等围栏块包裹公式,严禁用行内反引号包裹公式" +
            "(仅当用户明确要求查看 LaTeX 源码时才可例外)。" +
            "多行公式使用 aligned / cases / pmatrix 等标准 KaTeX 环境,公式内中文用 \\text{...} 包裹。\n" +
            "【Markdown 排版】可用标题(#~####)、有序/无序列表、任务列表、表格、引用块、链接、加粗/斜体;" +
            "围栏块只用于真正的代码,数学公式不得放进围栏块。\n" +
            "【独立文档】仅当用户明确要求生成可独立保存/导出的文档(如 Markdown 文档、复习提纲、LaTeX 讲义)时," +
            "才把文档正文整体包进围栏块并在首行写 milkdoc 标记:\n" +
            "```milkdoc title=\"文档标题.md\" type=\"markdown\"\n" +
            "…文档正文…\n" +
            "```\n" +
            "type 取 markdown|text|latex;普通回答直接输出,不要使用该围栏。\n" +
            "【学习诊断边界】评估以答题、笔记与知识点本身的正确性、完整性为主要依据," +
            "不得仅凭停顿、涂改、擦除的有无或时长推断熟练度、掌握程度、水平与心理状态。" +
            "需要页面书写过程的时间信息时,可调用可用工具并按提示向用户申请;" +
            "用户拒绝或数据不足时明确说明,不得反复请求或编造证据;" +
            "即使用户自述「学得不好」,也只针对内容本身给建议。"

    val all: List<Spec> = listOf(
        Spec(
            id = Id.EXPLAIN, label = "讲解",
            systemContract = "你是讲解老师。分步讲解思路,关键概念首次出现时**加粗**," +
                "公式用 \$…\$ 行内排版、重点结论可用 \$\$…\$\$ 独立成行;对比/分支可用表格或引用块;不要在结尾堆 #标签。",
            userTemplate = "请讲解这道题/这段笔记。{{TEXT}}",
        ),
        Spec(
            id = Id.SUMMARIZE, label = "归纳",
            systemContract = "你是笔记整理助手。输出结构化要点列表(短句),必要时用 #### 小节分组," +
                "对照关系可用表格;不编造图中没有的内容,不要在结尾堆 #标签。",
            userTemplate = "请归纳这段笔记的要点。{{TEXT}}",
        ),
        Spec(
            id = Id.TRANSLATE, label = "翻译",
            systemContract = "你是翻译助手。中英对照输出:每行一个条目,格式「原文 — 译文」列表;" +
                "公式保持原样不翻译。",
            userTemplate = "请翻译这段内容(中英对照)。{{TEXT}}",
        ),
        Spec(
            id = Id.QUIZ, label = "出题",
            systemContract = "你是出题老师。基于内容出 3 道递进题:1 道选择、1 道填空、1 道解答;" +
                "题目在前,**答案统一放在末尾**一节(便于先自测)。",
            userTemplate = "请基于这段笔记出 3 道递进练习题。{{TEXT}}",
        ),
        Spec(
            id = Id.CONTINUE, label = "续写",
            systemContract = "你是笔记续写助手。按原笔记的条目风格续写大纲(短句、①②③编号)," +
                "只延伸已有脉络,不编造已写内容。",
            userTemplate = "请按笔记风格续写大纲。{{TEXT}}",
        ),
        Spec(
            id = Id.LATEX, label = "转LaTeX",
            systemContract = "你是公式转写助手。**只输出**识别到的全部公式:每个公式独占行," +
                "用 \$\$...\$\$ 包裹成可渲染的公式块;禁止使用 ```latex、```tex、```math 围栏," +
                "禁止用行内反引号包裹公式,禁止输出解释文字、标题、列表或任何非公式内容。",
            userTemplate = "请把手写公式转成 LaTeX。{{TEXT}}",
            latex = true,
        ),
    )

    fun byId(id: Id): Spec = all.firstOrNull { it.id == id } ?: all.first()

    fun byLabel(label: String): Spec? = all.firstOrNull { it.label == label }
}

/**
 * 提示词组装(§6.2):
 * - 图片放最后一条 user 消息(图先文后);历史只带文字;
 * - 历史窗口截断保首条 user(题目本体不丢)。
 */
object PromptEngine {

    const val HISTORY_WINDOW = 8

    fun systemPrompt(spec: Intents.Spec): String =
        Intents.BASE_SYSTEM + "\n\n" + spec.systemContract

    /**
     * 带技能目录与工具原则的完整 system 提示词(渐进式披露):
     * 只列 id / name / summary,正文由模型按需调用 read_skill 获取。
     * 需授权的工具同样可见;是否调用由模型判断,执行前由控制器逐次请求用户同意。
     */
    fun systemPrompt(
        spec: Intents.Spec,
        enabledSkills: List<AgentSkill>,
        enabledTools: List<WireToolDef>,
    ): String {
        val sb = StringBuilder(systemPrompt(spec))
        val skills = enabledSkills.filter { it.enabled }
        val toolNames = enabledTools.map { it.name }.toSet()

        if (skills.isNotEmpty()) {
            sb.append("\n\n【可用技能目录】")
            if ("read_skill" in toolNames) {
                sb.append("需要时先调用 read_skill(skill_id) 读取完整步骤,再按技能规范作答;不要凭空假设技能内容。\n")
            } else {
                sb.append("以下技能可参考:\n")
            }
            skills.forEach { s ->
                sb.append("- ").append(s.id).append(" · ").append(s.name)
                if (s.summary.isNotBlank()) sb.append(":").append(s.summary.trim())
                sb.append('\n')
            }
        }

        if (enabledTools.isNotEmpty()) {
            sb.append("\n\n【工具使用原则】")
            sb.append("可用工具:").append(enabledTools.joinToString("、") { it.name }).append("。\n")
            sb.append("- 只在确实需要时调用工具,不要为简单问题滥用。\n")
            if ("calculate" in toolNames) {
                sb.append("- 复杂算式必须用 calculate 验算,不要心算;多步计算可复用变量。\n")
            }
            if ("web_search" in toolNames) {
                sb.append("- 只有需要外部资料、最新信息或你无法确定的事实时才用 web_search,并在回答中标注来源链接。\n")
            }
            if (BuiltinTools.ELEMENT_TIMESTAMPS_TOOL_ID in toolNames) {
                sb.append("- 只有在判改卷子、学习诊断等确需页面书写过程信息时才用 get_element_timestamps;")
                sb.append("调用会先请用户授权。用户拒绝后不要重复调用,继续基于已有内容回答;")
                sb.append("时序只是弱证据,结论以内容正确性为主,不得据此推断熟练度或心理状态。\n")
            }
            if (BuiltinTools.INSERT_FUNCTION_GRAPH_TOOL_ID in toolNames) {
                sb.append("- 用户要求画函数图像,或直观图像明显有助于解释时,用 insert_function_graph ")
                sb.append("一次画 1-6 条曲线并插入当前页;不要用 Markdown/文字描述冒充函数图像。\n")
            }
            if (toolNames.any { it.startsWith("add_page") || it.startsWith("delete_page") || it.startsWith("insert_") || it.startsWith("list_page") || it.startsWith("modify_page") }) {
                sb.append("- 页面/画布操作默认作用于当前页;只有用户明确提到其他页时才传 page_id。\n")
            }
            if ("delete_page" in toolNames || "modify_page_element" in toolNames) {
                sb.append("- 删除页面和修改页面元素会请求用户确认;用户拒绝后不要重复调用同一操作,改为说明或给替代方案。\n")
            }
            sb.append("- 工具报错时简要说明原因并继续作答,不要编造工具结果。\n")
            sb.append("- 最终回答仍按 Markdown/LaTeX 规范输出,不要向用户复述工具 JSON。")
        }
        return sb.toString()
    }

    fun userText(spec: Intents.Spec, extra: String): String {
        // 图先文后:图片部分由请求组装排前([图N] 标注见 assembleUserContent),文字在后
        val extraText = extra.trim()
        return spec.userTemplate.replace("{{TEXT}}", if (extraText.isEmpty()) "" else "\n用户补充:$extraText")
    }

    /** 历史窗口:近 N 条;超窗从最旧截断,但首条 user 永远保留 */
    fun windowedHistory(messages: List<Pair<String, String>>): List<WireMessage> {
        if (messages.isEmpty()) return emptyList()
        val w = HISTORY_WINDOW
        if (messages.size <= w) return messages.map { WireMessage(it.first, it.second) }
        val first = messages.first()
        val rest = messages.takeLast(w - 1)
        return (listOf(first) + rest.filter { it != first }).map { WireMessage(it.first, it.second) }
    }

    /** 图先文后(§3.5):content = [图1][图2]… + 用户文字 */
    fun assembleUserContent(newUserText: String, imageCount: Int): String {
        val sb = StringBuilder()
        for (i in 1..imageCount) sb.append("[图$i] ")
        if (newUserText.isNotBlank()) sb.append(newUserText)
        return sb.toString().trim()
    }

    /**
     * 富内容组装(保持 `[图N]` 前缀不变,兼容既有解析/断言):
     * - 图序标注 `[图1] [图2] …` 永远在最前;
     * - 有命名图时补一句「图片对应」映射(`[图1]=@选区`),让模型知道 @ 提及指哪张图;
     * - 文档正文整段附在用户文字之后,以 [附件文档 …] … [/附件文档] 包裹。
     */
    fun assembleRichUserContent(
        newUserText: String,
        imageLabels: List<String>,
        documentBlocks: List<Pair<String, String>>,
    ): String {
        val sb = StringBuilder()
        for (i in imageLabels.indices) sb.append("[图${i + 1}] ")
        val named = imageLabels.mapIndexedNotNull { i, label ->
            if (label.isBlank()) null else "[图${i + 1}]=$label"
        }
        if (named.isNotEmpty()) sb.append("(图片对应:").append(named.joinToString(", ")).append(") ")
        if (newUserText.isNotBlank()) sb.append(newUserText)
        for ((header, body) in documentBlocks) {
            if (body.isBlank()) continue
            sb.append("\n\n").append(header).append("\n").append(body.trim()).append("\n[/附件文档]")
        }
        return sb.toString().trim()
    }
}
