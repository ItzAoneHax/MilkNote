package com.milknote.app.ui.bookshelf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.milknote.app.ui.theme.MilkTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BookshelfHeaderAdaptTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun cardsHeader_keepsSearchAndCreateReachableInNarrowContent() {
        var createClicked = false
        rule.setContent {
            MilkTheme {
                Box(Modifier.width(276.dp)) {
                    BookshelfHeaderBar(
                        title = "卡片",
                        railCollapsed = false,
                        isCards = true,
                        isTrash = false,
                        hasTrash = false,
                        listView = false,
                        onExpandRail = {},
                        onSearch = {},
                        onToggleListView = {},
                        onSortToggle = {},
                        sortExpanded = false,
                        onSortDismiss = {},
                        sortContent = {},
                        onPrimaryNew = { createClicked = true },
                        onPrimaryMoreCreate = {},
                        onEmptyTrash = {},
                    )
                }
            }
        }

        rule.onNodeWithText("新建卡片").assertDoesNotExist()
        rule.onNodeWithContentDescription("搜索").assertIsDisplayed()
        rule.onNodeWithText("新建").assertIsDisplayed().performClick()
        assertTrue(createClicked)
    }
}
