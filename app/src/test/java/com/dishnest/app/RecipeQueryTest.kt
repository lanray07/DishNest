package com.dishnest.app

import com.dishnest.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class RecipeQueryTest {
    @Test fun allFiltersComposeWithoutConfusingUserCollectionNamesWithSpecialFilters() {
        val a = Recipe(id = "a", title = "Soup", description = "Warming", collection = "Favourites",
            cuisine = "Italian", tags = listOf("Vegan"), difficulty = "Easy", prepMinutes = 5, cookMinutes = 20,
            favourite = false, notes = "Freeze well")
        val b = a.copy(id = "b", favourite = true, cookMinutes = 50)
        val c = a.copy(id = "c", favourite = true, tags = listOf("Vegetarian"))
        val all = listOf(a, b, c)
        assertEquals(listOf(a), RecipeQuery(text = "freeze", collection = "Favourites", maximumMinutes = 30,
            cuisine = "italian", tag = "vegan", difficulty = "easy").apply(all))
        assertEquals(listOf(b), RecipeQuery(favouritesOnly = true, tag = "Vegan").apply(all))
        assertTrue(RecipeQuery(tag = "veg").apply(all).isEmpty())
    }

    @Test fun titleAndRecentSortsAreDeterministicAndSearchIncludesIngredients() {
        val a = Recipe(id = "a", title = "Ziti", updatedAt = 20, ingredients = listOf(Ingredient("tomatoes")))
        val b = Recipe(id = "b", title = "apple", updatedAt = 10)
        assertEquals(listOf(a, b), RecipeQuery().apply(listOf(b, a)))
        assertEquals(listOf(b, a), RecipeQuery(sortByTitle = true).apply(listOf(a, b)))
        assertEquals(listOf(a), RecipeQuery(text = " TOMATO ").apply(listOf(a, b)))
    }
}
