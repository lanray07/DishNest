package com.dishnest.app.data

import com.dishnest.app.domain.*
import com.google.gson.*
import java.net.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

object RecipeImport {
    fun text(raw: String): Recipe {
        require(raw.length in 1..100000) { "Paste a recipe under 100,000 characters." }
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        var section = "description"
        val ingredients = mutableListOf<Ingredient>()
        val steps = mutableListOf<String>()
        val description = mutableListOf<String>()
        for (line in lines.drop(1)) {
            when (line.lowercase().trimEnd(':')) {
                "ingredients" -> {
                    section = "ingredients"
                    continue
                }
                "instructions",
                "method",
                "directions",
                "steps" -> {
                    section = "steps"
                    continue
                }
            }
            when (section) {
                "ingredients" -> ingredients.add(Ingredients.parse(line.removePrefix("- ")))
                "steps" -> steps.add(line.replace(Regex("^\\d+[.)]\\s*"), ""))
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
            var current = URI(raw.trim()).toURL()
            var html: String? = null
            repeat(6) {
                if (html != null) return@repeat
                require(
                    current.protocol == "https" &&
                        current.userInfo == null &&
                        current.port in listOf(-1, 443)
                ) {
                    "Use a public HTTPS recipe URL."
                }
                val addresses = InetAddress.getAllByName(current.host)
                require(
                    addresses.isNotEmpty() &&
                        addresses.all {
                            !it.isAnyLocalAddress &&
                                !it.isLoopbackAddress &&
                                !it.isLinkLocalAddress &&
                                !it.isSiteLocalAddress &&
                                !it.isMulticastAddress &&
                                !it.hostAddress.orEmpty().startsWith("100.64.")
                        }
                ) {
                    "Private network URLs are not supported."
                }
                val connection = current.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty(
                    "User-Agent",
                    "DishNest/0.1 RecipeMetadata (user-requested import)",
                )
                try {
                    if (connection.responseCode in 300..399) {
                        current =
                            URL(
                                current,
                                connection.getHeaderField("Location")
                                    ?: error("Missing redirect target"),
                            )
                    } else {
                        require(connection.responseCode == 200) {
                            "The site declined access (${connection.responseCode}). Paste a recipe you have permission to save."
                        }
                        require(connection.contentType.orEmpty().contains("text/html")) {
                            "This URL does not contain a web recipe."
                        }
                        val bytes =
                            connection.inputStream.use { input ->
                                val output = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                var total = 0
                                while (true) {
                                    val size = input.read(buffer)
                                    if (size < 0) break
                                    total += size
                                    require(total <= 2_000_000) { "Recipe page is too large." }
                                    output.write(buffer, 0, size)
                                }
                                output.toByteArray()
                            }
                        html = String(bytes, Charsets.UTF_8)
                    }
                } finally {
                    connection.disconnect()
                }
            }
            val page = html ?: error("Too many redirects")
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
