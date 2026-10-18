package com.dishnest.app.data

import androidx.room.withTransaction
import com.dishnest.app.domain.Meal
import com.dishnest.app.domain.Planner
import java.time.LocalDate
import kotlinx.coroutines.flow.first

class MealWriter(private val database: KitchenDatabase) {
    suspend fun assign(meal: Meal) = database.withTransaction {
        val dao = database.kitchen()
        require(meal.servings in 1..100 && meal.slot in Planner.slots)
        LocalDate.parse(meal.date)
        require(dao.recipes().first().any { it.id == meal.recipeId }) { "Recipe no longer exists." }
        dao.meals().first().filter { it.date == meal.date && it.slot == meal.slot && it.id != meal.id }
            .forEach { dao.deleteMeal(it.id) }
        dao.meal(meal)
    }

    /** Read both meals again under the write transaction, so stale UI selections cannot resurrect them. */
    suspend fun swap(firstId: String, secondId: String) = database.withTransaction {
        require(firstId != secondId)
        val dao = database.kitchen()
        val meals = dao.meals().first()
        val first = requireNotNull(meals.find { it.id == firstId }) { "Meal no longer exists." }
        val second = requireNotNull(meals.find { it.id == secondId }) { "Meal no longer exists." }
        dao.meal(first.copy(date = second.date, slot = second.slot))
        dao.meal(second.copy(date = first.date, slot = first.slot))
    }
}
