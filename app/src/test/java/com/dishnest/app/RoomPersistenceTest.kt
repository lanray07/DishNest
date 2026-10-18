package com.dishnest.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dishnest.app.data.KitchenDatabase
import com.dishnest.app.data.RecipeWriter
import com.dishnest.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = DishNestApp::class)
class RoomPersistenceTest {
    @Test
    fun simultaneousFreeWritesCannotOverrunCapAndExistingRecipesRemainEditable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<DishNestApp>()
        val db = Room.inMemoryDatabaseBuilder(context, KitchenDatabase::class.java).build()
        try {
            val writer = RecipeWriter(db)
            val results = coroutineScope {
                (1..40)
                    .map { index ->
                        async(Dispatchers.Default) {
                            runCatching {
                                writer.save(Recipe(title = "Recipe $index"), unlimited = false)
                            }
                        }
                    }
                    .awaitAll()
            }
            assertEquals(25, results.count { it.isSuccess })
            assertEquals(25, db.kitchen().recipes().first().size)
            val existing = db.kitchen().recipes().first().first()
            writer.save(existing.copy(title = "Still editable"), unlimited = false)
            assertTrue(
                db.kitchen().recipes().first().any {
                    it.id == existing.id && it.title == "Still editable"
                }
            )
            writer.save(Recipe(title = "Verified Premium creation"), unlimited = true)
            assertEquals(26, db.kitchen().recipes().first().size)
            writer.save(existing.copy(title = "Editable after expiry"), unlimited = false)
            assertTrue(db.kitchen().recipes().first().any { it.title == "Editable after expiry" })
        } finally {
            db.close()
        }
    }

    @Test
    fun crudSurvivesReopenAndRelatedDataPersists() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<DishNestApp>()
        val name = "room-verification.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, KitchenDatabase::class.java, name).build()
        val r =
            Recipe(
                title = "Persistent soup",
                ingredients = listOf(Ingredient("lentils", 200.0, "g")),
                steps = listOf("Simmer gently."),
            )
        val meal = Meal(date = "2026-10-19", slot = "Dinner", recipeId = r.id)
        val pantry = PantryItem(name = "lentils", quantity = 50.0, unit = "g")
        val timer =
            KitchenTimer(label = "Soup", deadline = 30000, remainingMillis = 10000, paused = true)
        db.kitchen().recipe(r)
        db.kitchen().meal(meal)
        db.kitchen().pantry(pantry)
        db.kitchen().timer(timer)
        db.close()
        db = Room.databaseBuilder(context, KitchenDatabase::class.java, name).build()
        assertEquals(r, db.kitchen().recipes().first().single())
        assertEquals(meal, db.kitchen().meals().first().single())
        assertEquals(timer, db.kitchen().timers().first().single())
        val shopping =
            ShoppingEngine.generate(
                    db.kitchen().meals().first(),
                    db.kitchen().recipes().first(),
                    db.kitchen().pantry().first(),
                )
                .single()
        assertEquals(150.0, shopping.quantity!!, 0.0)
        db.kitchen().shopping(shopping)
        db.kitchen().recipe(r.copy(title = "Updated soup"))
        assertEquals("Updated soup", db.kitchen().recipes().first().single().title)
        db.kitchen().shopping(shopping.copy(checked = true))
        assertTrue(db.kitchen().shopping().first().single().checked)
        db.kitchen().deleteRecipeMeals(r.id)
        db.kitchen().deleteRecipe(r.id)
        assertTrue(db.kitchen().recipes().first().isEmpty())
        assertTrue(db.kitchen().meals().first().isEmpty())
        db.close()
        context.deleteDatabase(name)
        Unit
    }
}
