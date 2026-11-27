package com.milknote.agent

/**
 * 内置学习技能:始终按此规范定义提供(只读投影,不回写;文件里的旧记录原样保留但不生效)。
 * 只读 —— 用户不能编辑、关闭、删除或覆盖;自定义方法请建为用户技能。
 */
object BuiltinSkills {

    val mathSolutionSteps = AgentSkill(
        id = "math_solution_steps",
        name = "分步解题规范",
        summary = "按已知/思路/推导/验算四段式讲解,公式严格用 $ 与 $$ 排版",
        content = """
            # 分步解题规范

            1. **复述题目**:用一行写出已知条件与所求,不要漏掉隐含条件。
            2. **分析思路**:说明为什么选择该解法,列出用到的关键公式(公式独占行用 $$...$$)。
            3. **分步推导**:每一步单独成行,行内公式用 $...$;多行推导用 aligned 环境;公式内中文用 \text{...}。
            4. **验算**:代入检验或换一种方法核对;复杂算式必须调用 calculate 工具验算,不要心算。
            5. **结论**:用 2-3 句中文总结答案与易错点。

            约束:
            - 不跳步,不省略关键变形。
            - 公式不要放进围栏块,也不要用行内反引号包裹。
            - 用户要求「写到页面上」时,可调用 insert_markdown 把完整过程插入当前页。
        """.trimIndent(),
        enabled = true,
        builtin = true,
    )

    val cornellNotes = AgentSkill(
        id = "cornell_notes",
        name = "康奈尔笔记整理",
        summary = "把内容整理为线索、核心要点、总结三区,可沉淀为页面卡片",
        content = """
            # 康奈尔笔记整理

            把内容整理成三区:

            - **线索栏**:3-6 个问题或关键词,短句,便于自测。
            - **笔记栏**:核心要点逐条列出,每条一行;公式用 $...$ 行内排版。
            - **总结栏**:3 句以内概括本页主题、方法与结论。

            约束:
            - 不编造原文没有的内容;不确定处标注「待确认」。
            - 需要沉淀到页面时,先调用 list_page_elements 了解当前版面,再用 insert_markdown 生成一张卡片。
            - 输出保持 Markdown 结构,不要使用 #标签。
        """.trimIndent(),
        enabled = true,
        builtin = true,
    )

    val formulaSheet = AgentSkill(
        id = "formula_sheet",
        name = "公式速查卡片",
        summary = "提炼公式、适用条件与符号说明,输出适合卡片展示的 KaTeX 表格",
        content = """
            # 公式速查卡片

            输出结构:

            1. **主题**:用一行说明本章/本题涉及的公式范围。
            2. **速查表**:三列表格——公式、适用条件、符号说明。公式用独占行 $$...$$ 或表格内的 $...$。
            3. **易错提示**:2-3 条,指出定义域、符号或适用范围的陷阱。

            格式要求:
            - 使用 KaTeX 标准语法;多行公式用 aligned / cases / pmatrix 等环境。
            - 公式内中文一律用 \text{...} 包裹。
            - 不要输出 LaTeX 源码围栏,也不要解释排版语法。
            - 需要数值示例时用 calculate 工具验算后再写入。
        """.trimIndent(),
        enabled = true,
        builtin = true,
    )

    val all: List<AgentSkill> = listOf(mathSolutionSteps, cornellNotes, formulaSheet)
}
