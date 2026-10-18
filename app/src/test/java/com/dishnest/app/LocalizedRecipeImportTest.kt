package com.dishnest.app

import com.dishnest.app.data.RecipeImport
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class LocalizedRecipeImportTest {
    @Test fun recognisesHeadingsAcrossAllTwelveAppLocales() {
        val headings = listOf(
            "en-US" to ("Ingredients" to "Instructions"),
            "en-GB" to ("Ingredients" to "Method"),
            "de" to ("Zutaten" to "Zubereitung"),
            "es" to ("Ingredientes" to "Preparación"),
            "fr" to ("Ingrédients" to "Préparation"),
            "it" to ("Ingredienti" to "Procedimento"),
            "pt-BR" to ("Ingredientes" to "Modo de preparo"),
            "pt-PT" to ("Ingredientes" to "Modo de preparação"),
            "nl" to ("Ingrediënten" to "Bereiding"),
            "pl" to ("Składniki" to "Sposób przygotowania"),
            "ar" to ("المكونات" to "طريقة التحضير"),
            "hi" to ("सामग्री" to "विधि"),
        )
        for ((locale, sections) in headings) {
            val recipe = RecipeImport.text("Soupe à moi\nMy notes\n${sections.first} :\n• 200 g lentils\n${sections.second}：\n1. Rinse.\n2) Simmer.")
            assertEquals(locale, "Soupe à moi", recipe.title)
            assertEquals(locale, "My notes", recipe.description)
            assertEquals(locale, "lentils", recipe.ingredients.single().name)
            assertEquals(locale, 200.0, recipe.ingredients.single().quantity!!, 0.0)
            assertEquals(locale, listOf("Rinse.", "Simmer."), recipe.steps)
            assertEquals(locale, 0, recipe.servings) // Still requires user review, never inferred.
        }
    }

    @Test fun preservesUserTextAndOnlyTreatsWholeLinesAsHeadings() {
        val recipe = RecipeImport.text("Ingredients for life\nIngredients are seasonal\nIngre\u0301dients:\n* 1 apple\nMéthode:\n١. Mélanger les ingrédients.\n२। सामग्री मिलाएँ।")
        assertEquals("Ingredients for life", recipe.title)
        assertEquals("Ingredients are seasonal", recipe.description)
        assertEquals("apple", recipe.ingredients.single().name)
        assertEquals(listOf("Mélanger les ingrédients.", "सामग्री मिलाएँ।"), recipe.steps)
    }

    @Test fun headingsAreIndependentOfTheDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val recipe = RecipeImport.text("Soup\nINGREDIENTS\n200 g lentils\nINSTRUCTIONS\nRinse.")
            assertEquals("lentils", recipe.ingredients.single().name)
            assertEquals(listOf("Rinse."), recipe.steps)
        } finally { Locale.setDefault(previous) }
    }
}
