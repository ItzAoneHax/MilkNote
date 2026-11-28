package com.milknote.app.ui.agent

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.milknote.agent.AgentMessage
import com.milknote.agent.AgentSession
import com.milknote.agent.PageRef
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.theme.MilkTheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 消息右键/长按菜单的真实 Compose 交互:只验证 UI 与剪贴板,不触发网络 */
class AgentMessageMenuTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `AI 消息长按可复制原文并查看源文本`() {
        val assistantText = "**原文** \$x^2\$"
        val rig = prepareMessages(assistantText = assistantText)
        try {
            rule.onNodeWithTag("agent-assistant-message-${rig.assistantId}")
                .assertIsDisplayed()
                .performTouchInput { longClick() }
            rule.onNodeWithTag("message-action-copy").assertIsDisplayed()
            rule.onNodeWithTag("message-action-source").assertIsDisplayed()
            rule.onNodeWithTag("message-action-copy").performClick()
            val clipboard = rule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(assistantText, clipboard.primaryClip?.getItemAt(0)?.text?.toString())

            rule.onNodeWithTag("agent-assistant-message-${rig.assistantId}").performTouchInput { longClick() }
            rule.onNodeWithTag("message-action-source").performClick()
            rule.onNodeWithText("源文本").assertIsDisplayed()
            rule.onNodeWithText(assistantText, useUnmergedTree = true).assertIsDisplayed()
            rule.onNodeWithContentDescription("关闭源文本").performClick()
        } finally {
            cleanup(rig)
        }
    }

    @Test
    fun `用户消息长按可修改并二次确认撤回`() {
        val userText = "请解释 \$x^2\$"
        val rig = prepareMessages(userText = userText)
        try {
            rule.onNodeWithTag("agent-user-message-${rig.userId}")
                .assertIsDisplayed()
                .performTouchInput { longClick() }
            rule.onNodeWithTag("message-action-copy").assertIsDisplayed()
            rule.onNodeWithTag("message-action-edit").assertIsDisplayed()
            rule.onNodeWithTag("message-action-retract").assertIsDisplayed()

            rule.onNodeWithTag("message-action-edit").performClick()
            rule.onNodeWithText("修改消息").assertIsDisplayed()
            rule.onNodeWithTag("message-edit-field").assertIsDisplayed()
            assertTrue(
                "修改框应能读取原文",
                rule.onAllNodesWithText(userText, useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty(),
            )
            rule.onNodeWithText("取消").performClick()

            rule.onNodeWithTag("agent-user-message-${rig.userId}").performTouchInput { longClick() }
            rule.onNodeWithTag("message-action-retract").performClick()
            rule.onNodeWithText("撤回这条消息？").assertIsDisplayed()
            rule.onNodeWithText("该消息及其后的回复会一并移除", substring = true).assertIsDisplayed()
            rule.onNodeWithText("取消").performClick()
        } finally {
            cleanup(rig)
        }
    }

    private data class MenuRig(
        val session: AgentSession,
        val profileId: String,
        val userId: String,
        val assistantId: String,
    )

    private fun prepareMessages(
        userText: String = "请解释这条笔记",
        assistantText: String = "**原文** \$x^2\$",
    ): MenuRig {
        val agent = ServiceLocator.agentController
        val profileId = configureVisionModel()
        val now = System.currentTimeMillis()
        val notebookId = "menu-test-${UUID.randomUUID().toString().substring(0, 8)}"
        val pageId = "p0001"
        val sessionId = UUID.randomUUID().toString().substring(0, 8)
        val userId = "u-${UUID.randomUUID().toString().substring(0, 8)}"
        val assistantId = "a-${UUID.randomUUID().toString().substring(0, 8)}"
        val session = AgentSession(
            id = sessionId,
            notebookId = notebookId,
            pageId = pageId,
            createdAt = now,
            updatedAt = now,
            providerId = profileId,
            modelId = "demo-vision",
            title = "消息菜单测试",
            messages = listOf(
                AgentMessage(
                    id = userId,
                    role = AgentMessage.Role.USER,
                    sentAt = now,
                    text = userText,
                ),
                AgentMessage(
                    id = assistantId,
                    role = AgentMessage.Role.ASSISTANT,
                    sentAt = now + 1,
                    text = assistantText,
                    modelId = "demo-vision",
                    phase = AgentMessage.Phase.DONE.name,
                ),
            ),
        )
        ServiceLocator.agentStore.saveSession(session)
        agent.openDrawer(PageRef(notebookId, pageId))
        rule.setContent {
            MilkTheme {
                Box(Modifier.width(480.dp).height(700.dp)) {
                    AgentDrawerHost(
                        onOpenSettings = {},
                        onSnip = {},
                        onExplainPage = {},
                        onSummarizeChapter = {},
                        pageLabel = "第 1 页",
                    )
                }
            }
        }
        rule.waitForIdle()
        return MenuRig(session, profileId, userId, assistantId)
    }

    private fun configureVisionModel(): String {
        val agent = ServiceLocator.agentController
        val title = "菜单测试-${System.nanoTime()}"
        agent.addProfile(
            presetId = "agent-test-custom",
            title = title,
            baseUrl = "https://example.invalid/v1",
            apiKey = "",
        )
        val profile = agent.state.value.profiles.last { it.title == title }
        agent.addModel(profile.id, "demo-vision", declaredVision = true)
        agent.setActiveProfile(profile.id)
        return profile.id
    }

    private fun cleanup(rig: MenuRig) {
        ServiceLocator.agentController.closeDrawer()
        runCatching { ServiceLocator.agentStore.deleteSession(rig.session) }
        ServiceLocator.agentController.removeProfile(rig.profileId)
    }
}
