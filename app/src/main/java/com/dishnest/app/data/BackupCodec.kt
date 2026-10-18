package com.dishnest.app.data

import com.dishnest.app.domain.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.time.LocalDate

object BackupCodec {
    private val gson = Gson()

    fun encode(backup: Backup) = gson.toJson(backup)

    fun decode(raw: String, archivePhotos: Set<String> = emptySet()): Backup {
        require(raw.length <= 10_000_000) { "Backup exceeds 10 MB." }
        val root = JsonParser.parseString(raw).asJsonObject
        val version = root.get("version")?.asInt
        require(version in listOf(1, 2)) { "Unsupported backup version." }
        for (k in listOf("recipes", "meals", "pantry", "shopping", "templates")) require(
            root[k]?.isJsonArray == true
        ) {
            "Missing $k array."
        }
        if (version == 1) {
            root.addProperty("version", 2)
            root.add("plans", gson.toJsonTree(listOf(defaultPlan())))
            root["meals"].asJsonArray.forEach { it.asJsonObject.addProperty("planId", DEFAULT_PLAN_ID) }
            root["templates"].asJsonArray.forEach { template ->
                template.asJsonObject["meals"].asJsonArray.forEach { it.asJsonObject.addProperty("planId", DEFAULT_PLAN_ID) }
            }
        }
        require(root["plans"]?.isJsonArray == true) { "Missing plans array." }
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
        require(b.plans.size in 1..100 && b.plans.count { it.active } == 1) { "A backup must have exactly one selected plan." }
        b.plans.forEach { id(it.id); require(it.name.isNotBlank() && it.name.length <= 200) }
        val planIds = b.plans.map { it.id }.toSet()
        require(planIds.size == b.plans.size) { "Duplicate plan IDs." }
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
            require(r.photo.length <= 500 && (r.photo.isEmpty() || r.photo in bundledPhotoIds || r.photo in archivePhotos)) {
                "External image paths cannot be restored. Reattach recipe photos."
            }
            require(r.tags.size <= 100 && r.tags.all { it.length <= 200 } && r.notes.length <= 100000)
            require(r.cuisine.length <= 200 && r.collection.length <= 200 && r.difficulty.length <= 200)
            require(r.sourceUrl.length <= 2000 && (r.sourceUrl.isBlank() || r.sourceUrl.startsWith("https://")))
        }
        val recipeIds = b.recipes.map { it.id }.toSet()
        fun meal(m: Meal) {
            id(m.id)
            LocalDate.parse(m.date)
            require(
                m.slot in Planner.slots &&
                    m.servings in 1..100 &&
                    m.recipeId in recipeIds
            )
        }
        b.meals.forEach(::meal)
        require(b.meals.all { it.planId in planIds }) { "Meal refers to a missing plan." }
        b.pantry.forEach {
            id(it.id)
            require(it.name.isNotBlank() && it.name.length <= 1000 && it.unit.length <= 100)
            quantity(it.quantity)
            if (it.expiry.isNotEmpty()) LocalDate.parse(it.expiry)
        }
        b.shopping.forEach {
            id(it.id)
            require(it.name.isNotBlank() && it.name.length <= 1000 && it.unit.length <= 100 && it.category.length <= 200)
            quantity(it.quantity)
            require(it.contributions.size <= 10000)
        }
        b.templates.forEach {
            id(it.id)
            require(it.name.isNotBlank() && it.name.length <= 200 && it.meals.size in 1..28)
            it.meals.forEach(::meal)
            require(it.meals.map { meal -> meal.id }.distinct().size == it.meals.size) { "Duplicate template meal IDs." }
            require(it.meals.map { meal -> meal.date to meal.slot }.distinct().size == it.meals.size) { "Duplicate template slots." }
            require(it.meals.map { meal -> Planner.week(LocalDate.parse(meal.date)) }.distinct().size == 1) { "Template must fit one week." }
        }
        for (ids in
            listOf(
                b.recipes.map { it.id },
                b.meals.map { it.id },
                b.pantry.map { it.id },
                b.shopping.map { it.id },
                b.templates.map { it.id },
            )) require(ids.distinct().size == ids.size) { "Duplicate record IDs." }
        require(b.meals.map { Triple(it.planId, it.date, it.slot) }.distinct().size == b.meals.size) {
            "Duplicate meal slots."
        }
        return b
    }
}
