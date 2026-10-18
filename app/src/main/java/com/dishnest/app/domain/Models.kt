package com.dishnest.app.domain

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Index
import java.time.LocalDate
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

val bundledPhotoIds = setOf("demo", "demo-pasta", "demo-soup", "demo-oats")

data class Ingredient(
    val name: String,
    val quantity: Double? = null,
    val unit: String = "",
    val category: String = "Other",
)

@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey val id: String = newId(),
    val title: String = "",
    val description: String = "",
    val servings: Int = 2,
    val prepMinutes: Int = 0,
    val cookMinutes: Int = 0,
    val difficulty: String = "Easy",
    val cuisine: String = "",
    val tags: List<String> = emptyList(),
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<String> = emptyList(),
    val notes: String = "",
    val sourceUrl: String = "",
    val photo: String = "",
    val favourite: Boolean = false,
    val collection: String = "Try Soon",
    val updatedAt: Long = System.currentTimeMillis(),
)

const val DEFAULT_PLAN_ID = "default"

@Entity(tableName = "plans")
data class MealPlan(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val active: Boolean = false,
)

fun defaultPlan() = MealPlan(DEFAULT_PLAN_ID, "My meal plan", true)

@Entity(tableName = "meals", indices = [Index(value = ["planId", "date", "slot"], unique = true)])
data class Meal(
    @PrimaryKey val id: String = newId(),
    val date: String,
    val slot: String,
    val recipeId: String,
    val servings: Int = 2,
    @ColumnInfo(defaultValue = "'default'") val planId: String = DEFAULT_PLAN_ID,
)

@Entity(tableName = "pantry")
data class PantryItem(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val quantity: Double? = null,
    val unit: String = "",
    val expiry: String = "",
)

@Entity(tableName = "shopping")
data class ShoppingItem(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val quantity: Double? = null,
    val unit: String = "",
    val category: String = "Other",
    val checked: Boolean = false,
    val contributions: List<String> = emptyList(),
    val manual: Boolean = true,
)

@Entity(tableName = "timers")
data class KitchenTimer(
    @PrimaryKey val id: String = newId(),
    val label: String,
    val deadline: Long,
    val remainingMillis: Long,
    val paused: Boolean = false,
    val notified: Boolean = false,
) {
    fun remaining(now: Long = System.currentTimeMillis()) =
        if (paused) remainingMillis else (deadline - now).coerceAtLeast(0)
}

@Entity(tableName = "templates")
data class PlanTemplate(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val meals: List<Meal>,
)

data class Backup(
    val version: Int = 2,
    val recipes: List<Recipe>,
    val meals: List<Meal>,
    val pantry: List<PantryItem>,
    val shopping: List<ShoppingItem>,
    val templates: List<PlanTemplate> = emptyList(),
    val plans: List<MealPlan> = listOf(defaultPlan()),
)

data class PlanPreferences(
    val household: Int = 2,
    val maximumMinutes: Int = 60,
    val cuisine: String = "",
    val dietaryTag: String = "",
)

data class RecipeMatch(val recipe: Recipe, val available: Int, val missing: List<String>)

object Ingredients {
    private data class UnitRule(val family: String, val factor: Double, val base: String)

    private val rules =
        mapOf(
            "g" to UnitRule("mass", 1.0, "g"),
            "kg" to UnitRule("mass", 1000.0, "g"),
            "mg" to UnitRule("mass", 0.001, "g"),
            "oz" to UnitRule("mass", 28.349523125, "g"),
            "lb" to UnitRule("mass", 453.59237, "g"),
            "ml" to UnitRule("volume", 1.0, "ml"),
            "l" to UnitRule("volume", 1000.0, "ml"),
            "tsp" to UnitRule("volume", 5.0, "ml"),
            "tbsp" to UnitRule("volume", 15.0, "ml"),
            "" to UnitRule("count", 1.0, ""),
        )

    // Cups are deliberately not converted: a US cup and a metric cup are different.
    fun name(raw: String) = raw.trim().lowercase().replace(Regex("\\s+"), " ")

    fun unit(raw: String): String =
        when (val u = raw.trim().lowercase()) {
            "grams",
            "gram" -> "g"
            "kilogram",
            "kilograms" -> "kg"
            "millilitres",
            "milliliters" -> "ml"
            "litres",
            "liters",
            "litre",
            "liter" -> "l"
            "teaspoon",
            "teaspoons" -> "tsp"
            "tablespoon",
            "tablespoons" -> "tbsp"
            "ounce",
            "ounces" -> "oz"
            "pound",
            "pounds" -> "lb"
            else -> u
        }

    fun compatible(a: String, b: String): Boolean {
        val x = unit(a)
        val y = unit(b)
        return x == y || (rules[x]?.family != null && rules[x]?.family == rules[y]?.family)
    }

    fun convert(q: Double, from: String, to: String): Double? {
        val a = unit(from)
        val b = unit(to)
        if (a == b) return q
        if (!compatible(a, b)) return null
        return q * (rules[a]?.factor ?: return null) / (rules[b]?.factor ?: return null)
    }

    fun parse(line: String): Ingredient {
        val clean =
            line
                .trim()
                .replace(Regex("(\\d)([½¼¾])"), "$1 $2")
                .replace("½", "1/2")
                .replace("¼", "1/4")
                .replace("¾", "3/4")
                .removePrefix("•")
                .trim()
        // A comma followed by three digits is ambiguous without a source locale.
        if (Regex("^\\d+,\\d{3}(?:\\s|$)").containsMatchIn(clean))
            return Ingredient(clean, category = category(clean))
        val match =
            Regex("^(\\d+(?:[.,]\\d+)?(?:\\s+\\d+/\\d+)?|\\d+/\\d+)\\s+(.*)$").find(clean)
                ?: return Ingredient(clean, category = category(clean))
        val q =
            match.groupValues[1].split(' ').sumOf { part ->
                if ('/' in part) {
                    val f = part.split('/')
                    (f[0].toDoubleOrNull() ?: 0.0) / (f[1].toDoubleOrNull() ?: 1.0)
                } else part.replace(',', '.').toDoubleOrNull() ?: 0.0
            }
        val rest = match.groupValues[2]
        val head = rest.substringBefore(' ')
        val u = unit(head)
        val known =
            rules.containsKey(u) ||
                u in listOf("cup", "cups", "clove", "cloves", "can", "cans", "pinch", "bunch")
        val n = if (known && ' ' in rest) rest.substringAfter(' ') else rest
        return Ingredient(
            n,
            q.takeIf { it.isFinite() && it > 0 },
            if (known) u else "",
            category(n),
        )
    }

    fun category(n: String): String =
        when {
            Regex("chicken|beef|salmon|fish|pork", RegexOption.IGNORE_CASE).containsMatchIn(n) ->
                "Meat & fish"
            Regex("milk|cheese|yogurt|butter|egg", RegexOption.IGNORE_CASE).containsMatchIn(n) ->
                "Dairy & eggs"
            Regex(
                    "tomato|lemon|onion|garlic|spinach|pepper|carrot|courgette|parsley|potato|broccoli",
                    RegexOption.IGNORE_CASE,
                )
                .containsMatchIn(n) -> "Fruit & vegetables"
            Regex("rice|pasta|flour|oil|salt|chickpea|lentil|oat", RegexOption.IGNORE_CASE)
                .containsMatchIn(n) -> "Cupboard"
            else -> "Other"
        }

    fun display(q: Double?, u: String = ""): String =
        if (q == null) "Amount not specified"
        else "${java.text.DecimalFormat("0.##").format(q)} $u".trim()
}

object ShoppingEngine {
    fun generate(
        meals: List<Meal>,
        recipes: List<Recipe>,
        pantry: List<PantryItem>,
    ): List<ShoppingItem> {
        val result = mutableListOf<ShoppingItem>()
        for (m in meals) {
            val r = recipes.find { it.id == m.recipeId } ?: continue
            for (i in r.ingredients) {
                val qty = i.quantity?.times(m.servings.toDouble() / r.servings.coerceAtLeast(1))
                val index =
                    result.indexOfFirst {
                        Ingredients.name(it.name) == Ingredients.name(i.name) &&
                            it.quantity != null &&
                            qty != null &&
                            Ingredients.compatible(it.unit, i.unit)
                    }
                if (index < 0)
                    result.add(
                        ShoppingItem(
                            name = i.name,
                            quantity = qty,
                            unit = i.unit,
                            category = i.category,
                            contributions = listOf("${r.title} · ${m.servings} servings"),
                            manual = false,
                        )
                    )
                else {
                    val old = result[index]
                    result[index] =
                        old.copy(
                            quantity =
                                old.quantity!! + Ingredients.convert(qty!!, i.unit, old.unit)!!,
                            contributions =
                                old.contributions + "${r.title} · ${m.servings} servings",
                        )
                }
            }
        }
        // Deduct each pantry quantity once, including when an item has multiple unknown amounts.
        for (p in pantry) {
            var remaining = p.quantity ?: continue
            for (index in result.indices) {
                val item = result[index]
                if (
                    Ingredients.name(item.name) != Ingredients.name(p.name) || item.quantity == null
                )
                    continue
                val available = Ingredients.convert(remaining, p.unit, item.unit) ?: continue
                val used = minOf(item.quantity, available)
                result[index] = item.copy(quantity = (item.quantity - used).coerceAtLeast(0.0))
                remaining -= Ingredients.convert(used, item.unit, p.unit) ?: 0.0
            }
        }
        return result
            .filter { it.quantity == null || it.quantity > 0.000001 }
            .sortedWith(compareBy({ it.category }, { it.name }))
    }

    fun suggestions(recipes: List<Recipe>, pantry: List<PantryItem>): List<RecipeMatch> =
        recipes
            .map { r ->
                val missing =
                    r.ingredients
                        .filter { i ->
                            pantry.none { p ->
                                Ingredients.name(p.name) == Ingredients.name(i.name) &&
                                    p.quantity?.let { it > 0 } != false
                            }
                        }
                        .map { it.name }
                RecipeMatch(r, r.ingredients.size - missing.size, missing)
            }
            .sortedWith(
                compareByDescending<RecipeMatch> {
                        it.available.toDouble() / it.recipe.ingredients.size.coerceAtLeast(1)
                    }
                    .thenBy { it.recipe.title }
            )
}

object Planner {
    val slots = listOf("Breakfast", "Lunch", "Dinner", "Snacks")

    fun week(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

    fun generate(
        start: LocalDate,
        recipes: List<Recipe>,
        pantry: List<PantryItem>,
        prefs: PlanPreferences,
    ): List<Meal> {
        val allowed =
            recipes.filter {
                it.prepMinutes + it.cookMinutes <= prefs.maximumMinutes &&
                    (prefs.cuisine.isBlank() || it.cuisine.equals(prefs.cuisine, true)) &&
                    (prefs.dietaryTag.isBlank() ||
                        it.tags.any { t -> t.equals(prefs.dietaryTag, true) })
            }
        val ranked = ShoppingEngine.suggestions(allowed, pantry).map { it.recipe }
        if (ranked.isEmpty()) return emptyList()
        return (0..6).map { d ->
            Meal(
                date = start.plusDays(d.toLong()).toString(),
                slot = "Dinner",
                recipeId = ranked[d % ranked.size].id,
                servings = prefs.household.coerceIn(1, 50),
            )
        }
    }
}
