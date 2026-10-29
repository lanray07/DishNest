package com.dishnest.app

import android.os.Bundle
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.dishnest.app.platform.AppLanguages
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dishnest.app.ui.*

class MainActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        AppLanguages.restore(newBase)
        super.attachBaseContext(newBase)
    }

    override fun onResume() {
        super.onResume()
        (application as DishNestApp).billing.refresh()
        (application as DishNestApp).metrics.opened()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Amazon's SDK needs the activity lifecycle before starting licensing/IAP.
        (application as DishNestApp).billing.start()
        enableEdgeToEdge()
        setContent { DishNestTheme { KitchenApp() } }
    }
}
