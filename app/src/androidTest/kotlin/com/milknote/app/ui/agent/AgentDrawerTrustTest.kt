package com.milknote.app.ui.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.milknote.agent.PageRef
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.theme.MilkTheme
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
                        pageMarkdown = markdown,
                        onMarkdownChange = onMarkdownChange,
                        onSaveMarkdownCard = onSaveMarkdownCard,
                    )
                }
            }
        }
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
