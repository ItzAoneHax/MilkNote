package com.milknote.app.ui.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.milknote.agent.PageRef
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.theme.MilkTheme
import com.milknote.notes.model.NoteCard
import com.milknote.notes.model.NotebookMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AgentDrawerTrustTest {
    @get:Rule
    val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    /** 覆盖 Markdown 表格 + 块级公式 + AI 生成文档卡的最小真实样本 */
    private val richDocument = """
        # 线性代数 · 第三章

        矩阵的特征值满足 ${'$'}A v = \lambda v${'$'}，下面只留结论。

        | 符号 | 说明 |
        | :--- | ---: |
        | 特征值 | λ |
        | 特征向量 | v |

        ${'$'}${'$'}
        A v = \lambda v
        ${'$'}${'$'}

        ```milkdoc title="线性代数讲义.md" type="markdown"
        ## 小结

        每个特征向量只对应一个特征值。
        ```
    """.trimIndent()

    private fun showDrawer(
        markdown: String = "",
        onMarkdownChange: (String) -> Unit = {},
        onSaveMarkdownCard: () -> Unit = {},
        onOpenSettings: (String) -> Unit = {},
        relatedNotebooks: List<NotebookMeta> = emptyList(),
        relatedCards: List<NoteCard> = emptyList(),
        pageLabel: String = "第 1 页",
    ) {
        ServiceLocator.agentController.openDrawer(PageRef("agent-test", "p0001"))
        rule.setContent {
            MilkTheme {
                Box(Modifier.width(480.dp).height(700.dp)) {
                    AgentDrawerHost(
                        onOpenSettings = onOpenSettings,
                        onSnip = {},
                        onExplainPage = {},
                        onSummarizeChapter = {},
                        pageLabel = pageLabel,
                        pageMarkdown = markdown,
                        onMarkdownChange = onMarkdownChange,
                        onSaveMarkdownCard = onSaveMarkdownCard,
                        relatedNotebooks = relatedNotebooks,
                        relatedCards = relatedCards,
                    )
                }
            }
        }
    }

    /** 测试环境没有 Writer,页序提供方为 null:临时注入单页笔记本供「笔记页面」二级面板使用 */
    private fun stubPageList() {
        ServiceLocator.agentSnipSource.pagesListProvider = { listOf(PageRef("agent-test", "p0001")) }
    }

    private fun clearPageList() {
        ServiceLocator.agentSnipSource.pagesListProvider = null
    }

    /** 配置一个可用视觉模型(临时落盘,用例结束移除) */
    private fun configureVisionModel(): String {
        val agent = ServiceLocator.agentController
        val title = "测试端点-${System.nanoTime()}"
        agent.addProfile(
            presetId = "agent-test-custom",
            title = title,
            baseUrl = "https://example.invalid/v1",
            apiKey = "",
        )
        val profile = agent.state.value.profiles.last { it.title == title }
        agent.addModel(profile.id, "demo-vision", declaredVision = true)
        // 已有历史配置时 addProfile 不会切换 active profile,测试显式切到新建项
        agent.setActiveProfile(profile.id)
        return profile.id
    }

    @Test
    fun blankMarkdown_neverWritesOrSavesExampleContent() {
        val changes = mutableListOf<String>()
        var saves = 0
        showDrawer(markdown = "", onMarkdownChange = { changes += it }, onSaveMarkdownCard = { saves++ })
        try {
            rule.onNodeWithText("Markdown").assertIsDisplayed().performClick()
            rule.onNodeWithText("源码").assertIsDisplayed().performClick()
            rule.onNodeWithText("预览").assertIsDisplayed().performClick()

            assertTrue("切换源码和预览不得写入示例：$changes", changes.isEmpty())
            rule.onNodeWithText("存卡片").assertIsDisplayed().assertIsNotEnabled()
            assertEquals(0, saves)
            rule.onNodeWithText("线性代数讲义.pdf", substring = true).assertDoesNotExist()
        } finally {
            ServiceLocator.agentController.closeDrawer()
        }
    }

    @Test
    fun setupGuide_hasOneStateAndOnePlainAction() {
        var openedPage = ""
        showDrawer(onOpenSettings = { openedPage = it })
        try {
            rule.onNodeWithText("AI").assertIsDisplayed().performClick()
            rule.onNodeWithText("尚未设置模型").assertIsDisplayed()
            rule.onNodeWithText("未配置视觉模型").assertDoesNotExist()
            rule.onNodeWithText("配置视觉模型后开始").assertDoesNotExist()
            rule.onNodeWithText("设置模型").assertIsDisplayed().performClick()
            assertEquals("agent", openedPage)
        } finally {
            ServiceLocator.agentController.closeDrawer()
        }
    }

    @Test
    fun setupGuide_hasNoInstructionalProse() {
        showDrawer()
        try {
            rule.onNodeWithText("AI").assertIsDisplayed().performClick()
            rule.onNodeWithText("尚未设置模型").assertIsDisplayed()
            rule.onNodeWithText("设置模型后，AI 教师即可讲解页面、归纳章节和回答提问。").assertDoesNotExist()
            rule.onNodeWithText("即可讲解", substring = true).assertDoesNotExist()
        } finally {
            ServiceLocator.agentController.closeDrawer()
        }
    }

    @Test
    fun linksPane_emptyStateHasNoInstructionalProse() {
        showDrawer()
        try {
            rule.onNodeWithText("关联").assertIsDisplayed().performClick()
            rule.onNodeWithText("暂无其他笔记本").assertIsDisplayed()
            rule.onNodeWithText("暂无卡片").assertIsDisplayed()
            rule.onNodeWithText("点击双开分屏对照", substring = true).assertDoesNotExist()
            rule.onNodeWithText("点击贴回当前页", substring = true).assertDoesNotExist()
            rule.onNodeWithText("书架中有其他笔记本时", substring = true).assertDoesNotExist()
            rule.onNodeWithText("框选或伴随笔记存为卡片后", substring = true).assertDoesNotExist()
        } finally {
            ServiceLocator.agentController.closeDrawer()
        }
    }

    @Test
    fun linksPane_populatedRowsHaveNoInstructionalProse() {
        showDrawer(
            relatedNotebooks = listOf(
                NotebookMeta(id = "nb-2", name = "线性代数讲义", createdAt = 0L, updatedAt = 0L),
            ),
            relatedCards = listOf(NoteCard(id = "c-1", title = "特征值", body = "核心公式")),
        )
        try {
            rule.onNodeWithText("关联").assertIsDisplayed().performClick()
            rule.onNodeWithText("线性代数讲义").assertIsDisplayed()
            rule.onNodeWithText("特征值").assertIsDisplayed()
            rule.onNodeWithText("点击双开分屏对照", substring = true).assertDoesNotExist()
            rule.onNodeWithText("点击贴回当前页", substring = true).assertDoesNotExist()
        } finally {
            ServiceLocator.agentController.closeDrawer()
        }
    }

    @Test
    fun configuredEmptyPane_showsTactileActionsWithPageLabel() {
        val profileId = configureVisionModel()
        try {
            showDrawer(pageLabel = "第 3 页")
            rule.onNodeWithText("框选题目").assertIsDisplayed()
            rule.onNodeWithText("讲解本页").assertIsDisplayed()
            rule.onNodeWithText("归纳本章").assertIsDisplayed()
            rule.onNodeWithText("第 3 页").assertIsDisplayed()
            rule.onNodeWithText("思考强度", substring = true).assertDoesNotExist()
        } finally {
            ServiceLocator.agentController.closeDrawer()
            ServiceLocator.agentController.removeProfile(profileId)
        }
    }

    @Test
    fun configuredModelPill_opensModelSheetWithoutProse() {
        val profileId = configureVisionModel()
        try {
            showDrawer()
            rule.onNodeWithText("demo-vision").assertIsDisplayed().performClick()
            rule.onNodeWithText("模型与思考").assertIsDisplayed()
            rule.onNodeWithText("思考强度").assertIsDisplayed()
            rule.onNodeWithText("管理模型").assertIsDisplayed()
            rule.onNodeWithText("仅显示已声明", substring = true).assertDoesNotExist()
            rule.onNodeWithText("档位映射因厂商而异", substring = true).assertDoesNotExist()
        } finally {
            ServiceLocator.agentController.closeDrawer()
            ServiceLocator.agentController.removeProfile(profileId)
        }
    }

    @Test
    fun attachmentPanel_hasNoInstructionalProse() {
        val profileId = configureVisionModel()
        stubPageList()
        try {
            showDrawer()
            rule.onNodeWithContentDescription("添加附件").assertIsDisplayed().performClick()
            // 一级菜单:四个真实入口,没有一句操作提示
            rule.onNodeWithText("系统图片").assertIsDisplayed()
            rule.onNodeWithText("导入文档").assertIsDisplayed()
            rule.onNodeWithText("笔记页面").assertIsDisplayed()
            rule.onNodeWithText("画布框选").assertIsDisplayed()
            rule.onNodeWithText("可附加当前页", substring = true).assertDoesNotExist()
            rule.onNodeWithText("点按附加", substring = true).assertDoesNotExist()

            // 二级 C:笔记页面范围面板
            rule.onNodeWithText("笔记页面").assertIsDisplayed().performClick()
            rule.onNodeWithText("当前页 · 第 1 页").assertIsDisplayed()
            rule.onNodeWithText("可附加当前页", substring = true).assertDoesNotExist()
            rule.onNodeWithText("点按附加", substring = true).assertDoesNotExist()
            rule.onNodeWithText("p0001", substring = true).assertDoesNotExist()
        } finally {
            clearPageList()
            ServiceLocator.agentController.closeDrawer()
            ServiceLocator.agentController.removeProfile(profileId)
        }
    }

    @Test
    fun headerActions_openSessionSwitcherWithScopeSearchAndMetrics() {
        val profileId = configureVisionModel()
        try {
            showDrawer()
            rule.onNodeWithContentDescription("历史会话").assertIsDisplayed().performClick()

            // 独立浮层:标题 + 范围分段 + 搜索 + 新会话
            rule.onNodeWithText("历史会话").assertIsDisplayed()
            rule.onNodeWithText("本页").assertIsDisplayed()
            rule.onNodeWithText("整本笔记").assertIsDisplayed()
            rule.onNodeWithText("搜索总结、对话或模型…").assertIsDisplayed()
            rule.onNodeWithText("新会话").assertIsDisplayed()

            // 会话卡直接给出上下文用量与平均输出速度
            rule.onAllNodesWithText("上下文", substring = true).onFirst().assertIsDisplayed()
            rule.onAllNodesWithText("速度", substring = true).onFirst().assertIsDisplayed()
            rule.onNodeWithText("本页开始提问后会自动记录", substring = true).assertDoesNotExist()
        } finally {
            ServiceLocator.agentController.closeDrawer()
            ServiceLocator.agentController.removeProfile(profileId)
        }
    }

    @Test
    fun headerActions_openPreviewHubWithCategoriesAndLocalFileEntry() {
        val profileId = configureVisionModel()
        try {
            showDrawer(markdown = "## 本页要点\n\n特征值只与矩阵有关。")
            rule.onNodeWithContentDescription("文件与文档预览").assertIsDisplayed().performClick()

            rule.onNodeWithText("文件与文档预览").assertIsDisplayed()
            rule.onNodeWithText("全部").assertIsDisplayed()
            rule.onNodeWithText("图片").assertIsDisplayed()
            rule.onNodeWithText("文档").assertIsDisplayed()
            // 顶栏页签「Markdown」+ 预览中心分类「Markdown」
            rule.onAllNodesWithText("Markdown").assertCountEquals(2)
            rule.onNodeWithText("打开本地文件预览").assertIsDisplayed()

            // 分类筛选真的生效:当前只有页内 Markdown,文档分类应为空
            rule.onNodeWithText("文档").assertIsDisplayed().performClick()
            rule.onNodeWithText("该分类暂无内容").assertIsDisplayed()
        } finally {
            ServiceLocator.agentController.closeDrawer()
            ServiceLocator.agentController.removeProfile(profileId)
        }
    }

    @Test
    fun markdownDocument_rendersTableAndOpensFloatingPreview() {
        var preview by mutableStateOf<AgentPreviewTarget?>(null)
        rule.setContent {
            MilkTheme {
                Box(Modifier.width(480.dp).height(700.dp)) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        MilkMarkdownView(
                            text = richDocument,
                            onPreviewDocument = { document -> preview = document.toPreviewTarget() },
                            onFormulaClick = { latex ->
                                preview = AgentPreviewTarget.Markdown(
                                    title = "公式预览",
                                    content = latex,
                                    docType = MarkdownLite.GeneratedDocType.LATEX,
                                    canSaveToNotes = false,
                                    canSaveCard = true,
                                )
                            },
                        )
                    }
                    preview?.let { target ->
                        AgentFloatingPreviewDialog(target = target, onDismiss = { preview = null })
                    }
                }
            }
        }

        // 表格内容真实渲染(表头 + 两行单元格)
        rule.onNodeWithText("符号").assertIsDisplayed()
        rule.onNodeWithText("特征值").assertIsDisplayed()
        rule.onNodeWithText("特征向量").assertIsDisplayed()

        // 生成文档卡:标题 + 悬浮预览动作
        rule.onNodeWithText("线性代数讲义.md").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("悬浮预览").performScrollTo().assertIsDisplayed().performClick()

        // 打开悬浮预览窗:渲染预览 / 源码 双模式
        rule.onNodeWithText("渲染预览").assertIsDisplayed()
        rule.onNodeWithText("源码").assertIsDisplayed()
    }

    @Test
    fun back_closesRightDrawerFirst() {
        showDrawer()
        try {
            rule.onNodeWithText("Markdown").assertIsDisplayed()
            rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
            rule.waitForIdle()

            assertFalse(ServiceLocator.agentController.state.value.drawerOpen)
        } finally {
            ServiceLocator.agentController.closeDrawer()
        }
    }
}
