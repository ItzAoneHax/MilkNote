package com.milknote.app.ui.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 消息右键/长按菜单的角色与忙碌策略(纯展示逻辑) */
class MessageMenuPolicyTest {

    @Test
    fun `AI 菜单只有复制与查看源文本且始终可用`() {
        val actions = messageMenuActions(MessageMenuTarget.AI, busy = true)
        assertEquals(listOf("copy", "source"), actions.map { it.id })
        assertEquals(listOf("复制", "查看源文本"), actions.map { it.label })
        assertTrue(actions.all { it.enabled })
        assertFalse(actions.any { it.destructive })
    }

    @Test
    fun `用户菜单包含复制修改撤回-忙碌时只禁用会改动回合的项`() {
        val idle = messageMenuActions(MessageMenuTarget.USER, busy = false)
        assertEquals(listOf("copy", "edit", "retract"), idle.map { it.id })
        assertTrue(idle.all { it.enabled })
        assertTrue(idle.first { it.id == "retract" }.destructive)

        val busy = messageMenuActions(MessageMenuTarget.USER, busy = true)
        assertTrue("复制不影响在途回合", busy.first { it.id == "copy" }.enabled)
        assertFalse("生成中/待确认时不得修改", busy.first { it.id == "edit" }.enabled)
        assertFalse("生成中/待确认时不得撤回", busy.first { it.id == "retract" }.enabled)
        assertEquals("禁用项保留位置", idle.map { it.id }, busy.map { it.id })
    }
}
