package com.dishnest.app.data

import androidx.room.*
import com.dishnest.app.domain.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    private val gson = Gson()

    @TypeConverter fun strings(value: List<String>): String = gson.toJson(value)

    @TypeConverter
    fun strings(value: String): List<String> =
        gson.fromJson(value, object : TypeToken<List<String>>() {}.type)

    @TypeConverter fun ingredients(value: List<Ingredient>): String = gson.toJson(value)

    @TypeConverter
    fun ingredients(value: String): List<Ingredient> =
        gson.fromJson(value, object : TypeToken<List<Ingredient>>() {}.type)

    @TypeConverter fun meals(value: List<Meal>): String = gson.toJson(value)

    @TypeConverter
    fun meals(value: String): List<Meal> =
        gson.fromJson(value, object : TypeToken<List<Meal>>() {}.type)
}

@Dao
interface KitchenDao {
    @Query("SELECT * FROM plans ORDER BY rowid") fun plans(): Flow<List<MealPlan>>
    @Upsert suspend fun plan(value: MealPlan)
    @Query("DELETE FROM plans WHERE id=:id") suspend fun deletePlan(id: String)
    @Query("DELETE FROM meals WHERE planId=:id") suspend fun deletePlanMeals(id: String)
    @Query("DELETE FROM plans") suspend fun clearPlans()
    @Query("SELECT * FROM recipes ORDER BY updatedAt DESC") fun recipes(): Flow<List<Recipe>>

    @Query("SELECT * FROM meals ORDER BY date, slot") fun meals(): Flow<List<Meal>>

    @Query("SELECT * FROM pantry ORDER BY CASE WHEN expiry='' THEN '9999' ELSE expiry END")
    fun pantry(): Flow<List<PantryItem>>

    @Query("SELECT * FROM shopping ORDER BY category, name")
    fun shopping(): Flow<List<ShoppingItem>>

    @Query("SELECT * FROM timers") fun timers(): Flow<List<KitchenTimer>>

    @Query("SELECT * FROM templates") fun templates(): Flow<List<PlanTemplate>>

    @Upsert suspend fun recipe(value: Recipe)

    @Upsert suspend fun meal(value: Meal)

    @Upsert suspend fun pantry(value: PantryItem)

    @Upsert suspend fun shopping(value: ShoppingItem)

    @Upsert suspend fun timer(value: KitchenTimer)

    @Upsert suspend fun template(value: PlanTemplate)

    @Query("DELETE FROM recipes WHERE id=:id") suspend fun deleteRecipe(id: String)

    @Query("DELETE FROM meals WHERE recipeId=:id") suspend fun deleteRecipeMeals(id: String)

    @Query("DELETE FROM meals WHERE id=:id") suspend fun deleteMeal(id: String)

    @Query("DELETE FROM pantry WHERE id=:id") suspend fun deletePantry(id: String)

    @Query("DELETE FROM shopping WHERE id=:id") suspend fun deleteShopping(id: String)

    @Query("DELETE FROM timers WHERE id=:id") suspend fun deleteTimer(id: String)

    @Query("DELETE FROM templates WHERE id=:id") suspend fun deleteTemplate(id: String)

    @Query("DELETE FROM shopping WHERE manual=0") suspend fun clearGeneratedShopping()

    @Query("DELETE FROM meals WHERE date>=:start AND date<=:end AND planId=:planId")
    suspend fun clearWeek(start: String, end: String, planId: String = DEFAULT_PLAN_ID)

    @Query("DELETE FROM recipes") suspend fun clearRecipes()

    @Query("DELETE FROM meals") suspend fun clearMeals()

    @Query("DELETE FROM pantry") suspend fun clearPantry()

    @Query("DELETE FROM shopping") suspend fun clearShopping()

    @Query("DELETE FROM timers") suspend fun clearTimers()

    @Query("DELETE FROM templates") suspend fun clearTemplates()
}

@Database(
    entities =
        [
            Recipe::class,
            Meal::class,
            PantryItem::class,
            ShoppingItem::class,
            KitchenTimer::class,
            PlanTemplate::class,
            MealPlan::class,
        ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KitchenDatabase : RoomDatabase() {
    abstract fun kitchen(): KitchenDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS plans (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, active INTEGER NOT NULL)")
                db.execSQL("INSERT INTO plans (id,name,active) VALUES ('default','My meal plan',1)")
                db.execSQL("ALTER TABLE meals ADD COLUMN planId TEXT NOT NULL DEFAULT 'default'")
                db.execSQL("CREATE UNIQUE INDEX index_meals_planId_date_slot ON meals(planId,date,slot)")
                db.query("SELECT id, meals FROM templates").use { cursor ->
                    while (cursor.moveToNext()) {
                        val meals = com.google.gson.JsonParser.parseString(cursor.getString(1)).asJsonArray
                        meals.forEach { it.asJsonObject.addProperty("planId", DEFAULT_PLAN_ID) }
                        db.execSQL("UPDATE templates SET meals=? WHERE id=?", arrayOf(meals.toString(), cursor.getString(0)))
                    }
                }
            }
        }
    }
}
