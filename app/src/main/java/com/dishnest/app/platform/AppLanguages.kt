package com.dishnest.app.platform

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Only shipped translations are selectable. Language names are their native names. */
object AppLanguages {
    val choices = linkedMapOf(
        "en-GB" to "English (United Kingdom)", "en-US" to "English (United States)",
        "es" to "Español", "fr" to "Français", "de" to "Deutsch", "it" to "Italiano",
        "pt-BR" to "Português (Brasil)", "pt-PT" to "Português (Portugal)",
        "nl" to "Nederlands", "pl" to "Polski", "ar" to "العربية", "hi" to "हिन्दी",
    )

    private fun preferences(context: Context) = context.getSharedPreferences("app_language", Context.MODE_PRIVATE)

    fun selected(context: Context): String {
        val tag = if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags().substringBefore(',')
        } else preferences(context).getString("tag", "").orEmpty()
        return tag.takeIf { it in choices }.orEmpty()
    }

    /** Restore before AppCompat attaches the activity on pre-Android-13 devices. */
    fun restore(context: Context) {
        if (Build.VERSION.SDK_INT < 33) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(selected(context)))
        } else if (preferences(context).contains("tag")) {
            // One-time OS-upgrade migration; preserve an existing system selection.
            val manager = context.getSystemService(LocaleManager::class.java)
            val legacy = preferences(context).getString("tag", "").orEmpty()
            if (manager.applicationLocales.isEmpty && legacy in choices) {
                manager.applicationLocales = android.os.LocaleList.forLanguageTags(legacy)
            }
            preferences(context).edit().remove("tag").apply()
        }
    }

    fun choose(context: Context, tag: String) {
        require(tag.isEmpty() || tag in choices)
        if (Build.VERSION.SDK_INT < 33) {
            check(preferences(context).edit().putString("tag", tag).commit())
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    /** Receivers must use the preference too, including after a cold process start. */
    fun localized(context: Context): Context {
        val tag = selected(context)
        if (tag.isEmpty()) return context
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(java.util.Locale.forLanguageTag(tag))
        return context.createConfigurationContext(configuration)
    }
}
