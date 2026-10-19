package com.dishnest.app.data

import androidx.room.withTransaction
import com.dishnest.app.domain.Backup
import com.dishnest.app.domain.KitchenProblem
import com.dishnest.app.domain.requireKitchen
import com.dishnest.app.domain.bundledPhotoIds
import kotlinx.coroutines.flow.first

/** Take and restore one consistent database snapshot, independent of UI subscriptions. */
class KitchenBackups(private val database: KitchenDatabase) {
    suspend fun snapshot(): Backup = database.withTransaction {
        val dao = database.kitchen()
        Backup(
            recipes = dao.recipes().first(), meals = dao.meals().first(),
            pantry = dao.pantry().first(), shopping = dao.shopping().first(),
            templates = dao.templates().first(),
            plans = PlanStore(database).ensureDefault(),
        )
    }

    suspend fun exportJson(): String = BackupCodec.encode(snapshot().let { backup ->
        backup.copy(recipes = backup.recipes.map {
            it.copy(photo = it.photo.takeIf { photo -> photo in bundledPhotoIds }.orEmpty())
        })
    })

    suspend fun restore(backup: Backup) = database.withTransaction {
        val dao = database.kitchen()
        requireKitchen(dao.recipes().first().isEmpty() && dao.meals().first().isEmpty() &&
            dao.pantry().first().isEmpty() && dao.shopping().first().isEmpty() &&
            dao.templates().first().isEmpty() && dao.plans().first().let {
                it.isEmpty() || it == listOf(com.dishnest.app.domain.defaultPlan())
            }, KitchenProblem.EMPTY_KITCHEN)
        dao.clearPlans()
        backup.plans.forEach { dao.plan(it) }
        backup.recipes.forEach { dao.recipe(it) }
        backup.meals.forEach { dao.meal(it) }
        backup.pantry.forEach { dao.pantry(it) }
        backup.shopping.forEach { dao.shopping(it) }
        backup.templates.forEach { dao.template(it) }
    }
}
