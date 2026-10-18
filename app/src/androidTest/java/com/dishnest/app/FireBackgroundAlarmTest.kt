package com.dishnest.app

import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dishnest.app.data.TimerStore
import com.dishnest.app.platform.TimerScheduler
import com.dishnest.app.domain.*
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Run in two separate instrumentation processes, with am kill between them. */
@RunWith(AndroidJUnit4::class)
class FireBackgroundAlarmTest {
    @Test fun backgroundAlarmProbe(): Unit = runBlocking {
        check(BuildConfig.APPLICATION_ID == "com.dishnest.app.qa")
        val phase = InstrumentationRegistry.getArguments().getString("phase")
        assumeTrue(phase == "seed" || phase == "verify")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DishNestApp
        val dao = app.database.kitchen()
        val store = TimerStore(app.database, TimerScheduler(app))
        val label = "QA background alarm fixture"
        val recipe = Recipe(id = "qa-process-recipe", title = "شوربة · सूप", notes = "Keep these notes",
            ingredients = listOf(Ingredient("lentils", 200.0, "g")), steps = listOf("Rinse.", "Simmer."),
            favourite = true, updatedAt = 123456789L)
        val plan = MealPlan("qa-process-plan", "Saved process plan", true)
        val meal = Meal(id = "qa-process-meal", date = "2030-01-07", slot = "Dinner", recipeId = recipe.id, servings = 3, planId = plan.id)
        val pantry = PantryItem(id = "qa-process-pantry", name = "lentils", quantity = 50.0, unit = "g", expiry = "2030-01-10")
        val shopping = ShoppingItem(id = "qa-process-shopping", name = "lemons", quantity = 2.0, checked = true)
        val template = PlanTemplate("qa-process-template", "Saved process template", listOf(meal))
        if (phase == "seed") {
            app.database.withTransaction {
                dao.recipe(recipe); dao.plan(plan); dao.meal(meal)
                dao.pantry(pantry); dao.shopping(shopping); dao.template(template)
            }
            dao.timers().first().filter { it.label == label }.forEach { store.cancel(it.id) }
            store.start(label, 15)
            Unit
        } else {
            val timer = dao.timers().first().single { it.label == label }
            try {
                assertEquals(recipe, dao.recipes().first().single { it.id == recipe.id })
                assertEquals(plan, dao.plans().first().single { it.id == plan.id })
                assertEquals(meal, dao.meals().first().single { it.id == meal.id })
                assertEquals(pantry, dao.pantry().first().single { it.id == pantry.id })
                assertEquals(shopping, dao.shopping().first().single { it.id == shopping.id })
                assertEquals(template, dao.templates().first().single { it.id == template.id })
                assertTrue("Background receiver did not persist delivery", timer.notified)
                val manager = app.getSystemService(NotificationManager::class.java)
                assertTrue("Own timer notification missing", manager.activeNotifications.any { it.tag == "timer:${timer.id}" })
            } finally {
                store.cancel(timer.id)
                app.database.withTransaction {
                    dao.deleteMeal(meal.id); dao.deletePlan(plan.id); dao.deleteRecipe(recipe.id)
                    dao.deletePantry(pantry.id); dao.deleteShopping(shopping.id); dao.deleteTemplate(template.id)
                }
            }
        }
    }
}
