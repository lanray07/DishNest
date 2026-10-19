package com.dishnest.app.data

import com.dishnest.app.domain.*
import com.google.gson.*
import java.net.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

object RecipeImport {
    // Match complete heading lines only; never translate or rewrite recipe content.
    private val ingredientHeadings = setOf(
        "ingredients", "zutaten", "ingredientes", "ingrédients", "ingredienti",
        "ingrediënten", "składniki", "المكونات", "مكونات", "المقادير", "सामग्री",
    )
    private val methodHeadings = setOf(
        "instructions", "method", "directions", "steps", "zubereitung", "anleitung",
        "preparación", "preparacion", "instrucciones", "préparation", "preparation",
        "méthode", "procedimento", "preparazione", "modo de preparo", "modo de preparação",
        "preparo", "preparação", "bereiding", "bereidingswijze", "przygotowanie",
        "sposób przygotowania", "طريقة التحضير", "الطريقة", "विधि", "बनाने की विधि",
    )

    private fun heading(line: String): String = java.text.Normalizer
        .normalize(line, java.text.Normalizer.Form.NFKC)
        .trim().trimEnd(':', '：').trim().lowercase(java.util.Locale.ROOT)

    fun text(raw: String): Recipe {
        require(raw.length in 1..100000) { "Paste a recipe under 100,000 characters." }
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        var section = "description"
        val ingredients = mutableListOf<Ingredient>()
        val steps = mutableListOf<String>()
        val description = mutableListOf<String>()
        for (line in lines.drop(1)) {
            when (heading(line)) {
                in ingredientHeadings -> {
                    section = "ingredients"
                    continue
                }
                in methodHeadings -> {
                    section = "steps"
                    continue
                }
            }
            when (section) {
                "ingredients" -> ingredients.add(Ingredients.parse(line.replace(Regex("^[-•*]\\s+"), "")))
                "steps" -> steps.add(line.replace(Regex("^\\p{Nd}+[.)।]\\s*"), ""))
                else -> description.add(line)
            }
        }
        return Recipe(
            title = lines.firstOrNull().orEmpty(),
            description = description.joinToString("\n"),
            ingredients = ingredients,
            steps = steps,
            servings = 0,
            prepMinutes = -1,
            cookMinutes = -1,
            difficulty = "",
        )
    }

    suspend fun url(raw: String): Recipe =
        withContext(Dispatchers.IO) {
            val fetched = RecipeWebAccess.page(URI(raw.trim()).toURL())
            val current = fetched.url
            val page = fetched.html
            val document = Jsoup.parse(page)
            val recipe =
                document
                    .select("script[type=application/ld+json]")
                    .asSequence()
                    .mapNotNull {
                        runCatching { findRecipe(JsonParser.parseString(it.data())) }.getOrNull()
                    }
                    .firstOrNull()
                    ?: error("No supported Recipe metadata found. Use text import instead.")
            fromJsonLd(recipe).copy(sourceUrl = current.toString())
        }

    fun findRecipe(node: JsonElement): JsonObject? {
        if (node.isJsonArray) return node.asJsonArray.firstNotNullOfOrNull { findRecipe(it) }
        if (!node.isJsonObject) return null
        val o = node.asJsonObject
        val type = o.get("@type")
        if (
            type?.let {
                if (it.isJsonArray)
                    it.asJsonArray.any { t -> t.isJsonPrimitive && t.asString == "Recipe" }
                else it.isJsonPrimitive && it.asString == "Recipe"
            } == true
        )
            return o
        return o.entrySet().firstNotNullOfOrNull { (_, v) ->
            if (v.isJsonArray || v.isJsonObject) findRecipe(v) else null
        }
    }

    fun fromJsonLd(o: JsonObject): Recipe {
        fun str(key: String) = o.get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        fun minutes(key: String) =
            runCatching {
                    java.time.Duration.parse(str(key)).toMinutes().toInt().coerceIn(0, 10080)
                }
                .getOrDefault(-1)
        fun steps(e: JsonElement?): List<String> =
            when {
                e == null -> emptyList()
                e.isJsonPrimitive -> e.asString.lines().filter { it.isNotBlank() }
                e.isJsonArray -> e.asJsonArray.flatMap { steps(it) }
                e.isJsonObject ->
                    if (e.asJsonObject.has("text")) steps(e.asJsonObject["text"])
                    else steps(e.asJsonObject["itemListElement"])
                else -> emptyList()
            }
        return Recipe(
            title = Jsoup.parse(str("name")).text(),
            description = Jsoup.parse(str("description")).text(),
            servings =
                Regex("\\d+").find(str("recipeYield"))?.value?.toIntOrNull()?.coerceIn(1, 100) ?: 0,
            difficulty = "",
            prepMinutes = minutes("prepTime"),
            cookMinutes = minutes("cookTime"),
            cuisine = str("recipeCuisine"),
            ingredients =
                o.getAsJsonArray("recipeIngredient")
                    ?.map { Ingredients.parse(Jsoup.parse(it.asString).text()) }
                    .orEmpty(),
            steps = steps(o["recipeInstructions"]).map { Jsoup.parse(it).text() },
        )
    }
}
