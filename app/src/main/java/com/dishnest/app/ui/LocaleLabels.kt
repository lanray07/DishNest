package com.dishnest.app.ui

import android.content.res.Resources
import com.dishnest.app.R
import com.dishnest.app.domain.Recipe
import com.dishnest.app.domain.Ingredients

fun Resources.mealSlot(slot: String): String = when (slot) {
    "Breakfast" -> getString(R.string.slot_breakfast)
    "Lunch" -> getString(R.string.slot_lunch)
    "Dinner" -> getString(R.string.slot_dinner)
    "Snacks" -> getString(R.string.slot_snacks)
    else -> slot
}

fun Resources.duration(minutes: Int): String = getString(R.string.duration_minutes, minutes)
fun Resources.servings(count: Int): String = getString(R.string.servings_value, count)

// Stored canonical values and user-authored labels are never rewritten by a locale change.
fun Resources.shoppingCategory(value: String): String = when (value) {
    "Meat & fish" -> getString(R.string.category_meat)
    "Dairy & eggs" -> getString(R.string.category_dairy)
    "Fruit & vegetables" -> getString(R.string.category_produce)
    "Cupboard" -> getString(R.string.category_cupboard)
    "Other" -> getString(R.string.category_other)
    else -> value
}

fun Resources.difficulty(value: String): String = when (value) {
    "Easy" -> getString(R.string.difficulty_easy)
    "Medium" -> getString(R.string.difficulty_medium)
    "Hard" -> getString(R.string.difficulty_hard)
    else -> value
}

fun Resources.amount(quantity: Double?, unit: String, canonicalUnit: Boolean = false): String {
    if (quantity == null) return getString(R.string.amount_unspecified)
    val label = when (Ingredients.unit(unit)) {
        "g" -> R.string.unit_g
        "kg" -> R.string.unit_kg
        "mg" -> R.string.unit_mg
        "ml" -> R.string.unit_ml
        "l" -> R.string.unit_l
        "tsp" -> R.string.unit_tsp
        "tbsp" -> R.string.unit_tbsp
        "oz" -> R.string.unit_oz
        "lb" -> R.string.unit_lb
        else -> null
    }
    val number = java.text.NumberFormat.getNumberInstance(configuration.locales[0]).apply {
        maximumFractionDigits = 2
        isGroupingUsed = false
    }.format(quantity)
    // Text exports retain parseable unit codes; only surrounding authored headings are translated.
    return "$number ${if (canonicalUnit) unit else label?.let { getString(it) } ?: unit}".trim()
}
/** Old backups store this generated suffix in English; keep the user's recipe title intact. */
fun Resources.shoppingContribution(value: String): String {
    val match = Regex("^(.*) · ([0-9]+) servings$", RegexOption.DOT_MATCHES_ALL).matchEntire(value)
        ?: return value
    val count = match.groupValues[2].toIntOrNull() ?: return value
    return match.groupValues[1] + " · " + servings(count)
}
fun Resources.recipeSummary(recipe: Recipe): String =
    "${duration(recipe.prepMinutes + recipe.cookMinutes)} · ${servings(recipe.servings)}"
