package com.milknote.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.milknote.agent.PendingToolConfirmation
import com.milknote.agent.ToolTrace
import com.milknote.app.ui.common.MilkToolConfirmBar
import com.milknote.app.ui.common.MilkToolTraceList
import com.milknote.app.ui.theme.MilkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun skillsAndTools_liveUnderTeacherGroup() {
        showSettings("skills_tools")

        // 当前页归属「AI 教师」组;导航入口与本页标题同名,两处都在
        rule.onNodeWithText("AI 教师").assertIsDisplayed()
        rule.onAllNodesWithText("技能与工具").assertCountEquals(2)
    }

    @Test
    fun skillsAndTools_listEveryBuiltinToolWithConfirmBadges() {
        showSettings("skills_tools")

        rule.onNodeWithText("技能").assertIsDisplayed()
        rule.onNodeWithText("工具").assertIsDisplayed()

        listOf(
            "联网搜索",
            "数学计算",
            "新增页面",
            "删除页面",
            "插入 Markdown 卡片",
            "移动或删除页面元素",
            "新增技能",
        ).forEach { name ->
            rule.onNodeWithText(name).performScrollTo().assertIsDisplayed()
        }

        // 删除页面 / 移动或删除页面元素默认需要执行前确认
        val badges = rule.onAllNodesWithText("需确认").fetchSemanticsNodes()
        assertTrue("默认应有 2 个需确认徽标，实际 ${badges.size}", badges.size == 2)
    }

    @Test
    fun skillsAndTools_openSkillAndToolEditors() {
        showSettings("skills_tools")

        // 技能:新建 → 面板自带名称/摘要/正文/启用
        rule.onNodeWithText("新建").performScrollTo().performClick()
        rule.onNodeWithText("新建技能").assertIsDisplayed()
        rule.onNodeWithText("摘要").assertIsDisplayed()
        rule.onNodeWithText("正文").assertIsDisplayed()
        rule.onNodeWithText("取消").performClick()

        // 工具:点击行(非开关)打开设置面板,含确认策略与声明式参数
        rule.onNodeWithText("联网搜索").performScrollTo().performClick()
        rule.onNodeWithText("工具设置").assertIsDisplayed()
        rule.onNodeWithText("执行前确认").assertIsDisplayed()
        rule.onNodeWithText("恢复默认").assertIsDisplayed()
        rule.onNodeWithText("搜索引擎").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun capabilityKit_rendersTraceListAndConfirmBar() {
        var approved = 0
        var rejected = 0
        rule.setContent {
            MilkTheme {
                Column(Modifier.width(420.dp).padding(12.dp)) {
                    MilkToolTraceList(
                        listOf(
                            ToolTrace(
                                callId = "c1",
                                toolName = "web_search",
                                displayName = "联网搜索",
                                argumentsJson = """{"query":"矩阵"}""",
                                status = ToolTrace.Status.DONE,
                                resultSummary = "5 条结果",
                                output = "特征值与特征向量",
                            ),
                            ToolTrace(
                                callId = "c2",
                                toolName = "delete_page",
                                displayName = "删除页面",
                                argumentsJson = "{}",
                                status = ToolTrace.Status.PENDING_CONFIRM,
                                actionSummary = "将删除第 3 页",
                            ),
                        ),
                    )
                    Spacer(Modifier.height(12.dp))
                    MilkToolConfirmBar(
                        confirmation = PendingToolConfirmation(
                            callId = "c2",
                            toolId = "delete_page",
                            displayName = "删除页面",
                            actionSummary = "将删除第 3 页及其上的全部笔迹",
                            argumentsJson = "{}",
                        ),
                        onApprove = { approved++ },
                        onReject = { rejected++ },
                    )
                }
            }
        }

        // 轨迹行:名称 + 摘要;点击展开参数与输出
        rule.onNodeWithText("5 条结果").assertIsDisplayed()
        rule.onNodeWithText("联网搜索").performClick()
        rule.onNodeWithText("特征值与特征向量").assertIsDisplayed()

        // 确认条:名称 + 需确认徽标 + 动作描述 + 固定宽度动作
        rule.onAllNodesWithText("需确认").assertCountEquals(1)
        rule.onNodeWithText("将删除第 3 页及其上的全部笔迹").assertIsDisplayed()
        rule.onNodeWithText("确认").performClick()
        assertEquals(1, approved)
        assertEquals(0, rejected)
    }
}
