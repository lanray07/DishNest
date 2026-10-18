package com.dishnest.app.data

import androidx.room.withTransaction
import com.dishnest.app.domain.*
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Load explicit demo content into the selected plan, preserving saved plan identities. */
class DemoKitchen(private val database: KitchenDatabase) {
    suspend fun load() = database.withTransaction {
        val dao = database.kitchen()
        require(dao.recipes().first().isEmpty() && dao.pantry().first().isEmpty() &&
            dao.meals().first().isEmpty() && dao.shopping().first().isEmpty() &&
            dao.templates().first().isEmpty()) { "Demo can only be loaded into an empty kitchen." }
        val planId = PlanStore(database).activeId()
        val recipes = DemoData.recipes()
        recipes.forEach { dao.recipe(it) }
        DemoData.pantry().forEach { dao.pantry(it) }
        val start = Planner.week(LocalDate.now())
        (0..4).forEach { day ->
            dao.meal(Meal(date = start.plusDays(day.toLong()).toString(), slot = "Dinner",
                recipeId = recipes[day % recipes.size].id, planId = planId))
        }
    }
}
