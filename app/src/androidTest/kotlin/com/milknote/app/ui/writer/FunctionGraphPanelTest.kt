package com.milknote.app.ui.writer

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.milknote.agent.FunctionGraphSpec
import com.milknote.app.ui.theme.MilkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FunctionGraphPanelTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `面板打开后可插入默认函数图像并关闭`() {
        var inserted: FunctionGraphSpec? = null
        var dismissed = false
        rule.setContent {
            MilkTheme {
                FunctionGraphPanel(
                    onDismiss = { dismissed = true },
                    onInsert = { spec, crop, done ->
                        inserted = spec
                        assertTrue(crop == null)
                        done(true)
                    },
                )
            }
        }
        rule.onNodeWithTag("function-graph-panel").assertIsDisplayed()
        rule.onNodeWithTag("function-graph-input").assertIsDisplayed()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        rule.onNodeWithTag("function-graph-insert").assertIsEnabled().performClick()
        rule.waitUntil(5_000) { dismissed }
        assertNotNull(inserted)
        assertEquals(1, inserted!!.curves.size)
    }

    @Test
    fun `添加曲线-颜色与框选入口可用`() {
        rule.setContent {
            MilkTheme {
                FunctionGraphPanel(onDismiss = {}, onInsert = { _, _, _ -> })
            }
        }
        rule.onNodeWithTag("function-graph-add").performClick()
        rule.onNodeWithTag("function-graph-row-1").assertIsDisplayed()
        rule.onNodeWithTag("function-graph-color-3").performClick()
        rule.onNodeWithTag("function-graph-key-fraction").performClick()
        rule.onNodeWithTag("function-graph-key-delete").performClick()
        rule.onNodeWithTag("function-graph-crop").performClick()
        rule.onNodeWithTag("function-graph-preview").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `删除第1行不会误删选中的第2行且输入区同步`() {
        rule.setContent {
            MilkTheme {
                FunctionGraphPanel(onDismiss = {}, onInsert = { _, _, _ -> })
            }
        }
        // 初始第 1 行 x^2;添加后第 2 行 x 并自动选中。
        rule.onNodeWithTag("function-graph-add").performClick()
        rule.onNodeWithTag("function-graph-row-1").assertIsDisplayed()
        rule.onNodeWithTag("function-graph-remove-0").performClick()
        rule.onNodeWithTag("function-graph-row-1").assertDoesNotExist()
        rule.onNodeWithTag("function-graph-row-0").assertIsDisplayed()
        rule.onNodeWithTag("function-graph-input").assertTextEquals("x")
    }
}
