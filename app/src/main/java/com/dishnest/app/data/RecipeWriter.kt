package com.dishnest.app.data

import androidx.room.withTransaction
import com.dishnest.app.domain.Recipe
import com.dishnest.app.platform.RecipePhotos
import kotlinx.coroutines.flow.first

/** Serialise the free-tier limit check with the write; UI Flow snapshots may lag. */
class RecipeWriter(private val database: KitchenDatabase, private val photos: RecipePhotos? = null) {
    suspend fun save(recipe: Recipe, unlimited: Boolean) {
        database.withTransaction {
            val dao = database.kitchen()
            val saved = dao.recipes().first()
            require(unlimited || saved.any { it.id == recipe.id } || saved.size < 25) {
                "The free recipe book holds 25 recipes."
            }
            // Existing recipes stay editable even when a former Premium book exceeds 25.
            dao.recipe(recipe.copy(updatedAt = System.currentTimeMillis()))
            saved.find { it.id == recipe.id }?.photo?.let { old ->
                photos?.removeIfUnused(old, dao.recipes().first().map { it.photo })
            }
        }
    }

    suspend fun delete(id: String) = database.withTransaction {
        val dao = database.kitchen()
        val photo = dao.recipes().first().find { it.id == id }?.photo
        dao.deleteRecipeMeals(id)
        dao.deleteRecipe(id)
        dao.templates().first().forEach { template ->
            val meals = template.meals.filter { it.recipeId != id }
            if (meals.isEmpty()) dao.deleteTemplate(template.id)
            else dao.template(template.copy(meals = meals))
        }
        if (photo != null) photos?.removeIfUnused(photo, dao.recipes().first().map { it.photo })
    }

    suspend fun toggleFavourite(id: String) = database.withTransaction {
        val dao = database.kitchen()
        val recipe = dao.recipes().first().find { it.id == id } ?: return@withTransaction
        dao.recipe(recipe.copy(favourite = !recipe.favourite))
    }
}
