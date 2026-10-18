package com.dishnest.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dishnest.app.data.*
import com.dishnest.app.domain.*
import com.dishnest.app.platform.RecipePhotos
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = DishNestApp::class)
class ReadinessDataTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun database() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<DishNestApp>(), KitchenDatabase::class.java
    ).build()

    private fun backup(recipes: List<Recipe>) = Backup(
        recipes = recipes, meals = emptyList(), pantry = emptyList(), shopping = emptyList()
    )

    private fun image(): ByteArray {
        val bitmap = Bitmap.createBitmap(16, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.GREEN)
        return ByteArrayOutputStream().also {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
            bitmap.recycle()
        }.toByteArray()
    }

    private fun archive(entries: List<Pair<String, ByteArray>>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
        }
    }.toByteArray()

    @Test fun simultaneousRestoresCannotOverwriteAnExistingKitchen() = runBlocking {
        val db = database()
        try {
            val service = KitchenBackups(db)
            val results = coroutineScope {
                (1..8).map { index -> async(Dispatchers.Default) {
                    runCatching { service.restore(backup(listOf(Recipe(id = "r$index", title = "Kitchen $index")))) }
                } }.awaitAll()
            }
            assertEquals(1, results.count { it.isSuccess })
            assertEquals(1, service.snapshot().recipes.size)
            assertEquals(service.snapshot(), BackupCodec.decode(service.exportJson()))
        } finally { db.close() }
    }

    @Test fun sharedPhotosSurviveUntilTheLastReferenceIsReplacedOrDeleted() = runBlocking {
        val db = database()
        val photos = RecipePhotos(temporary.root)
        try {
            val shared = photos.import(image())
            val first = Recipe(id = "first", title = "First", photo = shared)
            val second = first.copy(id = "second", title = "Second")
            val writer = RecipeWriter(db, photos)
            writer.save(first, false); writer.save(second, false)
            val meal = Meal(date = "2026-10-19", slot = "Dinner", recipeId = first.id)
            db.kitchen().meal(meal)
            db.kitchen().template(PlanTemplate(name = "Saved", meals = listOf(meal)))
            writer.delete(first.id)
            writer.toggleFavourite(first.id)
            assertEquals(listOf(second.id), db.kitchen().recipes().first().map { it.id })
            assertTrue(File(shared).exists())
            assertTrue(db.kitchen().meals().first().isEmpty())
            assertTrue(db.kitchen().templates().first().isEmpty())
            val replacement = photos.import(image())
            writer.save(second.copy(photo = replacement), false)
            assertFalse(File(shared).exists())
            assertTrue(File(replacement).exists())
            writer.delete(second.id)
            assertFalse(File(replacement).exists())
        } finally { db.close() }
    }

    @Test fun abandonedDraftCleanupPreservesRecentAndReferencedPhotosAndOutsideFiles() {
        val photos = RecipePhotos(temporary.root)
        val recent = photos.import(image())
        val abandoned = File(photos.import(image())).apply { setLastModified(1) }
        val saved = File(photos.import(image())).apply { setLastModified(1) }
        val outside = temporary.newFile("${newId()}.jpg")
        val unrelated = File(photos.directory, "user-document.txt").apply { writeText("keep"); setLastModified(1) }
        photos.prune(listOf(saved.absolutePath))
        photos.removeIfUnused(outside.absolutePath, emptyList())
        assertTrue(File(recent).exists()); assertTrue(saved.exists())
        assertFalse(abandoned.exists()); assertTrue(outside.exists()); assertTrue(unrelated.exists())
    }

    @Test fun photoArchiveRoundTripRetainsSharedPhotosAndKitchenData() = runBlocking {
        val source = RecipePhotos(temporary.newFolder("source"))
        val target = RecipePhotos(temporary.newFolder("target"))
        val photo = source.import(image())
        val first = Recipe(id = "one", title = "Soup", photo = photo)
        val meal = Meal(date = "2026-10-19", slot = "Dinner", recipeId = first.id, servings = 4)
        val original = backup(listOf(first, first.copy(id = "two"))).copy(
            meals = listOf(meal), pantry = listOf(PantryItem(name = "rice", quantity = 200.0, unit = "g")),
            templates = listOf(PlanTemplate(name = "Week", meals = listOf(meal)))
        )
        val output = ByteArrayOutputStream()
        PhotoBackups(source).write(original, output)
        var restored: Backup? = null
        PhotoBackups(target).restore(ByteArrayInputStream(output.toByteArray())) { restored = it }
        val actual = requireNotNull(restored)
        assertEquals(original.meals, actual.meals); assertEquals(original.pantry, actual.pantry)
        assertEquals(original.templates, actual.templates)
        assertEquals(1, actual.recipes.map { it.photo }.distinct().size)
        assertNotEquals(photo, actual.recipes.first().photo)
        assertEquals(1, target.directory.listFiles()!!.size)
        val bitmap = BitmapFactory.decodeFile(actual.recipes.first().photo)
        assertEquals(16, bitmap.width); assertEquals(8, bitmap.height); bitmap.recycle()
    }

    @Test fun failedDatabaseRestoreRemovesOnlyItsNewlyStagedPhotos() = runBlocking {
        val photos = RecipePhotos(temporary.root)
        val existing = photos.import(image())
        val raw = archive(listOf(
            "photos/0.jpg" to image(),
            "kitchen.json" to BackupCodec.encode(backup(listOf(Recipe(title = "Soup", photo = "photos/0.jpg")))).toByteArray()
        ))
        val result = runCatching {
            PhotoBackups(photos).restore(ByteArrayInputStream(raw)) { error("Kitchen is not empty") }
        }
        assertTrue(result.isFailure)
        assertEquals(listOf(existing), photos.directory.listFiles()!!.map { it.absolutePath })
    }

    @Test fun maliciousAndIncompleteArchivesCannotWriteOutsidePhotosOrCommit() = runBlocking {
        val photos = RecipePhotos(temporary.root)
        val json = BackupCodec.encode(backup(listOf(Recipe(title = "Soup", photo = "photos/0.jpg")))).toByteArray()
        val cases = listOf(
            archive(listOf("../outside.txt" to byteArrayOf(1))),
            archive(listOf("kitchen.json" to json)),
            archive(listOf("photos/0.jpg" to image(), "photos/1.jpg" to byteArrayOf(1, 2))),
            archive(listOf("photos/0.jpg" to image(), "kitchen.json" to "{}".toByteArray())),
            archive(listOf("photos/0.jpg" to ByteArray(12_000_001))),
            archive(listOf("photos/0.jpg" to image(), "kitchen.json" to BackupCodec.encode(backup(emptyList())).toByteArray())),
        )
        cases.forEach { raw ->
            var committed = false
            assertTrue(runCatching { PhotoBackups(photos).restore(ByteArrayInputStream(raw)) { committed = true } }.isFailure)
            assertFalse(committed)
            assertTrue(photos.directory.listFiles().orEmpty().isEmpty())
            assertFalse(File(temporary.root.parentFile, "outside.txt").exists())
        }
    }

    @Test fun swapUsesPersistedMealsPreservesServingsAndRejectsStaleSelections() = runBlocking {
        val db = database()
        try {
            val writer = MealWriter(db)
            val r1 = Recipe(id = "r1"); val r2 = Recipe(id = "r2")
            db.kitchen().recipe(r1); db.kitchen().recipe(r2)
            val a = Meal(date = "2026-10-19", slot = "Dinner", recipeId = r1.id, servings = 2)
            val b = Meal(date = "2026-10-21", slot = "Lunch", recipeId = r2.id, servings = 5)
            writer.assign(a); writer.assign(b); writer.swap(a.id, b.id)
            val saved = db.kitchen().meals().first().associateBy { it.id }
            assertEquals(a.copy(date = b.date, slot = b.slot), saved[a.id])
            assertEquals(b.copy(date = a.date, slot = a.slot), saved[b.id])
            db.kitchen().deleteMeal(b.id)
            assertTrue(runCatching { writer.swap(a.id, b.id) }.isFailure)
            assertEquals(listOf(saved[a.id]), db.kitchen().meals().first())
            db.kitchen().deleteRecipe(r2.id)
            assertTrue(runCatching { writer.assign(b) }.isFailure)
        } finally { db.close() }
    }

    @Test fun templateValidationRejectsOverlappingSlotsAndMultipleWeeks() {
        val recipe = Recipe(id = "recipe", title = "Soup")
        val meal = Meal(date = "2026-10-19", slot = "Dinner", recipeId = recipe.id)
        listOf(meal.copy(id = newId()), meal.copy(id = newId(), date = "2026-10-26")).forEach { invalid ->
            val data = backup(listOf(recipe)).copy(templates = listOf(PlanTemplate(name = "Invalid", meals = listOf(meal, invalid))))
            assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(BackupCodec.encode(data)) }
        }
    }
}
