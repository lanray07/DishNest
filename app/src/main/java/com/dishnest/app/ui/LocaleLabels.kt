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
fun Resources.recipeSummary(recipe: Recipe): String =
    "${duration(recipe.prepMinutes + recipe.cookMinutes)} · ${servings(recipe.servings)}"
