package com.dishnest.app

import android.app.Application
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.dishnest.app.data.*
import com.dishnest.app.domain.*
import com.dishnest.app.platform.TimerScheduler
import java.time.LocalDate
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private val android.content.Context.settings by preferencesDataStore("settings")

data class KitchenState(
    val recipes: List<Recipe> = emptyList(),
    val meals: List<Meal> = emptyList(),
    val pantry: List<PantryItem> = emptyList(),
    val shopping: List<ShoppingItem> = emptyList(),
    val timers: List<KitchenTimer> = emptyList(),
    val templates: List<PlanTemplate> = emptyList(),
    val demo: Boolean = false,
)

class KitchenViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DishNestApp
    private val db = app.database
    private val dao = db.kitchen()
    private val recipeWriter = RecipeWriter(db)
    private val scheduler = TimerScheduler(app)
    val message = MutableStateFlow("")
    val billing = app.billing
    val metrics = app.metrics

    fun canUsePremium(): Boolean =
        (!BuildConfig.BILLING_ENABLED && state.value.demo) || billing.hasPremium()

    private val demo = app.settings.data.map { it[booleanPreferencesKey("demo")] ?: false }
    private val base =
        combine(dao.recipes(), dao.meals(), dao.pantry(), dao.shopping(), dao.timers()) {
            r,
            m,
            p,
            s,
            t ->
            KitchenState(r, m, p, s, t)
        }
    val state =
        combine(base, dao.templates(), demo) { b, t, d -> b.copy(templates = t, demo = d) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), KitchenState())

    fun run(action: suspend () -> Unit) =
        viewModelScope.launch {
            runCatching { action() }
                .onFailure {
                    message.value = it.message ?: "Something went wrong. Please try again."
                }
        }

    fun save(r: Recipe, onSaved: () -> Unit = {}) = run {
        require(
            r.title.isNotBlank() &&
                r.servings in 1..100 &&
                r.ingredients.isNotEmpty() &&
                r.steps.isNotEmpty()
        ) {
            "Add a title, ingredients and instructions."
        }
        require(
            r.ingredients.all {
                it.name.isNotBlank() &&
                    (it.quantity == null || (it.quantity.isFinite() && it.quantity > 0))
            }
        ) {
            "Review ingredient quantities."
        }
        recipeWriter.save(r, unlimited = canUsePremium())
        metrics.record("first_recipe_saved")
        message.value = "Recipe saved"
        onSaved()
    }

    fun delete(r: Recipe) = run {
        db.withTransaction {
            dao.deleteRecipeMeals(r.id)
            dao.deleteRecipe(r.id)
            dao.templates().first().forEach { t ->
                val meals = t.meals.filter { it.recipeId != r.id }
                if (meals.isEmpty()) dao.deleteTemplate(t.id)
                else dao.template(t.copy(meals = meals))
            }
        }
        message.value = "Recipe deleted"
    }

    fun favourite(r: Recipe) = run { dao.recipe(r.copy(favourite = !r.favourite)) }

    fun meal(value: Meal) = run {
        require(value.servings in 1..100)
        db.withTransaction {
            dao.meals()
                .first()
                .filter { it.date == value.date && it.slot == value.slot }
                .forEach { dao.deleteMeal(it.id) }
            dao.meal(value)
        }
        metrics.record("first_meal_plan_created")
    }

    fun removeMeal(m: Meal) = run { dao.deleteMeal(m.id) }

    fun clearWeek(start: LocalDate) = run {
        dao.clearWeek(start.toString(), start.plusDays(6).toString())
    }

    fun smartPlan(start: LocalDate, prefs: PlanPreferences) = run {
        val generated = Planner.generate(start, state.value.recipes, state.value.pantry, prefs)
        require(generated.isNotEmpty()) { "No saved recipes match these preferences." }
        db.withTransaction {
            for (m in generated) {
                dao.meals()
                    .first()
                    .filter { it.date == m.date && it.slot == m.slot }
                    .forEach { dao.deleteMeal(it.id) }
                dao.meal(m)
            }
        }
        metrics.record("first_meal_plan_created")
        message.value = "Seven dinners planned"
    }

    fun copyPrevious(start: LocalDate) = run {
        val previous =
            state.value.meals.filter {
                it.date >= start.minusDays(7).toString() && it.date < start.toString()
            }
        require(previous.isNotEmpty()) { "There are no meals in the previous week." }
        db.withTransaction {
            dao.clearWeek(start.toString(), start.plusDays(6).toString())
            previous.forEach {
                dao.meal(
                    it.copy(id = newId(), date = LocalDate.parse(it.date).plusDays(7).toString())
                )
            }
        }
    }

    fun saveTemplate(name: String, start: LocalDate) = run {
        require(canUsePremium()) { app.getString(R.string.premium_required) }
        val meals =
            state.value.meals.filter {
                it.date >= start.toString() && it.date <= start.plusDays(6).toString()
            }
        require(meals.isNotEmpty())
        dao.template(PlanTemplate(name = name, meals = meals))
        message.value = "Template saved"
    }

    fun applyTemplate(template: PlanTemplate, start: LocalDate) = run {
        require(canUsePremium()) { app.getString(R.string.premium_required) }
        val oldStart = Planner.week(LocalDate.parse(template.meals.minOf { it.date }))
        db.withTransaction {
            dao.clearWeek(start.toString(), start.plusDays(6).toString())
            template.meals
                .filter { m -> state.value.recipes.any { it.id == m.recipeId } }
                .forEach {
                    dao.meal(
                        it.copy(
                            id = newId(),
                            date =
                                start
                                    .plusDays(
                                        java.time.temporal.ChronoUnit.DAYS.between(
                                            oldStart,
                                            LocalDate.parse(it.date),
                                        )
                                    )
                                    .toString(),
                        )
                    )
                }
        }
    }

    fun generateShopping(start: LocalDate) = run {
        val s = state.value
        val meals =
            s.meals.filter {
                it.date >= start.toString() && it.date <= start.plusDays(6).toString()
            }
        require(meals.isNotEmpty()) { "Plan a meal for this week first." }
        val items = ShoppingEngine.generate(meals, s.recipes, s.pantry)
        db.withTransaction {
            dao.clearGeneratedShopping()
            items.forEach { dao.shopping(it) }
        }
        metrics.record("shopping_list_generated")
        message.value = "Shopping list updated; pantry quantities deducted"
    }

    fun shopping(item: ShoppingItem) = run {
        require(item.name.isNotBlank())
        dao.shopping(item)
    }

    fun deleteShopping(item: ShoppingItem) = run { dao.deleteShopping(item.id) }

    fun pantry(item: PantryItem) = run {
        require(item.name.isNotBlank())
        require(item.quantity == null || (item.quantity.isFinite() && item.quantity >= 0))
        if (item.expiry.isNotBlank()) LocalDate.parse(item.expiry)
        dao.pantry(item)
    }

    fun deletePantry(item: PantryItem) = run { dao.deletePantry(item.id) }

    fun timer(label: String, seconds: Long) = run {
        require(seconds in 1..86400)
        val timer =
            KitchenTimer(
                label = label.ifBlank { "Cooking timer" },
                deadline = System.currentTimeMillis() + seconds * 1000,
                remainingMillis = seconds * 1000,
            )
        dao.timer(timer)
        scheduler.schedule(timer)
    }

    fun pause(t: KitchenTimer) = run {
        val remaining = t.remaining()
        val updated =
            if (t.paused)
                t.copy(
                    paused = false,
                    deadline = System.currentTimeMillis() + remaining,
                    notified = false,
                )
            else t.copy(paused = true, remainingMillis = remaining)
        scheduler.cancel(t.id)
        dao.timer(updated)
        scheduler.schedule(updated)
    }

    fun cancel(t: KitchenTimer) = run {
        scheduler.cancel(t.id)
        dao.deleteTimer(t.id)
    }

    fun loadDemo() = run {
        require(
            state.value.recipes.isEmpty() &&
                state.value.pantry.isEmpty() &&
                state.value.meals.isEmpty()
        ) {
            "Demo can only be loaded into an empty kitchen."
        }
        db.withTransaction {
            DemoData.recipes().forEach { dao.recipe(it) }
            DemoData.pantry().forEach { dao.pantry(it) }
            val start = Planner.week(LocalDate.now())
            (0..4).forEach { d ->
                dao.meal(
                    Meal(
                        date = start.plusDays(d.toLong()).toString(),
                        slot = "Dinner",
                        recipeId = DemoData.recipes()[d % 4].id,
                    )
                )
            }
        }
        app.settings.edit { it[booleanPreferencesKey("demo")] = true }
        message.value = "Demo kitchen loaded"
    }

    fun deleteAll() = run {
        state.value.timers.forEach { scheduler.cancel(it.id) }
        db.withTransaction {
            dao.clearRecipes()
            dao.clearMeals()
            dao.clearPantry()
            dao.clearShopping()
            dao.clearTimers()
            dao.clearTemplates()
        }
        app.settings.edit { it.clear() }
        metrics.clear()
        app.filesDir.resolve("photos").deleteRecursively()
        message.value = "All kitchen data deleted"
    }

    fun export(): String {
        val s = state.value
        return BackupCodec.encode(
            Backup(
                recipes =
                    s.recipes.map {
                        it.copy(photo = if (it.photo in bundledPhotoIds) it.photo else "")
                    },
                meals = s.meals,
                pantry = s.pantry,
                shopping = s.shopping,
                templates = s.templates,
            )
        )
    }

    fun restore(raw: String) = run {
        val backup = BackupCodec.decode(raw)
        require(
            state.value.recipes.isEmpty() &&
                state.value.pantry.isEmpty() &&
                state.value.meals.isEmpty() &&
                state.value.shopping.isEmpty() &&
                state.value.templates.isEmpty()
        ) {
            "Import requires an empty kitchen. Export existing data before deleting it."
        }
        db.withTransaction {
            backup.recipes.forEach { dao.recipe(it) }
            backup.meals.forEach { dao.meal(it) }
            backup.pantry.forEach { dao.pantry(it) }
            backup.shopping.forEach { dao.shopping(it) }
            backup.templates.forEach { dao.template(it) }
        }
        message.value = "Backup imported"
    }
}
