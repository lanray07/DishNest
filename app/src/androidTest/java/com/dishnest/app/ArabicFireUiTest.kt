package com.dishnest.app

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import com.dishnest.app.ui.number
import com.dishnest.app.ui.storedDate
import com.dishnest.app.domain.*
import kotlinx.coroutines.flow.first

class ArabicFireUiTest {
    @get:Rule val rule = createAndroidComposeRule<QaConfigurationActivity>()

    private fun reset() {
        check(BuildConfig.APPLICATION_ID == "com.dishnest.app.qa")
        lateinit var job: kotlinx.coroutines.Job
        rule.runOnIdle { job = ViewModelProvider(rule.activity)[KitchenViewModel::class.java].deleteAll() }
        runBlocking { job.join() }
    }

    @Test fun duplicateUsesArabicCopyLabelAndPreservesOriginalRecipe() {
        reset()
        val context = rule.activity
        val dao = (context.application as DishNestApp).database.kitchen()
        val source = Recipe(title = "وصفتي · My recipe", notes = "Keep notes · ملاحظات",
            ingredients = listOf(Ingredient("عدس", 200.0, "g")), steps = listOf("Keep my method · اخلط"))
        runBlocking { dao.recipe(source) }
        rule.onNodeWithText(context.getString(R.string.nav_recipes), useUnmergedTree = true).performClick()
        rule.onNodeWithText(source.title).performScrollTo().performClick()
        rule.onNodeWithContentDescription(context.getString(R.string.copy_02a9db66ee)).performClick()
        val expectedTitle = source.title + " (نسخة)"
        rule.onNodeWithText(expectedTitle).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(source.title + " (copy)").assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.ui_save_recipe_5622a7)).performScrollTo().performClick()
        rule.waitUntil(20000) { runBlocking { dao.recipes().first().size == 2 } }
        runBlocking {
            val recipes = dao.recipes().first()
            assertEquals(source, recipes.single { it.id == source.id })
            val copy = recipes.single { it.id != source.id }
            assertEquals(expectedTitle, copy.title)
            assertEquals(source.notes, copy.notes)
            assertEquals(source.ingredients, copy.ingredients)
            assertEquals(source.steps, copy.steps)
        }
    }

    @Test fun swapDisplaysArabicDatesAndRetainsCanonicalStoredDates() {
        reset()
        val context = rule.activity
        val app = context.application as DishNestApp
        val dao = app.database.kitchen()
        val monday = Planner.week(java.time.LocalDate.now())
        val recipe = Recipe(title = "شوربة الاختبار")
        val meals = runBlocking {
            val plan = com.dishnest.app.data.PlanStore(app.database).ensureDefault().single { it.active }
            dao.recipe(recipe)
            listOf(Meal(date = monday.toString(), slot = "Dinner", recipeId = recipe.id, planId = plan.id),
                Meal(date = monday.plusDays(1).toString(), slot = "Lunch", recipeId = recipe.id, planId = plan.id)).also {
                it.forEach { meal -> dao.meal(meal) }
            }
        }
        rule.onNodeWithText(context.getString(R.string.nav_plan), useUnmergedTree = true).performClick()
        rule.onNodeWithText(context.getString(R.string.swap_meals)).performScrollTo().performClick()
        val firstDate = context.resources.storedDate(meals[0].date)
        val secondDate = context.resources.storedDate(meals[1].date)
        rule.onNode(hasText(firstDate, substring = true)).performScrollTo().performClick()
        rule.onNode(hasText(firstDate, substring = true)).assertIsSelected()
        rule.onNode(hasText(meals[0].date, substring = true)).assertDoesNotExist()
        rule.onNode(hasText(secondDate, substring = true)).performScrollTo().performClick()
        rule.waitUntil(20000) { runBlocking {
            val saved = dao.meals().first()
            saved.single { it.id == meals[0].id }.date == meals[1].date &&
                saved.single { it.id == meals[1].id }.date == meals[0].date
        } }
    }

    @Test fun arabicPlannerAndNativeDialogRemainReachableAtLargeFontScale() {
        check(BuildConfig.APPLICATION_ID == "com.dishnest.app.qa")
        lateinit var reset: kotlinx.coroutines.Job
        rule.runOnIdle { reset = ViewModelProvider(rule.activity)[KitchenViewModel::class.java].deleteAll() }
        runBlocking { reset.join() }
        val context = rule.activity
        assertEquals("ar", context.resources.configuration.locales[0].language)
        assertEquals(1.5f, context.resources.configuration.fontScale, 0.001f)
        rule.onNodeWithText(context.getString(R.string.nav_plan), useUnmergedTree = true).performClick()
        val firstDay = com.dishnest.app.domain.Planner.week(java.time.LocalDate.now()).dayOfMonth
        rule.onNodeWithText(context.resources.number(firstDay)).assertIsDisplayed()
        rule.onNodeWithText(firstDay.toString()).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.manage_plans)).performClick()
        rule.onNodeWithText(context.getString(R.string.new_plan_name)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.create_plan)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("New plan name").assertDoesNotExist()
        val bitmap = rule.onNode(isDialog()).captureToImage().asAndroidBitmap()
        java.io.File(context.cacheDir, "fire-qa-arabic-large-font.png").outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
