package com.dishnest.app.platform

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.Clock
import java.time.temporal.ChronoUnit

/** Opt-in, aggregate counters only. There is deliberately no network sink. */
class LocalMetrics(context: Context, private val clock: Clock = Clock.systemDefaultZone()) {
    companion object { private val lock = Any() }
    private val preferences = context.getSharedPreferences("local_usage", Context.MODE_PRIVATE)
    private fun readEnabled() = runCatching { preferences.getBoolean("enabled", false) }.getOrDefault(false)
    private val enabledChanges = MutableStateFlow(readEnabled())
    val enabledState = enabledChanges.asStateFlow()
    // Keep a strong reference: SharedPreferences itself holds listeners weakly.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == "enabled") enabledChanges.value = readEnabled()
    }
    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
        enabledChanges.value = readEnabled()
    }

    var enabled: Boolean
        get() = readEnabled()
        set(value) = synchronized(lock) {
            if (value && enabled) {
                enabledChanges.value = true
                return@synchronized
            }
            val edit = preferences.edit().clear().putBoolean("enabled", value)
            if (value) edit.putString("cohort", LocalDate.now(clock).toString())
            edit.apply()
            enabledChanges.value = value
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

    fun record(event: String) = synchronized(lock) {
        if (!enabled || event !in allowed) return@synchronized
        val once = event.startsWith("first_") || event.startsWith("retained_") || event == "premium_conversion"
        if (once && preferences.contains(event)) return@synchronized
        val count = runCatching { preferences.getInt(event, 0) }.getOrDefault(0).coerceAtLeast(0)
        preferences.edit().putInt(event, if (count == Int.MAX_VALUE) count else count + 1).apply()
    }

    fun opened() = synchronized(lock) {
        if (!enabled) return@synchronized
        val cohort = runCatching { LocalDate.parse(preferences.getString("cohort", null)) }.getOrNull()
            ?: return@synchronized
        val days = ChronoUnit.DAYS.between(cohort, LocalDate.now(clock))
        if (days == 7L) record("retained_day_7")
        if (days == 30L) record("retained_day_30")
    }

    fun clear() = synchronized(lock) {
        preferences.edit().clear().apply()
        enabledChanges.value = false
    }
}
