package com.milknote.app.ui.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.milknote.app.ui.theme.MilkTheme
import org.junit.Rule
import org.junit.Test

class SettingsRefinementTest {
    @get:Rule
    val rule = createComposeRule()

    private fun showSettings(page: String) {
        rule.setContent {
            MilkTheme {
                SettingsScreen(onBack = {}, initialPage = page)
            }
        }
    }

    @Test
    fun agentSettings_usesPlainProviderEntryAndTeacherGroup() {
        showSettings("agent")

        rule.onNodeWithText("AI 教师").assertIsDisplayed()
        rule.onNodeWithText("添加模型服务").assertIsDisplayed()
        rule.onNodeWithText("新增 Provider").assertDoesNotExist()
    }

    @Test
    fun privacy_keepsOneLocalStorageBadge() {
        showSettings("agent")

        rule.onAllNodesWithText("仅本机", substring = true).assertCountEquals(1)
    }

    @Test
    fun appearance_showsPaperColorNamesVisibly() {
        showSettings("appearance")

        rule.onNodeWithText("奶白").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("浅蓝").assertIsDisplayed()
        rule.onNodeWithText("浅紫").assertIsDisplayed()
        rule.onNodeWithText("浅绿").assertIsDisplayed()
    }

    @Test
    fun agentBehavior_hasSummaryAndAttachmentBlocks() {
        showSettings("agent")

        rule.onNodeWithText("会话总结").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("自动生成会话总结").assertIsDisplayed()
        rule.onNodeWithText("继承对话").assertIsDisplayed()
        rule.onNodeWithText("指定模型").assertIsDisplayed()

        rule.onNodeWithText("附件与引用").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("上传附件时默认 @ 引用").assertIsDisplayed()
    }
}
