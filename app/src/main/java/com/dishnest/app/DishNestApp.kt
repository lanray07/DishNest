package com.dishnest.app

import android.app.Application
import androidx.room.Room
import com.dishnest.app.data.KitchenDatabase
import com.dishnest.app.platform.AmazonBilling
import com.dishnest.app.platform.LocalMetrics
import com.dishnest.app.platform.RecipePhotos
import com.dishnest.app.platform.PantryReminders
import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class DishNestApp : Application() {
    val database by lazy {
        Room.databaseBuilder(this, KitchenDatabase::class.java, "dishnest.db")
            .addMigrations(KitchenDatabase.MIGRATION_1_2).build()
    }
    val billing by lazy { AmazonBilling(this) }
    val metrics by lazy { LocalMetrics(this) }

    override fun onCreate() {
        super.onCreate()
        metrics.opened()
        PantryReminders(this).reschedule()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            // Only abandoned drafts older than seven days are eligible; saved/shared photos survive.
            runCatching {
                cacheDir.listFiles()?.filter { it.name.startsWith("ocr-input-") &&
                    System.currentTimeMillis() - it.lastModified() > 86400_000L }?.forEach { it.delete() }
                database.withTransaction {
                    RecipePhotos(filesDir).prune(database.kitchen().recipes().first().map { it.photo })
                }
            }
        }
    }
}
