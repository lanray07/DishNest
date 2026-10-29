package com.dishnest.app

import android.app.Application
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.dishnest.app.data.*
import com.dishnest.app.domain.*
import com.dishnest.app.platform.AppLanguages
import com.dishnest.app.platform.TimerScheduler
import com.dishnest.app.ui.kitchenFailure
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
    val plans: List<MealPlan> = listOf(defaultPlan()),
    val activePlanId: String = DEFAULT_PLAN_ID,
)

class KitchenViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DishNestApp
    private val uiContext get() = AppLanguages.localized(app)
    private val db = app.database
    private val dao = db.kitchen()
    private val recipeWriter = RecipeWriter(db, com.dishnest.app.platform.RecipePhotos(app.filesDir))
    private val backups = KitchenBackups(db)
    private val mealWriter = MealWriter(db)
    private val planStore = PlanStore(db)
    private val scheduler = TimerScheduler(app)
    private val timerStore = TimerStore(db, scheduler)
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
        combine(base, dao.templates(), demo, dao.plans()) { b, t, d, saved ->
            val plans = saved.ifEmpty { listOf(defaultPlan()) }
            val active = plans.single { it.active }.id
            b.copy(templates = t, demo = d, plans = plans, activePlanId = active,
                meals = b.meals.filter { it.planId == active })
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), KitchenState())

    fun run(failureMessage: Int = R.string.error_action_retry, action: suspend () -> Unit) =
        viewModelScope.launch {
            runCatching { action() }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    message.value = uiContext.resources.kitchenFailure(it, failureMessage)
                }
        }

    fun save(r: Recipe, onSaved: () -> Unit = {}) = run {
        requireKitchen(
            r.title.isNotBlank() &&
                r.servings in 1..100 &&
                r.ingredients.isNotEmpty() &&
                r.steps.isNotEmpty(), KitchenProblem.RECIPE_FIELDS
        )
        requireKitchen(
            r.ingredients.all {
                it.name.isNotBlank() &&
                    (it.quantity == null || (it.quantity.isFinite() && it.quantity > 0))
            }, KitchenProblem.QUANTITY
        )
        recipeWriter.save(r, unlimited = canUsePremium())
        metrics.record("first_recipe_saved")
        message.value = uiContext.getString(R.string.recipe_saved)
        onSaved()
    }

    fun delete(r: Recipe) = run {
        recipeWriter.delete(r.id)
        message.value = uiContext.getString(R.string.recipe_deleted)
    }

    fun favourite(r: Recipe) = run { recipeWriter.toggleFavourite(r.id) }

    fun meal(value: Meal) = run {
        mealWriter.assign(value)
        metrics.record("first_meal_plan_created")
    }

    fun swapMeals(firstId: String, secondId: String) = run {
        mealWriter.swap(firstId, secondId)
        message.value = uiContext.getString(R.string.meals_swapped)
    }

    fun removeMeal(m: Meal) = run { dao.deleteMeal(m.id) }

    fun createPlan(name: String) = run { planStore.create(name, canUsePremium()) }
    fun selectPlan(id: String) = run { planStore.select(id) }
    fun renamePlan(id: String, name: String) = run { planStore.rename(id, name) }
    fun deletePlan(id: String) = run { planStore.delete(id) }

    fun clearWeek(start: LocalDate) = run {
        db.withTransaction { dao.clearWeek(start.toString(), start.plusDays(6).toString(), planStore.activeId()) }
    }

    fun smartPlan(start: LocalDate, prefs: PlanPreferences) = run {
        db.withTransaction {
            val planId = planStore.activeId()
            val generated = Planner.generate(start, dao.recipes().first(), dao.pantry().first(), prefs).map { it.copy(planId = planId) }
            requireKitchen(generated.isNotEmpty(), KitchenProblem.NO_MATCH)
            for (m in generated) {
                dao.meals()
                    .first()
                    .filter { it.planId == planId && it.date == m.date && it.slot == m.slot }
                    .forEach { dao.deleteMeal(it.id) }
                dao.meal(m)
            }
        }
        metrics.record("first_meal_plan_created")
        message.value = uiContext.getString(R.string.dinners_planned)
    }

    fun copyPrevious(start: LocalDate) = run {
        db.withTransaction {
            val planId = planStore.activeId()
            val previous =
                dao.meals().first().filter {
                    it.planId == planId && it.date >= start.minusDays(7).toString() && it.date < start.toString()
                }
            requireKitchen(previous.isNotEmpty(), KitchenProblem.PREVIOUS_WEEK)
            dao.clearWeek(start.toString(), start.plusDays(6).toString(), planStore.activeId())
            previous.forEach {
                dao.meal(
                    it.copy(id = newId(), date = LocalDate.parse(it.date).plusDays(7).toString())
                )
            }
        }
    }

    fun saveTemplate(name: String, start: LocalDate) = run {
        require(canUsePremium()) { uiContext.getString(R.string.premium_required) }
        requireKitchen(name.isNotBlank() && name.length <= 200, KitchenProblem.PLAN_NAME)
        db.withTransaction {
            val planId = planStore.activeId()
            val meals =
                dao.meals().first().filter {
                    it.planId == planId && it.date >= start.toString() && it.date <= start.plusDays(6).toString()
                }
            requireKitchen(meals.isNotEmpty(), KitchenProblem.EMPTY_WEEK)
            dao.template(PlanTemplate(name = name, meals = meals))
        }
        message.value = uiContext.getString(R.string.template_saved)
    }

    fun applyTemplate(template: PlanTemplate, start: LocalDate) = run {
        require(canUsePremium()) { uiContext.getString(R.string.premium_required) }
        db.withTransaction {
            val saved = (dao.templates().first().find { it.id == template.id } ?: throw KitchenFailure(KitchenProblem.MISSING_RECORD))
            val oldStart = Planner.week(LocalDate.parse(saved.meals.minOf { it.date }))
            val recipeIds = dao.recipes().first().map { it.id }.toSet()
            dao.clearWeek(start.toString(), start.plusDays(6).toString(), planStore.activeId())
            saved.meals
                .filter { m -> m.recipeId in recipeIds }
                .forEach {
                    dao.meal(
                        it.copy(
                            id = newId(),
                            planId = planStore.activeId(),
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

    fun generateShopping(start: LocalDate, selectedPlanIds: Set<String>? = null) = run {
        db.withTransaction {
            val selected = selectedPlanIds ?: setOf(planStore.activeId())
            requireKitchen(selected.isNotEmpty() && selected.all { id -> planStore.ensureDefault().any { it.id == id } }, KitchenProblem.MISSING_RECORD)
            require(selected.size == 1 || canUsePremium()) { uiContext.getString(R.string.premium_required) }
            val meals =
                dao.meals().first().filter {
                    it.planId in selected && it.date >= start.toString() && it.date <= start.plusDays(6).toString()
                }
            requireKitchen(meals.isNotEmpty(), KitchenProblem.EMPTY_WEEK)
            val items = ShoppingEngine.generate(meals, dao.recipes().first(), dao.pantry().first())
            dao.clearGeneratedShopping()
            items.forEach { dao.shopping(it) }
        }
        metrics.record("shopping_list_generated")
        message.value = uiContext.getString(R.string.shopping_updated)
    }

    fun shopping(item: ShoppingItem) = run {
        require(item.name.isNotBlank())
        dao.shopping(item)
    }

    fun deleteShopping(item: ShoppingItem) = run { dao.deleteShopping(item.id) }

    fun pantry(item: PantryItem) = run {
        require(item.name.isNotBlank())
        requireKitchen(item.quantity == null || (item.quantity.isFinite() && item.quantity >= 0), KitchenProblem.QUANTITY)
        if (item.expiry.isNotBlank()) LocalDate.parse(item.expiry)
        dao.pantry(item)
    }

    fun deletePantry(item: PantryItem) = run { dao.deletePantry(item.id) }

    fun timer(label: String, seconds: Long) = run {
        timerStore.start(label.ifBlank { uiContext.getString(R.string.timer_channel) }, seconds)
    }

    fun pause(t: KitchenTimer) = run { timerStore.toggle(t.id) }
    fun cancel(t: KitchenTimer) = run { timerStore.cancel(t.id) }

    fun loadDemo() = run {
        DemoKitchen(db).load()
        app.settings.edit { it[booleanPreferencesKey("demo")] = true }
        message.value = uiContext.getString(R.string.demo_loaded)
    }

    fun deleteAll() = run {
        db.withTransaction {
            dao.timers().first().forEach { scheduler.cancel(it.id) }
            dao.clearRecipes()
            dao.clearMeals()
            dao.clearPantry()
            dao.clearShopping()
            dao.clearTimers()
            dao.clearTemplates()
            dao.clearPlans()
            app.filesDir.resolve("photos").deleteRecursively()
        }
        app.settings.edit { it.clear() }
        metrics.clear()
        com.dishnest.app.platform.PantryReminders(app).clear()
        message.value = uiContext.getString(R.string.kitchen_deleted)
    }

    suspend fun export(): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        backups.exportJson()
    }

    suspend fun recipesForExport(): List<Recipe> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        backups.snapshot().recipes
    }

    suspend fun exportPhotos(output: java.io.OutputStream) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        PhotoBackups(com.dishnest.app.platform.RecipePhotos(app.filesDir)).write(backups.snapshot(), output)
    }

    suspend fun restorePhotos(input: java.io.InputStream) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        PhotoBackups(com.dishnest.app.platform.RecipePhotos(app.filesDir)).restore(input, backups::restore)
        message.value = uiContext.getString(R.string.backup_imported)
    }

    fun restore(raw: String) = run(R.string.error_invalid_backup) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            backups.restore(BackupCodec.decode(raw))
        }
        message.value = uiContext.getString(R.string.backup_imported)
    }
}
