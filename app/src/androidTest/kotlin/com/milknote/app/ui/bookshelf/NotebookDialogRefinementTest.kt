package com.milknote.app.ui.bookshelf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.milknote.app.ui.theme.MilkTheme
import com.milknote.notes.model.NotebookMeta
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NotebookDialogRefinementTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun newNotebook_mainLayerKeepsAdvancedOptionsBehindExplicitDisclosure() {
        rule.setContent {
            MilkTheme {
                NewNotebookDialog(onDismiss = {}, onCreate = { _, _, _, _, _, _, _ -> })
            }
        }

        rule.onNodeWithText("尺寸").assertDoesNotExist()
        rule.onNodeWithText("更多设置").assertIsDisplayed().performClick()
        rule.onNodeWithText("尺寸").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("方向").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("纸色").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("用途").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun newNotebook_largeFontKeepsMoreSettingsActionVisible() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                MilkTheme {
                    NewNotebookDialog(onDismiss = {}, onCreate = { _, _, _, _, _, _, _ -> })
                }
            }
        }

        rule.onNodeWithText("更多设置").assertIsDisplayed().performClick()
        rule.onNodeWithText("尺寸").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun newNotebook_previewIsHonestBookshelfCover() {
        rule.setContent {
            MilkTheme {
                NewNotebookDialog(onDismiss = {}, onCreate = { _, _, _, _, _, _, _ -> })
            }
        }

        rule.onNodeWithText("书架封面预览").assertIsDisplayed()
        rule.onNodeWithText("A x = λ x").assertDoesNotExist()
        rule.onNodeWithText("未命名").assertIsDisplayed()
    }

    @Test
    fun manageNotebook_editsPaperAndSpecificationAfterCreation() {
        var saved: NotebookMeta? = null
        val meta = NotebookMeta(
            id = "manage-test",
            name = "原笔记",
            createdAt = 0L,
            updatedAt = 0L,
        )
        rule.setContent {
            MilkTheme {
                NotebookManagePanel(
                    meta = meta,
                    folders = emptyList(),
                    onDismiss = {},
                    onSave = { saved = it },
                    onDelete = {},
                )
            }
        }

        rule.onNodeWithText("原笔记").performTextClearance()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("线性代数")
        rule.onNodeWithText("A4").performScrollTo().performClick()
        rule.onNodeWithText("竖向").performScrollTo().performClick()
        rule.onNodeWithText("浅蓝").performScrollTo().performClick()
        rule.onNodeWithText("阅读摘录").performScrollTo().performClick()
        rule.onNodeWithText("保存").performClick()

        assertEquals("线性代数", saved?.name)
        assertEquals("A4", saved?.pageSize)
        assertEquals("portrait", saved?.orientation)
        assertEquals("#EAF2F8", saved?.paperColor)
        assertEquals("阅读摘录", saved?.purpose)
    }
}
