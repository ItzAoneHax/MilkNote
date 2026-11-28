package com.milknote.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
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
    fun skillsAndTools_listsBuiltinCapabilitiesWithReadOnlyBadges() {
        showSettings("skills_tools")

        rule.onNodeWithText("内置技能").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("内置工具").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("我的技能").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("自定义工具").assertDoesNotExist()

        listOf("分步解题规范", "康奈尔笔记整理", "公式速查卡片").forEach { name ->
            rule.onNodeWithText(name).performScrollTo().assertIsDisplayed()
        }

        listOf(
            "联网搜索",
            "数学计算",
            "新增页面",
            "删除页面",
            "插入 Markdown 卡片",
            "插入函数图像",
            "查看页面元素",
            "移动或删除页面元素",
            "读取页面元素时序",
            "阅读技能",
            "新增技能",
        ).forEach { name ->
            rule.onNodeWithText(name).performScrollTo().assertIsDisplayed()
        }

        // 删除页面 / 移动或删除页面元素 / 读取页面元素时序都是内置强制确认;旧“需申请”入口已下线。
        val confirmBadges = rule.onAllNodesWithText("需确认").fetchSemanticsNodes()
        assertEquals("三个内置强制确认工具应各有一个需确认徽标，实际 ${confirmBadges.size}", 3, confirmBadges.size)
        val applyBadges = rule.onAllNodesWithText("需申请").fetchSemanticsNodes()
        assertEquals("旧“需申请”授权入口已下线，实际 ${applyBadges.size}", 0, applyBadges.size)
    }

    @Test
    fun skillsAndTools_createsCustomSkillAndInspectsBuiltinReadOnlyDetails() {
        showSettings("skills_tools")

        // 自定义技能仍可新建编辑:面板自带名称/摘要/正文/启用
        rule.onNodeWithText("新建").performScrollTo().performClick()
        rule.onNodeWithText("新建技能").assertIsDisplayed()
        rule.onNodeWithText("名称").assertIsDisplayed()
        rule.onNodeWithText("摘要").assertIsDisplayed()
        rule.onNodeWithText("正文").assertIsDisplayed()
        rule.onNodeWithText("启用").assertIsDisplayed()
        rule.onNodeWithText("取消").performClick()

        // 内置技能:只读详情可查看,没有保存/编辑/开关/可输入定义
        val skillInputsBefore = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size
        val skillTogglesBefore = rule.onAllNodes(isToggleable()).fetchSemanticsNodes().size
        rule.onNodeWithText("康奈尔笔记整理").performScrollTo().performClick()
        rule.onNodeWithText("应用自带 · 只读").assertIsDisplayed()
        rule.onNodeWithText("线索栏", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(
            "内置技能随应用提供,不可编辑、关闭或删除;用户补充的方法请建为「我的技能」。",
        ).performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("保存").assertCountEquals(0)
        rule.onAllNodesWithText("编辑").assertCountEquals(0)
        rule.onAllNodesWithText("名称").assertCountEquals(0)
        rule.onAllNodesWithText("摘要").assertCountEquals(0)
        rule.onAllNodesWithText("正文").assertCountEquals(0)
        rule.onAllNodesWithText("启用").assertCountEquals(0)
        assertEquals(
            "只读详情不得新增开关",
            skillTogglesBefore,
            rule.onAllNodes(isToggleable()).fetchSemanticsNodes().size,
        )
        assertTrue(
            "只读详情不得新增输入框",
            rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size <= skillInputsBefore,
        )
        rule.onNodeWithText("关闭").performClick()

        // 内置工具:只读详情含说明/参数,没有工具设置/确认开关/设置字段
        val toolInputsBefore = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size
        val toolTogglesBefore = rule.onAllNodes(isToggleable()).fetchSemanticsNodes().size
        rule.onNodeWithText("联网搜索").performScrollTo().performClick()
        rule.onNodeWithText("应用自带 · 只读").assertIsDisplayed()
        // 背景工具列表行也带同一段说明且可点击;这里只定位只读窗口正文
        rule.onNode(
            hasText("搜索互联网获取最新信息", substring = true) and !hasClickAction(),
        ).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("参数").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("query", substring = true).performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("工具设置").assertCountEquals(0)
        rule.onAllNodesWithText("执行前确认").assertCountEquals(0)
        rule.onAllNodesWithText("搜索引擎").assertCountEquals(0)
        rule.onAllNodesWithText("保存").assertCountEquals(0)
        rule.onAllNodesWithText("恢复默认").assertCountEquals(0)
        assertEquals(
            "只读详情不得新增开关",
            toolTogglesBefore,
            rule.onAllNodes(isToggleable()).fetchSemanticsNodes().size,
        )
        assertTrue(
            "只读详情不得新增输入框",
            rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size <= toolInputsBefore,
        )
        rule.onNodeWithText("关闭").performClick()
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
