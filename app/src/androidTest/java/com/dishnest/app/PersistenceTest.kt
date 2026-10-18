package com.dishnest.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dishnest.app.data.KitchenDatabase
import com.dishnest.app.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    @Test
    fun recipeSurvivesCloseAndReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "persistence-test.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, KitchenDatabase::class.java, name).build()
        val recipe =
            Recipe(
                title = "Persistent soup",
                ingredients = listOf(Ingredient("lentils", 200.0, "g")),
                steps = listOf("Simmer."),
            )
        db.kitchen().recipe(recipe)
        db.close()
        db = Room.databaseBuilder(context, KitchenDatabase::class.java, name).build()
        assertEquals(recipe, db.kitchen().recipes().first().single())
        db.kitchen().recipe(recipe.copy(title = "Updated soup"))
        assertEquals("Updated soup", db.kitchen().recipes().first().single().title)
        db.kitchen().deleteRecipe(recipe.id)
        assertTrue(db.kitchen().recipes().first().isEmpty())
        db.close()
        context.deleteDatabase(name)
        Unit
    }
}
