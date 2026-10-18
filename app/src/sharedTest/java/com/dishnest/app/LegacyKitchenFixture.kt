package com.dishnest.app

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.google.gson.JsonParser

/** Recreate the exact released v1 schema; no production database is touched. */
object LegacyKitchenFixture {
    fun create(context: Context, name: String, schema: String) {
        require(name.startsWith("migration-fixture-"))
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            val database = JsonParser.parseString(schema).asJsonObject["database"].asJsonObject
            database["entities"].asJsonArray.forEach { entity ->
                val value = entity.asJsonObject
                db.execSQL(value["createSql"].asString.replace("\${TABLE_NAME}", value["tableName"].asString))
            }
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT INTO room_master_table (id,identity_hash) VALUES(42,?)", arrayOf(database["identityHash"].asString))
            db.insertOrThrow("recipes", null, ContentValues().apply {
                put("id", "legacy-recipe"); put("title", "Legacy soup"); put("description", "Keep this")
                put("servings", 2); put("prepMinutes", 5); put("cookMinutes", 10)
                put("difficulty", "Easy"); put("cuisine", ""); put("tags", "[]")
                put("ingredients", """[{"name":"lentils","quantity":200,"unit":"g","category":"Other"}]""")
                put("steps", """["Simmer."]"""); put("notes", "Original notes")
                put("sourceUrl", ""); put("photo", ""); put("favourite", 1)
                put("collection", "My legacy collection"); put("updatedAt", 123L)
            })
            db.execSQL("INSERT INTO meals(id,date,slot,recipeId,servings) VALUES ('legacy-meal','2026-10-19','Dinner','legacy-recipe',3)")
            db.execSQL("INSERT INTO templates(id,name,meals) VALUES (?,?,?)", arrayOf("legacy-template", "Legacy week",
                """[{"id":"legacy-meal","date":"2026-10-19","slot":"Dinner","recipeId":"legacy-recipe","servings":3}]"""))
            db.version = 1
        }
    }
}
