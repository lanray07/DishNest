package com.dishnest.app

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Before
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test

class KitchenUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun resetDisposableQaKitchen() {
        check(BuildConfig.APPLICATION_ID == "com.dishnest.app.qa") { "Destructive UI fixtures require the isolated QA package" }
        lateinit var job: kotlinx.coroutines.Job
        rule.runOnIdle { job = ViewModelProvider(rule.activity)[KitchenViewModel::class.java].deleteAll() }
        runBlocking { job.join() }
        rule.waitForIdle()
    }

    @Test fun namedPlanSelectionSurvivesActivityRecreation() {
        rule.onNodeWithText("Explore a demo kitchen").performClick()
        rule.onNodeWithText("Meal planner", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Manage plans").performClick()
        rule.onNodeWithText("New plan name").performTextInput("Fire family plan")
        rule.onNodeWithText("Create plan").performScrollTo().performClick()
        rule.waitUntil(20000) {
            runBlocking { com.dishnest.app.data.PlanStore((rule.activity.application as DishNestApp).database)
                .ensureDefault().any { it.name == "Fire family plan" && it.active } }
        }
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.copy_7d9eb7acb1)).performClick()
        rule.activityRule.scenario.recreate()
        rule.onAllNodesWithText("Fire family plan").assertCountEquals(1)
        captureOwnUi("fire-qa-named-plan.png")
        val context = rule.activity.application as DishNestApp
        runBlocking {
            val plans = com.dishnest.app.data.PlanStore(context.database).ensureDefault()
            org.junit.Assert.assertEquals("Fire family plan", plans.single { it.active }.name)
            org.junit.Assert.assertEquals(5, context.database.kitchen().meals().first().size)
        }
    }

    @Test fun pantrySavesArabicDigitsAndMetricUnitWithoutChangingTheItemName() {
        fun text(id: Int) = rule.activity.getString(id)
        rule.onNodeWithText(text(R.string.nav_pantry), useUnmergedTree = true).performClick()
        rule.onAllNodesWithText(text(R.string.ui_add_item_d1a0b3)).onFirst().performClick()
        rule.onNodeWithText(text(R.string.ui_item_name_70d5f4)).performTextInput("عدس B１２")
        rule.onNodeWithText(text(R.string.ui_quantity_blank_if_unknown_1d8e1a)).performTextInput("٢٥٫٥")
        rule.onNodeWithText(text(R.string.ui_unit_g_kg_ml_l_tsp_tbsp_6e0535)).performTextInput("غرام")
        rule.onNodeWithText(text(R.string.ui_save_1509f5)).performClick()
        rule.waitUntil(20000) { rule.onAllNodesWithText("عدس B１２").fetchSemanticsNodes().isNotEmpty() }
        val app = rule.activity.application as DishNestApp
        runBlocking {
            val item = app.database.kitchen().pantry().first().single()
            org.junit.Assert.assertEquals("عدس B１２", item.name)
            org.junit.Assert.assertEquals(25.5, item.quantity!!, 0.0)
            org.junit.Assert.assertEquals("g", item.unit)
        }
    }

    private fun captureOwnUi(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(rule.activity.cacheDir, name).outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
    }

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
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("Fire smoke toast").assertExists()
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
