package com.dishnest.app

import android.app.Application
import androidx.room.Room
import com.dishnest.app.data.KitchenDatabase
import com.dishnest.app.platform.AmazonBilling
import com.dishnest.app.platform.LocalMetrics
import com.dishnest.app.platform.RecipePhotos
import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class DishNestApp : Application() {
    val database by lazy {
        Room.databaseBuilder(this, KitchenDatabase::class.java, "dishnest.db").build()
    }
    val billing by lazy { AmazonBilling(this) }
    val metrics by lazy { LocalMetrics(this) }

    override fun onCreate() {
        super.onCreate()
        billing.start()
        metrics.opened()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            // Only abandoned drafts older than seven days are eligible; saved/shared photos survive.
            runCatching {
                database.withTransaction {
                    RecipePhotos(filesDir).prune(database.kitchen().recipes().first().map { it.photo })
                }
            }
        }
    }
}
