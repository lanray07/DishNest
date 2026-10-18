package com.dishnest.app.platform

import android.content.Context
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Opt-in, aggregate counters only. There is deliberately no network sink. */
class LocalMetrics(context: Context) {
    private val preferences = context.getSharedPreferences("local_usage", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = preferences.getBoolean("enabled", false)
        set(value) {
            preferences
                .edit()
                .clear()
                .putBoolean("enabled", value)
                .putString("cohort", LocalDate.now().toString())
                .apply()
        }

    private val allowed =
        setOf(
            "first_recipe_saved",
            "first_meal_plan_created",
            "shopping_list_generated",
            "cooking_mode_activated",
            "voice_used",
            "recipe_import_success",
            "retained_day_7",
            "retained_day_30",
            "premium_conversion",
            "trial_conversion",
        )

    @Synchronized
    fun record(event: String) {
        if (!enabled || event !in allowed) return
        if (event.startsWith("first_") && preferences.contains(event)) return
        preferences.edit().putInt(event, preferences.getInt(event, 0) + 1).apply()
    }

    fun opened() {
        if (!enabled) return
        val cohort =
            runCatching { LocalDate.parse(preferences.getString("cohort", null)) }.getOrNull()
                ?: return
        val days = ChronoUnit.DAYS.between(cohort, LocalDate.now())
        if (days == 7L && !preferences.contains("retained_day_7")) record("retained_day_7")
        if (days == 30L && !preferences.contains("retained_day_30")) record("retained_day_30")
    }

    fun clear() = preferences.edit().clear().apply()
}
