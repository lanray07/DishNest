package com.dishnest.app.domain

import java.util.Locale

data class RecipeQuery(
    val text: String = "",
    val collection: String? = null,
    val favouritesOnly: Boolean = false,
    val maximumMinutes: Int? = null,
    val cuisine: String = "",
    val tag: String = "",
    val difficulty: String = "",
    val sortByTitle: Boolean = false,
) {
    fun apply(recipes: List<Recipe>): List<Recipe> {
        val matching = recipes.filter { r ->
            (collection == null || r.collection == collection) &&
                (!favouritesOnly || r.favourite) &&
                (maximumMinutes == null || r.prepMinutes + r.cookMinutes <= maximumMinutes) &&
                (cuisine.isBlank() || r.cuisine.equals(cuisine, true)) &&
                (tag.isBlank() || r.tags.any { it.equals(tag, true) }) &&
                (difficulty.isBlank() || r.difficulty.equals(difficulty, true)) &&
                (text.isBlank() || (listOf(r.title, r.cuisine, r.description, r.notes) +
                    r.tags + r.ingredients.map { it.name }).any { it.contains(text.trim(), true) })
        }
        return if (sortByTitle) matching.sortedWith(compareBy<Recipe> { it.title.lowercase(Locale.ROOT) }.thenBy { it.id })
        else matching.sortedByDescending { it.updatedAt }
    }
}
