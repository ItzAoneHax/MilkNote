package com.milknote.app.ui.writer

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.milknote.ink.input.InkController
import com.milknote.ink.render.InkSurfaceView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WriterInputInterruptionTest {
    @get:Rule
    val rule = createComposeRule()

    private fun withSurface(block: (InkSurfaceView, InkController) -> Unit) {
        val controller = InkController()
        lateinit var surface: InkSurfaceView
        rule.setContent {
            AndroidView(
                factory = { context ->
                    InkSurfaceView(context).also {
                        surface = it
                        it.controller = controller
                    }
                },
                modifier = Modifier.size(400.dp).testTag("writer-input-surface"),
            )
        }
        rule.waitForIdle()
        rule.runOnUiThread {
            surface.setPageSizePx(600f, 600f)
            block(surface, controller)
        }
        rule.waitForIdle()
    }

    @Test
    fun stylusHover_preventsAccidentalFingerInk() = withSurface { surface, controller ->
        surface.send(MotionEvent.ACTION_HOVER_MOVE, 200f, 200f, MotionEvent.TOOL_TYPE_STYLUS)
        surface.send(MotionEvent.ACTION_DOWN, 200f, 200f, MotionEvent.TOOL_TYPE_FINGER)
        surface.send(MotionEvent.ACTION_MOVE, 260f, 240f, MotionEvent.TOOL_TYPE_FINGER)
        surface.send(MotionEvent.ACTION_UP, 260f, 240f, MotionEvent.TOOL_TYPE_FINGER)
        assertEquals(0, controller.page.strokes.size)
    }

    @Test
    fun pointerCancel_cancelsActiveStrokeWithoutCommit() = withSurface { surface, controller ->
        surface.send(MotionEvent.ACTION_DOWN, 160f, 180f, MotionEvent.TOOL_TYPE_STYLUS)
        surface.send(MotionEvent.ACTION_MOVE, 250f, 260f, MotionEvent.TOOL_TYPE_STYLUS)
        surface.send(MotionEvent.ACTION_CANCEL, 250f, 260f, MotionEvent.TOOL_TYPE_STYLUS)
        assertEquals(0, controller.page.strokes.size)
        assertEquals(false, controller.drawing)
    }

    @Test
    fun secondContact_doesNotCreateAnotherStroke() = withSurface { surface, controller ->
        surface.send(MotionEvent.ACTION_DOWN, 140f, 160f, MotionEvent.TOOL_TYPE_STYLUS)
        surface.sendTwoPointers(MotionEvent.ACTION_POINTER_DOWN, 140f, 160f, 320f, 300f)
        surface.sendTwoPointers(MotionEvent.ACTION_POINTER_UP, 140f, 160f, 320f, 300f)
        surface.send(MotionEvent.ACTION_MOVE, 220f, 240f, MotionEvent.TOOL_TYPE_STYLUS)
        surface.send(MotionEvent.ACTION_UP, 220f, 240f, MotionEvent.TOOL_TYPE_STYLUS)
        assertEquals(1, controller.page.strokes.size)
    }

    @Test
    fun readingMode_allowsInputWithoutWritingInk() = withSurface { surface, controller ->
        surface.readingMode = true
        surface.send(MotionEvent.ACTION_DOWN, 160f, 180f, MotionEvent.TOOL_TYPE_STYLUS)
        surface.send(MotionEvent.ACTION_MOVE, 250f, 260f, MotionEvent.TOOL_TYPE_STYLUS)
        surface.send(MotionEvent.ACTION_UP, 250f, 260f, MotionEvent.TOOL_TYPE_STYLUS)
        assertEquals(0, controller.page.strokes.size)
    }

    private fun InkSurfaceView.send(action: Int, x: Float, y: Float, toolType: Int) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; this.toolType = toolType }
        val coordinates = MotionEvent.PointerCoords().apply {
            this.x = x; this.y = y; pressure = 1f; size = 1f
        }
        val event = MotionEvent.obtain(
            0L, SystemClock.uptimeMillis(), action, 1, arrayOf(properties), arrayOf(coordinates),
            0, 0, 1f, 1f, 0, 0,
            if (toolType == MotionEvent.TOOL_TYPE_FINGER) InputDevice.SOURCE_TOUCHSCREEN else InputDevice.SOURCE_STYLUS,
            0,
        )
        try {
            onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun InkSurfaceView.sendTwoPointers(action: Int, x1: Float, y1: Float, x2: Float, y2: Float) {
        val properties = arrayOf(
            MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS },
            MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER },
        )
        val coordinates = arrayOf(
            MotionEvent.PointerCoords().apply { x = x1; y = y1; pressure = 1f; size = 1f },
            MotionEvent.PointerCoords().apply { x = x2; y = y2; pressure = 1f; size = 1f },
        )
        val indexedAction = if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
            action or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        } else action
        val event = MotionEvent.obtain(
            0L, SystemClock.uptimeMillis(), indexedAction, 2, properties, coordinates,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
        )
        try {
            onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }
}
