package com.dishnest.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dishnest.app.ui.*

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        (application as DishNestApp).billing.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Amazon's SDK needs the activity lifecycle before starting licensing/IAP.
        (application as DishNestApp).billing.start()
        enableEdgeToEdge()
        setContent { DishNestTheme { KitchenApp() } }
    }
}
