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

class WriterChromeScrollTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun majorToolBand_swipesToLaserAndBackToBrush() {
        var clicked: WriterMajorTool? = null
        rule.setContent {
            MilkTheme {
                Box(Modifier.width(700.dp).height(48.dp)) {
                    WriterMajorToolbar(
                        state = WriterUiState(),
                        selectedTool = WriterMajorTool.BRUSH,
                        railMode = null,
                        onBack = {}, onShowPages = {}, onShowSnippets = {},
                        onTool = { clicked = it },
                        onRecord = {}, onVideo = {}, onAgent = {}, onMaterials = {},
                        onNewPage = {}, onMore = {}, agentOpen = false, agentBusy = false,
                    )
                }
            }
        }
        rule.onNodeWithTag("writer-major-tool-laser").assertDoesNotExist()
        repeat(4) { rule.onNodeWithTag("writer-major-tools").performTouchInput { swipeLeft() } }
        rule.onNodeWithTag("writer-major-tool-laser").assertIsDisplayed().performClick()
        assertEquals(WriterMajorTool.LASER, clicked)
        repeat(4) { rule.onNodeWithTag("writer-major-tools").performTouchInput { swipeRight() } }
        rule.onNodeWithTag("writer-major-tool-brush").assertIsDisplayed().performClick()
        assertEquals(WriterMajorTool.BRUSH, clicked)
    }

    @Test
    fun minorToolBand_swipesToWidthsAndBackToColors_andResetsForEraser() {
        var selectedColor = 0L
        var selectedWidth = 0f
        var selectedTool by mutableStateOf(WriterMajorTool.BRUSH)
        rule.setContent {
            MilkTheme {
                Box(Modifier.width(450.dp).height(48.dp)) {
                    WriterMinorToolbar(
                        state = WriterUiState(),
                        selectedTool = selectedTool,
                        textStyle = CardTextStyle(),
                        shapeTool = InkTool.RECT, shapeColor = 0xFF23221E, shapeWidth = 0.7f,
                        onUndo = {}, onRedo = {}, onCollapse = {},
                        onColor = { selectedColor = it },
                        onWidth = { selectedWidth = it }, onEraserWidth = { selectedWidth = it },
                        onLassoMode = {}, onLassoFilter = {},
                        onLassoSettings = {}, onImage = {}, onTextEditor = {}, onTextStyle = {},
                        onShapeTool = {}, onShapeColor = {}, onShapeWidth = {}, onStartShape = {},
                        onSnip = {}, onTape = {}, onMaterials = {},
                    )
                }
            }
        }
        repeat(3) { rule.onNodeWithTag("writer-minor-tools").performTouchInput { swipeLeft() } }
        rule.onNodeWithTag("writer-minor-width-2").assertIsDisplayed().performClick()
        assertEquals(1.2f, selectedWidth)
        repeat(3) { rule.onNodeWithTag("writer-minor-tools").performTouchInput { swipeRight() } }
        rule.onNodeWithTag("writer-minor-color-2").assertIsDisplayed().performClick()
        assertEquals(WriterToolSettings().palettes[InkTool.PEN]?.get(2), selectedColor)
        repeat(2) { rule.onNodeWithTag("writer-minor-tools").performTouchInput { swipeLeft() } }
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
                Box(Modifier.width(310.dp).height(500.dp)) {
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
        repeat(5) { rule.onNodeWithTag("writer-page-rail").performTouchInput { swipeUp() } }
        rule.onNodeWithTag("writer-page-p0012").assertIsDisplayed().performClick()
        assertEquals("p0012", switches.last())
        repeat(6) { rule.onNodeWithTag("writer-page-rail").performTouchInput { swipeDown() } }
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
                    Box(Modifier.width(310.dp).height(500.dp)) {
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
            repeat(8) { rule.onNodeWithTag("writer-snippet-list").performTouchInput { swipeUp() } }
            rule.onNodeWithTag("writer-snippet-insert-${ids[9]}").assertIsDisplayed().performClick()
            assertEquals(ids[9], inserted?.id)

            rule.onNodeWithContentDescription("搜索摘取卡片").performTextInput("needle-0")
            rule.waitUntil(timeoutMillis = 5000) {
                rule.onAllNodesWithTag("writer-snippet-card-${ids[0]}").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithTag("writer-snippet-card-${ids[0]}").assertIsDisplayed()
            rule.onNodeWithTag("writer-snippet-card-${ids[1]}").assertDoesNotExist()
            rule.onNodeWithContentDescription("搜索摘取卡片").performTextClearance()
            repeat(4) { rule.onNodeWithTag("writer-snippet-colors").performTouchInput { swipeLeft() } }
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
}
