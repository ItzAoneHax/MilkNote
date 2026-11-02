package com.milknote.agent

/**
 * 意图注册表(AGENT-BACKEND §6.1,六意图可扩展)+ PromptEngine(§6.2)。
 * 输出契约与 UI 渲染器(MarkdownLite)能力对齐:标题/列表/加粗/行内代码/$公式$;
 * 禁用表格与图片;不主动要求 #标签(A2.6 起,渲染器仍容错显示)。
 */
object Intents {

    enum class Id { EXPLAIN, SUMMARIZE, TRANSLATE, QUIZ, CONTINUE, LATEX }

    data class Spec(
        val id: Id,
        val label: String,
        val systemContract: String,          // 追加在基础前缀后的意图契约
        val userTemplate: String,            // "{{TEXT}}" 为用户补充文字
        val latex: Boolean = false,          // 贴回默认动作是公式卡
    )

    /** 基础 system 前缀(§6.1 六意图共用) */
    const val BASE_SYSTEM =
        "你在平板手写笔记 App 内工作;用户发来的是手写笔记截图,可能含公式/图示/中文。" +
            "语气简洁,面向大学生。严格 Markdown 子集:标题/列表/加粗/行内代码/\$公式\$," +
            "禁用表格与图片。"

    val all: List<Spec> = listOf(
        Spec(
            id = Id.EXPLAIN, label = "讲解",
            systemContract = "你是讲解老师。分步讲解思路,关键概念首次出现时**加粗**," +
                "公式用 \$…\$ 行内排版;不用表格,不要在结尾堆 #标签。",
            userTemplate = "请讲解这道题/这段笔记。{{TEXT}}",
        ),
        Spec(
            id = Id.SUMMARIZE, label = "归纳",
            systemContract = "你是笔记整理助手。输出结构化要点列表(短句),必要时用 #### 小节分组;" +
                "不编造图中没有的内容,不要在结尾堆 #标签。",
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
            systemContract = "你是公式转写助手。**只输出** \$\$…\$\$ 代码块(识别到的全部公式)," +
                "不要任何解释文字。",
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
}
