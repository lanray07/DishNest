package com.dishnest.app

import android.content.Context
import android.os.LocaleList
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import com.dishnest.app.platform.AppLanguages
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

class LanguageProbeActivity : AppCompatActivity() {
    override fun attachBaseContext(base: Context) {
        AppLanguages.restore(base)
        super.attachBaseContext(base)
    }
    override fun onCreate(state: android.os.Bundle?) {
        setTheme(R.style.Theme_DishNest)
        super.onCreate(state)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@Config(sdk = [28], application = DishNestApp::class)
class AppLanguagesTest {
    private val context get() = ApplicationProvider.getApplicationContext<DishNestApp>()
    @Before fun reset() {
        context.getSharedPreferences("app_language", 0).edit().clear().commit()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }
    @After fun clean() = reset()

    @Test fun choicePersistsAcrossDelegateResetAndLocalizesColdBackgroundContext() {
        val controller = Robolectric.buildActivity(LanguageProbeActivity::class.java).setup()
        val activity = controller.get()
        AppLanguages.choose(activity, "ar")
        assertEquals("ar", AppLanguages.selected(context))
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        AppLanguages.restore(context)
        assertEquals("ar", AppCompatDelegate.getApplicationLocales().toLanguageTags())
        controller.recreate()
        assertEquals("ar", controller.get().resources.configuration.locales[0].language)
        val english = android.content.res.Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.UK) }
        val background = context.createConfigurationContext(english)
        val localized = AppLanguages.localized(background)
        assertEquals(android.view.View.LAYOUT_DIRECTION_RTL, localized.resources.configuration.layoutDirection)
        assertEquals("ar", localized.resources.configuration.locales[0].language)
        assertNotEquals(background.getString(R.string.timer_ready), localized.getString(R.string.timer_ready))
        AppLanguages.choose(controller.get(), "")
        assertEquals("", AppLanguages.selected(context))
        assertSame(context, AppLanguages.localized(context))
        controller.pause().stop().destroy()
    }

    @Test fun everyChoiceHasPackagedLabelsAndUnknownTagsAreRejected() {
        val advertised = mutableSetOf<String>()
        context.resources.getXml(R.xml.app_locales).use { xml ->
            while (xml.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && xml.name == "locale")
                    advertised.add(xml.getAttributeValue("http://schemas.android.com/apk/res/android", "name"))
                xml.next()
            }
        }
        assertEquals(AppLanguages.choices.keys, advertised)
        assertEquals(12, AppLanguages.choices.size)
        for (tag in AppLanguages.choices.keys) {
            context.getSharedPreferences("app_language", 0).edit().putString("tag", tag).commit()
            val translated = AppLanguages.localized(context)
            assertTrue(translated.getString(R.string.language_title).isNotBlank())
            assertTrue(translated.getString(R.string.language_system).isNotBlank())
            assertEquals(context.resources.configuration.fontScale, translated.resources.configuration.fontScale)
        }
        try { AppLanguages.choose(context, "xx-private"); fail("Unsupported locale accepted") }
        catch (_: IllegalArgumentException) { }
        context.getSharedPreferences("app_language", 0).edit().putString("tag", "xx-private").commit()
        assertEquals("", AppLanguages.selected(context))
    }


}
