package com.dishnest.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dishnest.app.data.*
import com.dishnest.app.domain.*
import com.dishnest.app.platform.PantryReminderPolicy
import com.google.gson.JsonParser
import java.io.File
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = DishNestApp::class)
class FeatureScopeTest {
    private val context get() = ApplicationProvider.getApplicationContext<DishNestApp>()
    private fun database() = Room.inMemoryDatabaseBuilder(context, KitchenDatabase::class.java).build()

    @Test fun migrationPreservesRecipesMealsTemplatesAndSelectsDefaultPlan() = runBlocking {
        val name = "migration-fixture-${newId()}.db"
        LegacyKitchenFixture.create(context, name, File("schemas/com.dishnest.app.data.KitchenDatabase/1.json").readText())
        val db = Room.databaseBuilder(context, KitchenDatabase::class.java, name).addMigrations(KitchenDatabase.MIGRATION_1_2).build()
        try {
            assertEquals("Original notes", db.kitchen().recipes().first().single().notes)
            assertEquals(DEFAULT_PLAN_ID, db.kitchen().meals().first().single().planId)
            assertEquals(DEFAULT_PLAN_ID, db.kitchen().templates().first().single().meals.single().planId)
            assertEquals(listOf(defaultPlan()), db.kitchen().plans().first())
            BackupCodec.decode(KitchenBackups(db).exportJson())
            Unit
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun plansOwnTheirSlotsAndDeletionCannotAffectOtherPlansOrRecipes() = runBlocking {
        val db = database()
        try {
            val plans = PlanStore(db); val writer = MealWriter(db)
            plans.ensureDefault()
            assertTrue(runCatching { plans.create("Free second plan", false) }.isFailure)
            val second = plans.create("Family", true)
            val recipe = Recipe(title = "Soup"); db.kitchen().recipe(recipe)
            val a = Meal(date = "2026-10-19", slot = "Dinner", recipeId = recipe.id)
            val b = a.copy(id = newId(), planId = second.id)
            writer.assign(a); writer.assign(b)
            assertEquals(2, db.kitchen().meals().first().size)
            assertTrue(runCatching { writer.swap(a.id, b.id) }.isFailure)
            db.kitchen().clearWeek("2026-10-19", "2026-10-25", DEFAULT_PLAN_ID)
            assertEquals(listOf(b), db.kitchen().meals().first())
            plans.rename(second.id, "Still editable after expiry")
            plans.select(DEFAULT_PLAN_ID); plans.select(second.id)
            plans.delete(second.id)
            assertEquals(DEFAULT_PLAN_ID, plans.activeId())
            assertTrue(db.kitchen().meals().first().isEmpty())
            assertEquals(recipe, db.kitchen().recipes().first().single())
            assertTrue(runCatching { plans.delete(DEFAULT_PLAN_ID) }.isFailure)
        } finally { db.close() }
    }

    @Test fun demoUsesTheSelectedPlanWhenTheDefaultPlanHasBeenDeleted(): Unit = runBlocking {
        val db = database()
        try {
            val plans = PlanStore(db)
            val family = plans.create("Family", true)
            plans.delete(DEFAULT_PLAN_ID)
            DemoKitchen(db).load()
            assertEquals(5, db.kitchen().meals().first().size)
            assertTrue(db.kitchen().meals().first().all { it.planId == family.id })
            val snapshot = KitchenBackups(db).snapshot()
            assertEquals(listOf(family), snapshot.plans)
            assertEquals(snapshot, BackupCodec.decode(BackupCodec.encode(snapshot)))
            assertTrue(runCatching { DemoKitchen(db).load() }.isFailure)
            assertEquals(snapshot, KitchenBackups(db).snapshot())
        } finally { db.close() }
    }

    @Test fun versionOneBackupsMigrateAndVersionTwoRetainsPlanSelection() {
        val r = Recipe(id = "r", title = "Soup")
        val meal = Meal(date = "2026-10-19", slot = "Dinner", recipeId = r.id)
        val backup = Backup(recipes = listOf(r), meals = listOf(meal), pantry = emptyList(), shopping = emptyList(),
            templates = listOf(PlanTemplate(name = "Week", meals = listOf(meal))))
        val legacy = JsonParser.parseString(BackupCodec.encode(backup)).asJsonObject
        legacy.addProperty("version", 1); legacy.remove("plans")
        legacy["meals"].asJsonArray.forEach { it.asJsonObject.remove("planId") }
        legacy["templates"].asJsonArray.forEach { it.asJsonObject["meals"].asJsonArray.forEach { m -> m.asJsonObject.remove("planId") } }
        assertEquals(backup, BackupCodec.decode(legacy.toString()))
        val second = MealPlan(name = "Family", active = true)
        val multiple = backup.copy(plans = listOf(defaultPlan().copy(active = false), second),
            meals = listOf(meal, meal.copy(id = newId(), planId = second.id)))
        assertEquals(multiple, BackupCodec.decode(BackupCodec.encode(multiple)))
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(BackupCodec.encode(multiple.copy(plans = listOf(second)))) }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(BackupCodec.encode(multiple.copy(plans = listOf(defaultPlan(), second)))) }
    }

    @Test fun timerActionsUseCurrentStateAndNeverResurrectDeletedOrAlreadyHandledTimers() = runBlocking {
        val db = database()
        val events = mutableListOf<String>()
        val alarms = object : TimerAlarms {
            override fun schedule(timer: KitchenTimer) { events += "schedule:${timer.id}" }
            override fun cancel(id: String) { events += "cancel:$id" }
        }
        try {
            val store = TimerStore(db, alarms)
            val timer = store.start("Soup", 10, 1000)
            store.toggle(timer.id, 4000)
            assertEquals(7000L, db.kitchen().timers().first().single().remaining(99999))
            store.toggle(timer.id, 8000)
            assertEquals(15000L, db.kitchen().timers().first().single().deadline)
            var notifications = 0
            assertFalse(store.expire(timer.id, { notifications++; true }, 12000))
            assertTrue(store.expire(timer.id, { notifications++; true }, 15000))
            assertFalse(store.expire(timer.id, { notifications++; true }, 15001))
            assertEquals(1, notifications)
            store.toggle(timer.id, 0) // A wall-clock rewind must not revive an already handled timer.
            assertTrue(db.kitchen().timers().first().single().notified)
            store.cancel(timer.id); store.toggle(timer.id, 15002)
            assertTrue(db.kitchen().timers().first().isEmpty())
            assertFalse(store.expire(timer.id, { notifications++; true }, 15003))
        } finally { db.close() }
    }

    @Test fun pantryRemindersRespectCheckDatesAndLocalTimeAcrossDaylightSavingChanges() {
        val today = LocalDate.of(2026, 10, 24)
        assertFalse(PantryReminderPolicy.due(listOf(PantryItem(name = "Rice")), today))
        assertFalse(PantryReminderPolicy.due(listOf(PantryItem(name = "Rice", expiry = "not-a-date")), today))
        assertTrue(PantryReminderPolicy.due(listOf(PantryItem(name = "Milk", expiry = today.plusDays(3).toString())), today))
        assertFalse(PantryReminderPolicy.due(listOf(PantryItem(name = "Milk", expiry = today.plusDays(4).toString())), today))
        val now = today.atTime(20, 0).atZone(ZoneId.of("Europe/London"))
        val next = Instant.ofEpochMilli(PantryReminderPolicy.next(now)).atZone(now.zone)
        assertEquals(today.plusDays(1), next.toLocalDate()); assertEquals(9, next.hour)
        assertEquals(14L, Duration.between(now, next).toHours())
    }
}
