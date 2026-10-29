package com.dishnest.app

import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import com.dishnest.app.platform.AppLanguages
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@Config(sdk = [35], application = DishNestApp::class)
class AppLanguagesSystemTest {
    private val context get() = ApplicationProvider.getApplicationContext<DishNestApp>()
    @Test fun android13UsesSystemSelectionAndPreservesItDuringMigration() {
        val manager = context.getSystemService(android.app.LocaleManager::class.java)
        manager.applicationLocales = LocaleList.forLanguageTags("de")
        context.getSharedPreferences("app_language", 0).edit().putString("tag", "fr").commit()
        AppLanguages.restore(context)
        assertEquals("de", AppLanguages.selected(context))
        assertFalse(context.getSharedPreferences("app_language", 0).contains("tag"))
        manager.applicationLocales = LocaleList.getEmptyLocaleList()
        context.getSharedPreferences("app_language", 0).edit().putString("tag", "ar").commit()
        AppLanguages.restore(context)
        assertEquals("ar", AppLanguages.selected(context))
    }
}
