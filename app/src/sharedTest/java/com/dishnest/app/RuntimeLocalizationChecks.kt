package com.dishnest.app

import android.content.Context
import android.content.res.Configuration
import com.dishnest.app.data.RecipeImport
import com.dishnest.app.domain.*
import com.dishnest.app.ui.*
import java.util.Locale
import org.junit.Assert.*

/** The same checks run on host resources and the real Fire resource/number-format runtime. */
object RuntimeLocalizationChecks {
    fun allLocales(context: Context) {
        val locales = listOf("en-US", "en-GB", "de", "es", "fr", "it", "pt-BR", "pt-PT", "nl", "pl", "ar", "hi")
        val privateText = "My recipe · وصفتي · मेरी रेसिपी"
        for (tag in locales) {
            val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            }).resources
            for (problem in KitchenProblem.entries) {
                val message = resources.kitchenFailure(KitchenFailure(problem))
                assertTrue("$tag/$problem", message.isNotBlank())
                assertFalse(message.contains(problem.name))
            }
            val secret = "provider://private-record/token=must-never-be-shown"
            assertEquals(resources.getString(R.string.error_action_retry), resources.kitchenFailure(IllegalStateException(secret)))
            assertFalse(resources.kitchenFailure(IllegalStateException(secret)).contains(secret))
            assertEquals(privateText, resources.shoppingCategory(privateText))
            assertEquals(privateText, resources.difficulty(privateText))
            assertEquals(resources.getString(R.string.category_produce), resources.shoppingCategory("Fruit & vegetables"))
            assertEquals(resources.getString(R.string.difficulty_easy), resources.difficulty("Easy"))
            assertEquals(resources.getString(R.string.amount_unspecified), resources.amount(null, "g"))
            val locale = resources.configuration.locales[0]
            val expectedNumber = java.text.NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false }.format(1234)
            assertEquals(tag, expectedNumber, resources.number(1234))
            val numericDate = resources.calendarDate(java.time.LocalDate.of(2030, 12, 23), "d/M/yyyy")
            if (tag == "ar") {
                assertEquals("١٢٣٤", resources.number(1234))
                assertEquals("٢٣/١٢/٢٠٣٠", numericDate)
            }
            assertTrue(tag, numericDate.isNotBlank())
            val recipe = Recipe(title = privateText, servings = 3, ingredients = listOf(Ingredient("lentils", 200.0, "g")),
                steps = listOf("Keep my own words · اخلط"), notes = "My private note")
            val exported = recipeText(recipe, resources)
            assertTrue(exported.contains(resources.getString(R.string.method_heading)))
            assertTrue(exported.contains(resources.getString(R.string.ui_ingredients_87675d)))
            assertTrue(exported.contains(recipe.notes))
            val restored = RecipeImport.text(exported)
            assertEquals(tag, privateText, restored.title)
            assertEquals("$tag ingredient section", 1, restored.ingredients.size)
            assertEquals(tag, "lentils", restored.ingredients.single().name)
            assertEquals(tag, 200.0, restored.ingredients.single().quantity!!, 0.0)
            assertEquals(tag, "g", restored.ingredients.single().unit)
            assertTrue(tag, restored.steps.first().contains(recipe.steps.single()))
            val shopping = shoppingText(listOf(ShoppingItem(name = privateText, quantity = null, category = "Other")), resources)
            assertTrue(shopping.contains(resources.getString(R.string.category_other)))
            assertTrue(shopping.contains(resources.getString(R.string.amount_unspecified)))
            assertTrue(shopping.contains(privateText))
        }
    }

    fun resourceLocaleWinsOverProcessLocale(context: Context) {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocale(Locale.GERMAN)
            }).resources
            assertEquals("1,5 g", resources.amount(1.5, "g"))
            assertEquals("1,5 MyCup", resources.amount(1.5, "MyCup"))
            assertEquals("1,5 tsp", resources.amount(1.5, "tsp", canonicalUnit = true))
        } finally { Locale.setDefault(previous) }
    }
}
