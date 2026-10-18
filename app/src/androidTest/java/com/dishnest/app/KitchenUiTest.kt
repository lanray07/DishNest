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
    }
}
