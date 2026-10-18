package com.dishnest.app.data

import com.dishnest.app.domain.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.time.LocalDate

object BackupCodec {
    private val gson = Gson()

    fun encode(backup: Backup) = gson.toJson(backup)

    fun decode(raw: String): Backup {
        require(raw.length <= 10_000_000) { "Backup exceeds 10 MB." }
        val root = JsonParser.parseString(raw).asJsonObject
        require(root.get("version")?.asInt == 1) { "Unsupported backup version." }
        for (k in listOf("recipes", "meals", "pantry", "shopping", "templates")) require(
            root[k]?.isJsonArray == true
        ) {
            "Missing $k array."
        }
        val b = gson.fromJson(root, Backup::class.java)
        require(
            b.recipes.size <= 10000 &&
                b.meals.size <= 50000 &&
                b.pantry.size <= 10000 &&
                b.shopping.size <= 10000 &&
                b.templates.size <= 1000
        ) {
            "Backup contains too many records."
        }
        fun id(v: String) {
            require(v.isNotBlank() && v.length <= 100)
        }
        fun quantity(v: Double?) {
            require(v == null || (v.isFinite() && v >= 0 && v <= 1e9)) { "Invalid quantity" }
        }
        b.recipes.forEach { r ->
            id(r.id)
            require(
                r.title.isNotBlank() &&
                    r.title.length <= 500 &&
                    r.description.length <= 100000 &&
                    r.servings in 1..100 &&
                    r.prepMinutes in 0..10080 &&
                    r.cookMinutes in 0..10080
            )
            require(r.ingredients.size <= 500 && r.steps.size <= 500)
            r.ingredients.forEach {
                require(it.name.isNotBlank() && it.name.length <= 1000)
                quantity(it.quantity)
                require(it.unit.length <= 100)
            }
            r.steps.forEach { require(it.isNotBlank() && it.length <= 20000) }
            require(r.photo.length <= 500 && (r.photo.isEmpty() || r.photo in bundledPhotoIds)) {
                "External image paths cannot be restored. Reattach recipe photos."
            }
            require(r.tags.size <= 100 && r.notes.length <= 100000)
        }
        fun meal(m: Meal) {
            id(m.id)
            LocalDate.parse(m.date)
            require(
                m.slot in Planner.slots &&
                    m.servings in 1..100 &&
                    b.recipes.any { it.id == m.recipeId }
            )
        }
        b.meals.forEach(::meal)
        b.pantry.forEach {
            id(it.id)
            require(it.name.isNotBlank())
            quantity(it.quantity)
            if (it.expiry.isNotEmpty()) LocalDate.parse(it.expiry)
        }
        b.shopping.forEach {
            id(it.id)
            require(it.name.isNotBlank())
            quantity(it.quantity)
            require(it.contributions.size <= 10000)
        }
        b.templates.forEach {
            id(it.id)
            require(it.name.isNotBlank() && it.meals.size <= 28)
            it.meals.forEach(::meal)
        }
        for (ids in
            listOf(
                b.recipes.map { it.id },
                b.meals.map { it.id },
                b.pantry.map { it.id },
                b.shopping.map { it.id },
                b.templates.map { it.id },
            )) require(ids.distinct().size == ids.size) { "Duplicate record IDs." }
        require(b.meals.map { it.date to it.slot }.distinct().size == b.meals.size) {
            "Duplicate meal slots."
        }
        return b
    }
}
