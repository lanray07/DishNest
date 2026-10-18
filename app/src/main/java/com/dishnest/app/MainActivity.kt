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
        enableEdgeToEdge()
        setContent { DishNestTheme { KitchenApp() } }
    }
}
