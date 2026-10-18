package com.dishnest.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dishnest.app.ui.DishNestTheme
import com.dishnest.app.ui.KitchenApp
import java.util.Locale

/** QA-only Android context, so native dialog windows inherit the tested configuration too. */
class QaConfigurationActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val locale = Locale.forLanguageTag("ar")
        val config = Configuration(newBase.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
            fontScale = 1.5f
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(BuildConfig.APPLICATION_ID == "com.dishnest.app.qa")
        enableEdgeToEdge()
        setContent { DishNestTheme { KitchenApp() } }
    }
}
