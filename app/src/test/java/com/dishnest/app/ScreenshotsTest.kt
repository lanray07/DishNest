package com.dishnest.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-mdpi", application = DishNestApp::class)
class ScreenshotsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun emptyKitchen() {
        rule.runOnUiThread {
            androidx.lifecycle
                .ViewModelProvider(rule.activity)[KitchenViewModel::class.java]
                .deleteAll()
        }
        rule.waitUntil(20000) {
            rule
                .onAllNodesWithText(
                    rule.activity.getString(R.string.ui_explore_a_demo_kitchen_6bdc4e)
                )
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun shot(name: String) {
        rule.runOnUiThread {
            androidx.lifecycle
                .ViewModelProvider(rule.activity)[KitchenViewModel::class.java]
                .message
                .value = ""
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("../artifacts/screens/$name.png")
    }

    @Test
    fun tabletFlow() {
        runFlow("tablet")
    }

    @Test
    fun manualRecipeCreateEditAndDelete() {
        rule.onNodeWithText("Add recipe").performClick()
        rule.onNodeWithText("Recipe name").performTextInput("Morning toast")
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        rule.onNodeWithText("Morning toast").assertExists()
        rule
            .onNodeWithText("Ingredients · one per line (e.g. 200 g flour)")
            .performScrollTo()
            .performTextInput("2 slices bread")
        rule
            .onNodeWithText("Method · one step per line")
            .performScrollTo()
            .performTextInput("Toast the bread and serve.")
        rule.onNodeWithText("Save recipe").performScrollTo().performClick()
        rule.waitUntil(20000) {
            rule.onAllNodesWithText("Morning toast").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Recipe book", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Morning toast").performClick()
        rule.onNodeWithContentDescription("Edit recipe").performClick()
        rule.onNodeWithText("Recipe name").performTextReplacement("Breakfast toast")
        rule.onNodeWithText("Save recipe").performScrollTo().performClick()
        rule.waitUntil(20000) {
            rule.onAllNodesWithText("Breakfast toast").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription("Delete recipe").performClick()
        rule.onNodeWithText("Delete").performClick()
        rule.waitUntil(20000) {
            rule.onAllNodesWithText("Breakfast toast").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun runFlow(device: String) {
        File("../artifacts/screens").mkdirs()
        rule.waitUntil(20000) {
            rule.onAllNodesWithText("Explore a demo kitchen").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Explore a demo kitchen").performClick()
        rule.waitUntil(20000) {
            rule.onAllNodesWithText("Lemon & herb chicken").fetchSemanticsNodes().isNotEmpty()
        }
        shot("$device-home")
        rule.onNodeWithText("Recipe book", useUnmergedTree = true).performClick()
        shot("$device-recipes")
        rule.onNodeWithText("Lemon & herb chicken").performClick()
        shot("$device-recipe-detail")
        rule.onNodeWithText("Start cooking").performScrollTo().performClick()
        shot("$device-cooking")
        rule.onNodeWithText("Finish cooking").performClick()
        rule.onAllNodesWithText("Finish cooking").onLast().performClick()
        rule.onNodeWithText("Meal planner", useUnmergedTree = true).performClick()
        shot("$device-plan")
        rule.onNodeWithText("Make shopping list").performScrollTo().performClick()
        rule.waitUntil(20000) {
            rule.onAllNodesWithText("Everything you need").fetchSemanticsNodes().isNotEmpty()
        }
        shot("$device-shopping")
        rule.onNodeWithText("Pantry", useUnmergedTree = true).performClick()
        shot("$device-pantry")
        rule.onNodeWithContentDescription("Import recipe").performClick()
        shot("$device-import")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h800dp-port-xhdpi", application = DishNestApp::class)
    fun phoneFlow() {
        runFlow("phone")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w400dp-h800dp-port-xhdpi", application = DishNestApp::class)
    fun phoneFirstLaunch() {
        rule.waitUntil(20000) {
            rule.onAllNodesWithText("Welcome to your kitchen").fetchSemanticsNodes().isNotEmpty()
        }
        shot("phone-empty-home")
    }

    @Test
    @Config(
        sdk = [35],
        qualifiers = "ar-rEG-ldrtl-w1280dp-h800dp-land-mdpi",
        application = DishNestApp::class,
    )
    fun arabicNavigationAndRtlLayout() {
        rule.onNodeWithText("مطبخي", useUnmergedTree = true).assertExists()
        rule
            .onNodeWithText(rule.activity.getString(R.string.ui_explore_a_demo_kitchen_6bdc4e))
            .performClick()
        rule.waitUntil(20000) {
            rule.onAllNodesWithText("Lemon & herb chicken").fetchSemanticsNodes().isNotEmpty()
        }
        shot("tablet-rtl-ar")
    }
}
