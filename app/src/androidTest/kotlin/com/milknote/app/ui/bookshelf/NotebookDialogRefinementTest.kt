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

    @Test
    fun manageNotebook_picksFolderFromDropdownWithoutTyping() {
        var saved: NotebookMeta? = null
        val meta = NotebookMeta(
            id = "manage-folder",
            name = "线性代数",
            createdAt = 0L,
            updatedAt = 0L,
        )
        rule.setContent {
            MilkTheme {
                NotebookManagePanel(
                    meta = meta,
                    folders = listOf("数学", "英语"),
                    onDismiss = {},
                    onSave = { saved = it },
                    onDelete = {},
                )
            }
        }

        // 未分组 → 下拉列表里点「数学」:全程不敲键盘,也不存在拼错文件夹名的可能
        rule.onNodeWithText("未分组").performClick()
        rule.onNodeWithText("数学").performClick()
        rule.onNodeWithText("未分组").assertDoesNotExist()
        rule.onNodeWithText("保存").performClick()

        assertEquals("数学", saved?.folder)
    }

    @Test
    fun manageNotebook_folderDropdownOffersCreateFolderEntry() {
        rule.setContent {
            MilkTheme {
                NotebookManagePanel(
                    meta = NotebookMeta(id = "manage-create-folder", name = "笔记", createdAt = 0L, updatedAt = 0L),
                    folders = emptyList(),
                    onDismiss = {},
                    onSave = {},
                    onDelete = {},
                )
            }
        }

        rule.onNodeWithText("未分组").performClick()
        rule.onNodeWithText("新建文件夹").performClick()

        // 底部动作行把「新建文件夹」叠成第二层浮层,并要求先给名字才能创建
        rule.onNodeWithText("文件夹名称").assertIsDisplayed()
        rule.onNodeWithText("创建").assertIsDisplayed()
    }
}
