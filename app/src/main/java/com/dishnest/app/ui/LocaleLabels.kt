package com.dishnest.app.ui

import android.content.res.Resources
import com.dishnest.app.R
import com.dishnest.app.domain.Recipe

fun Resources.mealSlot(slot: String): String = when (slot) {
    "Breakfast" -> getString(R.string.slot_breakfast)
    "Lunch" -> getString(R.string.slot_lunch)
    "Dinner" -> getString(R.string.slot_dinner)
    "Snacks" -> getString(R.string.slot_snacks)
    else -> slot
}

fun Resources.duration(minutes: Int): String = getString(R.string.duration_minutes, minutes)
fun Resources.servings(count: Int): String = getString(R.string.servings_value, count)
/** Old backups store this generated suffix in English; keep the user's recipe title intact. */
fun Resources.shoppingContribution(value: String): String {
    val match = Regex("^(.*) · ([0-9]+) servings$", RegexOption.DOT_MATCHES_ALL).matchEntire(value)
        ?: return value
    val count = match.groupValues[2].toIntOrNull() ?: return value
    return match.groupValues[1] + " · " + servings(count)
}
fun Resources.recipeSummary(recipe: Recipe): String =
    "${duration(recipe.prepMinutes + recipe.cookMinutes)} · ${servings(recipe.servings)}"
