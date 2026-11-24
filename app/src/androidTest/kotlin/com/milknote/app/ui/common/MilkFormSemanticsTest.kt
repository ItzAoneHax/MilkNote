package com.milknote.app.ui.common

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.milknote.app.ui.theme.MilkTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MilkFormSemanticsTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun actionChip_isButtonWithTouchTarget() {
        rule.setContent {
            MilkTheme {
                MilkActionChip(label = "存卡片", onClick = {})
            }
        }

        val node = rule.onNodeWithTag("milk-chip-存卡片").assertIsDisplayed()
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val bounds = node.getUnclippedBoundsInRoot()
        assertTrue(bounds.right - bounds.left >= 48.dp)
        assertTrue(bounds.bottom - bounds.top >= 48.dp)
    }

    @Test
    fun selectionChip_keepsRadioRoleAndTouchTarget() {
        rule.setContent {
            MilkTheme {
                MilkChip(label = "点阵", selected = true, onClick = {}, small = true)
            }
        }

        val node = rule.onNodeWithTag("milk-chip-点阵").assertIsDisplayed()
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        val bounds = node.getUnclippedBoundsInRoot()
        assertTrue(bounds.right - bounds.left >= 48.dp)
        assertTrue(bounds.bottom - bounds.top >= 48.dp)
    }

    @Test
    fun segmentedOption_hasTouchTarget() {
        rule.setContent {
            MilkTheme {
                MilkSegmented(
                    options = listOf("portrait", "landscape"),
                    selected = "landscape",
                    label = { if (it == "portrait") "竖向" else "横向" },
                    onSelect = {},
                    testTagFor = { "segment-$it" },
                )
            }
        }

        val bounds = rule.onNodeWithTag("segment-portrait")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertTrue(bounds.bottom - bounds.top >= 48.dp)
    }

    @Test
    fun colorDot_hasTouchTargetAndReadableName() {
        rule.setContent {
            MilkTheme {
                MilkColorDot(
                    color = Color.White,
                    selected = false,
                    onClick = {},
                    contentDescription = "纸色 奶白",
                )
            }
        }

        val bounds = rule.onNodeWithContentDescription("纸色 奶白")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertTrue(bounds.right - bounds.left >= 48.dp)
        assertTrue(bounds.bottom - bounds.top >= 48.dp)
    }
}
