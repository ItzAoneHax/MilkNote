package com.milknote.app.ui.writer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import com.milknote.app.di.ServiceLocator
import com.milknote.app.ui.cards.CardsViewModel
import com.milknote.app.ui.theme.MilkTheme
import com.milknote.ink.model.CardTextStyle
import com.milknote.ink.model.InkTool
import com.milknote.notes.model.NoteCard
import com.milknote.notes.model.NotebookPage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 第二代书写页 chrome:精简顶栏 + 居中悬浮笔匣(工具与当前工具内容同在笔匣内) */
class WriterChromeScrollTest {
    @get:Rule
    val rule = createComposeRule()

    @androidx.compose.runtime.Composable
    private fun dockContent(
        selectedTool: WriterMajorTool,
        onTool: (WriterMajorTool) -> Unit,
        onWidth: (Float) -> Unit,
    ) {
        WriterMinorToolbar(
            state = WriterUiState(),
            selectedTool = selectedTool,
            textStyle = CardTextStyle(),
            shapeTool = InkTool.RECT, shapeColor = 0xFF23221E, shapeWidth = 0.7f,
            dock = MinorDock.TOP, maxWidth = 1100.dp,
            onUndo = {}, onRedo = {}, onCollapse = {},
            onTool = onTool,
            onColor = {}, onWidth = onWidth, onEraserWidth = onWidth,
            onLassoMode = {}, onLassoFilter = {},
            onLassoSettings = {}, onImage = {}, onTextEditor = {}, onTextStyle = {},
            onShapeTool = {}, onShapeColor = {}, onShapeWidth = {}, onStartShape = {},
            onSnip = {}, onTape = {}, onMaterials = {}, onTune = {},
        )
    }

    @Test
    fun dock_showsCoreToolsInlineAndSwitchesBetweenPenAndLaser() {
        var clicked: WriterMajorTool? = null
        rule.setContent {
            MilkTheme {
                Box(Modifier.width(1100.dp).height(60.dp)) {
                    dockContent(WriterMajorTool.BRUSH, onTool = { clicked = it }, onWidth = {})
                }
            }
        }
        // 常驻六工具无需滚动即可见;溢出工具(套索/图片/文本…)收进「更多工具」菜单
        rule.onNodeWithTag("writer-major-tool-laser").assertIsDisplayed().performClick()
        assertEquals(WriterMajorTool.LASER, clicked)
        rule.onNodeWithTag("writer-major-tool-brush").assertIsDisplayed().performClick()
        assertEquals(WriterMajorTool.BRUSH, clicked)
        rule.onNodeWithTag("writer-major-tool-lasso").assertDoesNotExist()
        rule.onNodeWithTag("writer-major-tool-more").assertIsDisplayed()
        rule.onNodeWithTag("writer-snip").assertIsDisplayed()
    }

    @Test
    fun dock_content_showsWidthsAndColors_andResetsForEraser() {
        var selectedColor = 0L
        var selectedWidth = 0f
        var selectedTool by mutableStateOf(WriterMajorTool.BRUSH)
        rule.setContent {
            MilkTheme {
                Box(Modifier.width(1100.dp).height(60.dp)) {
                    dockContent(selectedTool, onTool = {}, onWidth = { selectedWidth = it })
                }
            }
        }
        // 颜色回调在 dockContent 中固定为空,这里只验证粗细与橡皮档位切换
        rule.onNodeWithTag("writer-minor-width-2").assertIsDisplayed().performClick()
        assertEquals(1.2f, selectedWidth)
        rule.onNodeWithTag("writer-minor-color-2").assertIsDisplayed()
        rule.runOnUiThread { selectedTool = WriterMajorTool.ERASER }
        rule.waitForIdle()
        rule.onNodeWithTag("writer-eraser-width-0").assertIsDisplayed().performClick()
        assertEquals(2f, selectedWidth)
    }

    @Test
    fun pageRail_swipesToTwelfthPageAndBack() {
        val pages = (1..12).map { NotebookPage("nb", "p%04d".format(it), 0L) }
        val switches = mutableListOf<String>()
        rule.setContent {
            MilkTheme {
                Box(Modifier.width(248.dp).height(500.dp)) {
                    WriterPageRail(
                        state = WriterUiState(pages = pages, currentPageId = "p0001"),
                        thumbs = emptyMap(), onSwitch = { switches += it }, onNewPage = {},
                        onDuplicatePage = {}, onClearPage = {}, onDeletePage = {},
                        onPrevious = {}, onNext = {}, onShowSnippets = {},
                    )
                }
            }
        }
        rule.onNodeWithTag("writer-page-p0012").assertDoesNotExist()
        repeat(6) { rule.onNodeWithTag("writer-page-rail").performTouchInput { swipeUp() } }
        rule.onNodeWithTag("writer-page-p0012").assertIsDisplayed().performClick()
        assertEquals("p0012", switches.last())
        repeat(7) { rule.onNodeWithTag("writer-page-rail").performTouchInput { swipeDown() } }
        rule.onNodeWithTag("writer-page-p0001").assertIsDisplayed().performClick()
        assertEquals("p0001", switches.last())
    }

    @Test
    fun snippetRail_scrollsFiltersSearchesAndInsertsRealCards() {
        val prefix = "instrument-snippet-${System.currentTimeMillis()}"
        val ids = (0 until 12).map { "$prefix-$it" }
        runBlocking {
            ids.forEachIndexed { index, id ->
                ServiceLocator.cardRepository.upsert(
                    NoteCard(id = id, title = "滚动卡片 $index", body = "needle-$index", color = "#3B5BEC"),
                )
            }
        }
        var inserted: NoteCard? = null
        try {
            val vm = CardsViewModel()
            rule.setContent {
                MilkTheme {
                    Box(Modifier.width(248.dp).height(500.dp)) {
                        WriterSnippetRail(
                            vm = vm,
                            currentNotebookId = "nb-test",
                            currentPageId = "p0001",
                            onInsert = { inserted = it },
                        )
                    }
                }
            }
            rule.waitUntil(timeoutMillis = 5000) {
                rule.onAllNodesWithTag("writer-snippet-card-${ids[0]}").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithTag("writer-snippet-card-${ids[9]}").assertDoesNotExist()
            repeat(10) { rule.onNodeWithTag("writer-snippet-list").performTouchInput { swipeUp() } }
            rule.onNodeWithTag("writer-snippet-insert-${ids[9]}").assertIsDisplayed().performClick()
            assertEquals(ids[9], inserted?.id)

            rule.onNodeWithContentDescription("搜索摘取卡片").performTextInput("needle-0")
            rule.waitUntil(timeoutMillis = 5000) {
                rule.onAllNodesWithTag("writer-snippet-card-${ids[0]}").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithTag("writer-snippet-card-${ids[0]}").assertIsDisplayed()
            rule.onNodeWithTag("writer-snippet-card-${ids[1]}").assertDoesNotExist()
            rule.onNodeWithContentDescription("搜索摘取卡片").performTextClearance()
            repeat(10) { rule.onNodeWithTag("writer-snippet-colors").performTouchInput { swipeLeft() } }
            rule.onNodeWithTag("writer-snippet-color-#2C8CC8").assertIsDisplayed().performClick()
            rule.onNodeWithTag("writer-snippet-empty").assertIsDisplayed()
        } finally {
            runBlocking { ids.forEach { ServiceLocator.cardRepository.delete(it) } }
        }
    }

    @Test
    fun shapePanel_scrollsToBottomAction() {
        var started = false
        rule.setContent {
            MilkTheme {
                ShapeSettingsDialog(
                    initialTool = InkTool.RECT,
                    initialColor = 0xFF23221E,
                    initialWidth = 0.7f,
                    onDismiss = {},
                    onStart = { started = true },
                )
            }
        }
        rule.onNodeWithTag("writer-floating-panel").assertIsDisplayed()
        repeat(3) { rule.onNodeWithTag("writer-panel-scroll").performTouchInput { swipeUp() } }
        rule.onNodeWithText("开始绘制").assertIsDisplayed().performClick()
        assertTrue(started)
    }

    @Test
    fun readingMode_showsExitPillAndRestoresDockOnTap() {
        val vm = WriterViewModel()
        val cardsVm = CardsViewModel()
        rule.setContent {
            MilkTheme {
                Box(Modifier.width(1100.dp).height(700.dp)) {
                    WriterScreen(
                        vm = vm,
                        cardsVm = cardsVm,
                        dark = false,
                        onBack = {},
                        onOpenDiagnostics = {},
                    )
                }
            }
        }
        rule.onNodeWithTag("writer-major-tool-reading").assertIsDisplayed().performClick()
        rule.onNodeWithTag("writer-major-tool-reading").assertDoesNotExist()
        rule.onNodeWithTag("writer-exit-reading").assertIsDisplayed().performClick()
        rule.onNodeWithTag("writer-exit-reading").assertDoesNotExist()
        rule.onNodeWithTag("writer-major-tool-brush").assertIsDisplayed()
    }
}
