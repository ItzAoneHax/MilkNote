package com.milknote.app.ui.writer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 录音权限/录制状态机(纯 JVM):拒绝解释、前台开始/停止、关闭复位。 */
class RecordStateTest {

    @Test fun `未授权不能开始且给出说明`() {
        var state = reduceRecordState(RecordUiState(), RecordEvent.Start)
        assertFalse(state.isRecording)
        assertFalse(state.canStart)
        assertNotNull(state.message)

        state = reduceRecordState(state, RecordEvent.PermissionDenied)
        assertEquals(RecordPermission.DENIED, state.permission)
        assertFalse(state.canStart)
        assertNotNull(state.message)
    }

    @Test fun `授权后可开始 计时单调 停止保留时长`() {
        var state = reduceRecordState(RecordUiState(), RecordEvent.PermissionGranted)
        assertEquals(RecordPermission.GRANTED, state.permission)
        assertTrue(state.canStart)

        state = reduceRecordState(state, RecordEvent.Start)
        assertEquals(RecordPhase.RECORDING, state.phase)
        assertEquals(0L, state.elapsedMs)

        state = reduceRecordState(state, RecordEvent.Tick(1200L))
        state = reduceRecordState(state, RecordEvent.Tick(800L))   // 不回退
        assertEquals(1200L, state.elapsedMs)
        state = reduceRecordState(state, RecordEvent.Tick(2400L))
        assertEquals(2400L, state.elapsedMs)

        state = reduceRecordState(state, RecordEvent.Stopped(2400L))
        assertEquals(RecordPhase.IDLE, state.phase)
        assertEquals(2400L, state.elapsedMs)
        assertTrue(state.canStart)
    }

    @Test fun `关闭面板复位计时但保留权限`() {
        var state = reduceRecordState(RecordUiState(), RecordEvent.PermissionGranted)
        state = reduceRecordState(state, RecordEvent.Start)
        state = reduceRecordState(state, RecordEvent.Tick(1500L))
        state = reduceRecordState(state, RecordEvent.Dismiss)
        assertEquals(RecordPhase.IDLE, state.phase)
        assertEquals(0L, state.elapsedMs)
        assertEquals(RecordPermission.GRANTED, state.permission)
        assertNull(state.message)
    }

    @Test fun `启动失败进入错误态并允许重试`() {
        var state = reduceRecordState(RecordUiState(), RecordEvent.PermissionGranted)
        state = reduceRecordState(state, RecordEvent.Failed("设备被占用"))
        assertEquals(RecordPhase.ERROR, state.phase)
        assertEquals("设备被占用", state.message)
        assertTrue(state.canStart)

        state = reduceRecordState(state, RecordEvent.Start)
        assertEquals(RecordPhase.RECORDING, state.phase)
        assertNull(state.message)
    }

    @Test fun `计时格式`() {
        assertEquals("00:00", formatRecordElapsed(0L))
        assertEquals("00:09", formatRecordElapsed(9_400L))
        assertEquals("01:05", formatRecordElapsed(65_000L))
        assertEquals("1:01:01", formatRecordElapsed(3_661_000L))
    }
}
