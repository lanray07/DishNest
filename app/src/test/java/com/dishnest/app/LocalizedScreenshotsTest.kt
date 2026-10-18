package com.dishnest.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.withTransaction
import com.dishnest.app.domain.*
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** Real Compose UI and Room records; translated sample content is confined to this test. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [35], application = DishNestApp::class)
class LocalizedScreenshotsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    data class Copy(
        val title: String, val description: String, val ingredients: List<String>,
        val steps: List<String>, val pastaTitle: String, val pastaIngredients: List<String>,
        val pastaSteps: List<String>, val collection: String, val easy: String,
        val category: String,
    )
    data class Visual(val sample: Copy)

    private fun text(id: Int) = rule.activity.getString(id)
    private fun click(id: Int) = rule.onNodeWithText(text(id), useUnmergedTree = true).performClick()

    private fun flow(locale: String) {
        val type = object : TypeToken<Map<String, Visual>>() {}.type
        val copy = Gson().fromJson<Map<String, Visual>>(
            File("../marketing/visual-localization.json").readText(), type).getValue(locale).sample
        val vm = androidx.lifecycle.ViewModelProvider(rule.activity)[KitchenViewModel::class.java]
        rule.runOnUiThread { vm.deleteAll() }
        rule.waitUntil(20000) { rule.onAllNodesWithText(text(R.string.ui_explore_a_demo_kitchen_6bdc4e)).fetchSemanticsNodes().isNotEmpty() }
        click(R.string.ui_explore_a_demo_kitchen_6bdc4e)
        rule.waitUntil(20000) {
            rule.onAllNodesWithText("Lemon & herb chicken").fetchSemanticsNodes().isNotEmpty() &&
                vm.state.value.demo && vm.state.value.recipes.size == 4
        }
        val chicken = Recipe(id = "localized-chicken", title = copy.title,
            description = copy.description, photo = "demo", favourite = true,
            collection = copy.collection, difficulty = copy.easy, prepMinutes = 10, cookMinutes = 25,
            ingredients = copy.ingredients.map { Ingredients.parse(it).copy(category = copy.category) },
            steps = copy.steps, updatedAt = 2000)
        val pasta = Recipe(id = "localized-pasta", title = copy.pastaTitle,
            photo = "demo-pasta", collection = copy.collection, difficulty = copy.easy,
            prepMinutes = 10, cookMinutes = 20,
            ingredients = copy.pastaIngredients.map { Ingredients.parse(it).copy(category = copy.category) },
            steps = copy.pastaSteps, updatedAt = 1000)
        val database = vm.getApplication<DishNestApp>().database
        rule.runOnUiThread { vm.run {
            database.withTransaction {
                val dao = database.kitchen()
                dao.clearMeals(); dao.clearRecipes(); dao.clearPantry(); dao.clearShopping()
                dao.recipe(chicken); dao.recipe(pasta)
                chicken.ingredients.takeLast(3).forEach { ingredient ->
                    dao.pantry(PantryItem(name = ingredient.name, quantity = ingredient.quantity,
                        unit = ingredient.unit, expiry = LocalDate.now().plusDays(2).toString()))
                }
                (0..4).forEach { day -> dao.meal(Meal(
                    date = Planner.week(LocalDate.now()).plusDays(day.toLong()).toString(),
                    slot = "Dinner", recipeId = if (day % 2 == 0) chicken.id else pasta.id)) }
            }
            database.invalidationTracker.refreshAsync()
        } }
        try {
            rule.waitUntil(20000) {
                rule.onAllNodesWithText(copy.title).fetchSemanticsNodes().isNotEmpty() && vm.state.value.recipes.size == 2
            }
        } catch (error: Throwable) {
            println("Localized fixture state: $locale ${vm.state.value}")
            println(rule.onRoot().printToString())
            throw error
        }
        val folder = File("../artifacts/screens/localized/$locale").apply { mkdirs() }
        fun shot(name: String) {
            rule.runOnUiThread { vm.message.value = "" }
            rule.waitForIdle()
            if (!locale.startsWith("en")) {
                rule.onAllNodesWithText("Lemon & herb chicken").assertCountEquals(0)
                rule.onAllNodesWithText("Recipe book", useUnmergedTree = true).assertCountEquals(0)
                rule.onAllNodesWithText(" servings", substring = true).assertCountEquals(0)
            }
            rule.onRoot().captureRoboImage(File(folder, "tablet-$name.png").path)
        }
        shot("home")
        click(R.string.nav_recipes); shot("recipes")
        rule.onNodeWithText(copy.title).performClick(); shot("recipe-detail")
        rule.onNodeWithText(text(R.string.cook)).performScrollTo().performClick(); shot("cooking")
        click(R.string.ui_finish_cooking_073c7a)
        rule.onAllNodesWithText(text(R.string.ui_finish_cooking_073c7a)).onLast().performClick()
        click(R.string.nav_plan); shot("plan")
        rule.onNodeWithText(text(R.string.ui_make_shopping_list_ad0a17)).performScrollTo().performClick()
        rule.waitUntil(20000) {
            rule.onAllNodesWithText(text(R.string.copy_e2a2406740)).fetchSemanticsNodes().isNotEmpty() &&
                vm.state.value.shopping.isNotEmpty()
        }
        shot("shopping")
        click(R.string.nav_pantry); shot("pantry")
        rule.onNodeWithContentDescription(text(R.string.copy_024950cf2c)).performClick(); shot("import")
    }

    @Test @Config(qualifiers = "en-rGB-w1280dp-h800dp-land-mdpi") fun british() = flow("en-GB")
    @Test @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-mdpi") fun american() = flow("en-US")
    @Test @Config(qualifiers = "de-rDE-w1280dp-h800dp-land-mdpi") fun german() = flow("de-DE")
    @Test @Config(qualifiers = "es-rES-w1280dp-h800dp-land-mdpi") fun spanish() = flow("es-ES")
    @Test @Config(qualifiers = "fr-rFR-w1280dp-h800dp-land-mdpi") fun french() = flow("fr-FR")
    @Test @Config(qualifiers = "it-rIT-w1280dp-h800dp-land-mdpi") fun italian() = flow("it-IT")
    @Test @Config(qualifiers = "nl-rNL-w1280dp-h800dp-land-mdpi") fun dutch() = flow("nl-NL")
    @Test @Config(qualifiers = "pl-rPL-w1280dp-h800dp-land-mdpi") fun polish() = flow("pl-PL")
    @Test @Config(qualifiers = "pt-rBR-w1280dp-h800dp-land-mdpi") fun brazilian() = flow("pt-BR")
    @Test @Config(qualifiers = "pt-rPT-w1280dp-h800dp-land-mdpi") fun portuguese() = flow("pt-PT")
    @Test @Config(qualifiers = "ar-rEG-ldrtl-w1280dp-h800dp-land-mdpi") fun arabic() = flow("ar")
    @Test @Config(qualifiers = "hi-rIN-w1280dp-h800dp-land-mdpi") fun hindi() = flow("hi")
}
