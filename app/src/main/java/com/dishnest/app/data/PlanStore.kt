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
        requireKitchen(name.isNotBlank() && name.length <= 200, KitchenProblem.PLAN_NAME)
        val saved = ensureDefault()
        requireKitchen(unlimited, KitchenProblem.PREMIUM)
        requireKitchen(saved.size < 100, KitchenProblem.PLAN_LIMIT)
        val dao = database.kitchen()
        saved.forEach { dao.plan(it.copy(active = false)) }
        MealPlan(name = name.trim(), active = true).also { dao.plan(it) }
    }

    suspend fun select(id: String) = database.withTransaction {
        val plans = ensureDefault()
        requireKitchen(plans.any { it.id == id }, KitchenProblem.MISSING_RECORD)
        plans.forEach { database.kitchen().plan(it.copy(active = it.id == id)) }
    }

    suspend fun rename(id: String, name: String) = database.withTransaction {
        requireKitchen(name.isNotBlank() && name.length <= 200, KitchenProblem.PLAN_NAME)
        val plan = (ensureDefault().find { it.id == id } ?: throw KitchenFailure(KitchenProblem.MISSING_RECORD))
        database.kitchen().plan(plan.copy(name = name.trim()))
    }

    suspend fun delete(id: String) = database.withTransaction {
        val dao = database.kitchen()
        val saved = ensureDefault()
        requireKitchen(saved.size > 1, KitchenProblem.KEEP_PLAN)
        val plan = (saved.find { it.id == id } ?: throw KitchenFailure(KitchenProblem.MISSING_RECORD))
        dao.deletePlanMeals(id)
        dao.deletePlan(id)
        if (plan.active) dao.plan(saved.first { it.id != id }.copy(active = true))
    }
}
