package com.milknote.app.ui.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        try {
            showDrawer()
            rule.onNodeWithContentDescription("附件").assertIsDisplayed().performClick()
            rule.onNodeWithText("附加页面").assertIsDisplayed()
            rule.onNodeWithText("当前页 · 第 1 页").assertIsDisplayed()
            rule.onNodeWithText("可附加当前页", substring = true).assertDoesNotExist()
            rule.onNodeWithText("点按附加", substring = true).assertDoesNotExist()
            rule.onNodeWithText("p0001", substring = true).assertDoesNotExist()
        } finally {
            ServiceLocator.agentController.closeDrawer()
            ServiceLocator.agentController.removeProfile(profileId)
        }
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
