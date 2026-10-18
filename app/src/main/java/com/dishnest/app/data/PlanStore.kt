package com.dishnest.app.data

import androidx.room.withTransaction
import com.dishnest.app.domain.*
import kotlinx.coroutines.flow.first

/** The selected plan and the free creation cap are checked against persisted state. */
class PlanStore(private val database: KitchenDatabase) {
    suspend fun ensureDefault(): List<MealPlan> = database.withTransaction {
        val dao = database.kitchen()
        dao.plans().first().ifEmpty { listOf(defaultPlan().also { dao.plan(it) }) }
    }

    suspend fun activeId(): String = ensureDefault().single { it.active }.id

    suspend fun create(name: String, unlimited: Boolean): MealPlan = database.withTransaction {
        require(name.isNotBlank() && name.length <= 200) { "Enter a plan name (up to 200 characters)." }
        val saved = ensureDefault()
        require(unlimited) { "Multiple meal plans require Premium." }
        require(saved.size < 100) { "The plan limit is 100." }
        val dao = database.kitchen()
        saved.forEach { dao.plan(it.copy(active = false)) }
        MealPlan(name = name.trim(), active = true).also { dao.plan(it) }
    }

    suspend fun select(id: String) = database.withTransaction {
        val plans = ensureDefault()
        require(plans.any { it.id == id }) { "Plan no longer exists." }
        plans.forEach { database.kitchen().plan(it.copy(active = it.id == id)) }
    }

    suspend fun rename(id: String, name: String) = database.withTransaction {
        require(name.isNotBlank() && name.length <= 200) { "Enter a plan name (up to 200 characters)." }
        val plan = requireNotNull(ensureDefault().find { it.id == id }) { "Plan no longer exists." }
        database.kitchen().plan(plan.copy(name = name.trim()))
    }

    suspend fun delete(id: String) = database.withTransaction {
        val dao = database.kitchen()
        val saved = ensureDefault()
        require(saved.size > 1) { "Keep at least one meal plan." }
        val plan = requireNotNull(saved.find { it.id == id }) { "Plan no longer exists." }
        dao.deletePlanMeals(id)
        dao.deletePlan(id)
        if (plan.active) dao.plan(saved.first { it.id != id }.copy(active = true))
    }
}
