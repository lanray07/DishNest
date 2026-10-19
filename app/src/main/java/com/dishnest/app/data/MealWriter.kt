package com.dishnest.app.data

import androidx.room.withTransaction
import com.dishnest.app.domain.*
import com.dishnest.app.domain.Planner
import java.time.LocalDate
import kotlinx.coroutines.flow.first

class MealWriter(private val database: KitchenDatabase) {
    suspend fun assign(meal: Meal) = database.withTransaction {
        val dao = database.kitchen()
        requireKitchen(PlanStore(database).ensureDefault().any { it.id == meal.planId }, KitchenProblem.MISSING_RECORD)
        require(meal.servings in 1..100 && meal.slot in Planner.slots)
        LocalDate.parse(meal.date)
        requireKitchen(dao.recipes().first().any { it.id == meal.recipeId }, KitchenProblem.MISSING_RECORD)
        dao.meals().first().filter { it.planId == meal.planId && it.date == meal.date && it.slot == meal.slot && it.id != meal.id }
            .forEach { dao.deleteMeal(it.id) }
        dao.meal(meal)
    }

    /** Read both meals again under the write transaction, so stale UI selections cannot resurrect them. */
    suspend fun swap(firstId: String, secondId: String) = database.withTransaction {
        require(firstId != secondId)
        val dao = database.kitchen()
        val meals = dao.meals().first()
        val first = (meals.find { it.id == firstId } ?: throw KitchenFailure(KitchenProblem.MISSING_RECORD))
        val second = (meals.find { it.id == secondId } ?: throw KitchenFailure(KitchenProblem.MISSING_RECORD))
        requireKitchen(first.planId == second.planId, KitchenProblem.SAME_PLAN)
        // Release both unique slots inside the transaction before inserting their replacements.
        dao.deleteMeal(first.id)
        dao.deleteMeal(second.id)
        dao.meal(first.copy(date = second.date, slot = second.slot))
        dao.meal(second.copy(date = first.date, slot = first.slot))
    }
}
