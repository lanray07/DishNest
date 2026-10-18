package com.dishnest.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class KitchenUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun firstLaunchOffersAnEmptyPrivateKitchen() {
        rule.onNodeWithText("Welcome to your kitchen").assertIsDisplayed()
        rule.onNodeWithText("Save your first recipe").assertExists()
        rule.onNodeWithText("Explore a demo kitchen").assertExists()
        rule.onNodeWithText("Add recipe").performClick()
        rule.onNodeWithText("Recipe name").performTextInput("Fire smoke toast")
        rule.onNodeWithText("Ingredients · one per line (e.g. 200 g flour)")
            .performScrollTo().performTextInput("2 slices bread")
        rule.onNodeWithText("Method · one step per line")
            .performScrollTo().performTextInput("Toast and serve.")
        rule.onNodeWithText("Save recipe").performScrollTo().performClick()
        rule.waitUntil(20000) { rule.onAllNodesWithText("Fire smoke toast").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Recipe book", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Fire smoke toast").performClick()
        rule.onNodeWithContentDescription("Edit recipe").performClick()
        rule.onNodeWithText("Recipe name").performTextReplacement("Fire edited toast")
        rule.onNodeWithText("Save recipe").performScrollTo().performClick()
        rule.waitUntil(20000) { rule.onAllNodesWithText("Fire edited toast").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Delete recipe").performClick()
        rule.onNodeWithText("Delete").performClick()
        rule.waitUntil(20000) { rule.onAllNodesWithText("Fire edited toast").fetchSemanticsNodes().isEmpty() }
    }
}
