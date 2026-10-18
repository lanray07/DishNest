package com.dishnest.app.data

import androidx.room.withTransaction
import com.dishnest.app.domain.Recipe
import kotlinx.coroutines.flow.first

/** Serialise the free-tier limit check with the write; UI Flow snapshots may lag. */
class RecipeWriter(private val database: KitchenDatabase) {
    suspend fun save(recipe: Recipe, unlimited: Boolean) {
        database.withTransaction {
            val dao = database.kitchen()
            val saved = dao.recipes().first()
            require(unlimited || saved.any { it.id == recipe.id } || saved.size < 25) {
                "The free recipe book holds 25 recipes."
            }
            // Existing recipes stay editable even when a former Premium book exceeds 25.
            dao.recipe(recipe.copy(updatedAt = System.currentTimeMillis()))
        }
    }
}
