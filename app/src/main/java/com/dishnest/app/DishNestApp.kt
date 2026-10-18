package com.dishnest.app

import android.app.Application
import androidx.room.Room
import com.dishnest.app.data.KitchenDatabase
import com.dishnest.app.platform.AmazonBilling
import com.dishnest.app.platform.LocalMetrics

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
    }
}
