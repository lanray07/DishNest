package com.dishnest.app

import com.dishnest.app.data.BackupCodec
import com.dishnest.app.data.RecipeImport
import com.dishnest.app.domain.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    private fun recipe(
        id: String = "r",
        ingredients: List<Ingredient> = listOf(Ingredient("flour", 100.0, "g")),
    ) =
        Recipe(
            id = id,
            title = "Bread",
            servings = 2,
            ingredients = ingredients,
            steps = listOf("Mix and bake"),
        )

    private fun meal(id: String = "m", r: String = "r", servings: Int = 2) =
        Meal(id = id, date = "2026-10-19", slot = "Dinner", recipeId = r, servings = servings)

    @Test
    fun scalesAndConsolidatesCompatibleWeights() {
        val recipes = listOf(recipe(), recipe("b", listOf(Ingredient("flour", 0.2, "kg"))))
        val result =
            ShoppingEngine.generate(
                listOf(meal(servings = 4), meal("n", "b")),
                recipes,
                emptyList(),
            )
        assertEquals(1, result.size)
        assertEquals(400.0, result.single().quantity!!, 0.00001)
        assertEquals(2, result.single().contributions.size)
    }

    @Test
    fun neverMixesWeightVolumeOrIngredientForms() {
        val r =
            recipe(
                ingredients =
                    listOf(
                        Ingredient("tomatoes", 100.0, "g"),
                        Ingredient("tomatoes", 100.0, "ml"),
                        Ingredient("chopped tomatoes", 100.0, "g"),
                    )
            )
        assertEquals(3, ShoppingEngine.generate(listOf(meal()), listOf(r), emptyList()).size)
        assertNull(Ingredients.convert(1.0, "ml", "g"))
        assertNull(Ingredients.convert(1.0, "cups", "ml"))
    }

    @Test
    fun deductsPantryOnlyInCompatibleUnits() {
        val result =
            ShoppingEngine.generate(
                listOf(meal()),
                listOf(recipe()),
                listOf(PantryItem(name = "flour", quantity = 0.05, unit = "kg")),
            )
        assertEquals(50.0, result.single().quantity!!, 0.00001)
        assertEquals(
            100.0,
            ShoppingEngine.generate(
                    listOf(meal()),
                    listOf(recipe()),
                    listOf(PantryItem(name = "flour", quantity = 50.0, unit = "ml")),
                )
                .single()
                .quantity!!,
            0.0,
        )
    }

    @Test
    fun unknownAmountsRemainSeparateAndAreNotInvented() {
        val r = recipe(ingredients = listOf(Ingredient("salt"), Ingredient("salt", 2.0, "g")))
        val rows =
            ShoppingEngine.generate(
                listOf(meal()),
                listOf(r),
                listOf(PantryItem(name = "salt", quantity = 1.0, unit = "g")),
            )
        assertEquals(2, rows.size)
        assertTrue(rows.any { it.quantity == null })
        assertTrue(rows.any { it.quantity == 1.0 })
    }

    @Test
    fun consumesPantryOnceAcrossMultipleContributions() {
        val result =
            ShoppingEngine.generate(
                listOf(meal(), meal("b")),
                listOf(recipe()),
                listOf(PantryItem(name = "flour", quantity = 150.0, unit = "g")),
            )
        assertEquals(50.0, result.single().quantity!!, 0.0)
    }

    @Test
    fun pantryCoversEverything() {
        assertTrue(
            ShoppingEngine.generate(
                    listOf(meal()),
                    listOf(recipe()),
                    listOf(PantryItem(name = "flour", quantity = 1.0, unit = "kg")),
                )
                .isEmpty()
        )
    }

    @Test
    fun fractionParsingAndNormalisation() {
        assertEquals(1.5, Ingredients.parse("1½ cups milk").quantity!!, 0.0)
        assertEquals(1.25, Ingredients.parse("1¼ cups milk").quantity!!, 0.0)
        assertNull(Ingredients.parse("1,000 g flour").quantity)
        assertEquals(1.5, Ingredients.parse("1 1/2 tablespoons olive oil").quantity!!, 0.0)
        assertEquals("tbsp", Ingredients.parse("1 1/2 tablespoons olive oil").unit)
        assertEquals("olive oil", Ingredients.parse("1 1/2 tablespoons olive oil").name)
        assertEquals(0.5, Ingredients.parse("1/2 tsp salt").quantity!!, 0.0)
        assertNull(Ingredients.parse("salt to taste").quantity)
    }

    @Test
    fun unitConversionsAreReversible() {
        for (unit in listOf("kg", "oz", "lb", "mg")) {
            val grams = Ingredients.convert(3.5, unit, "g")!!
            assertEquals(3.5, Ingredients.convert(grams, "g", unit)!!, 0.00001)
        }
    }

    @Test
    fun plannerRespectsFiltersAndIsDeterministic() {
        val a = recipe().copy(tags = listOf("Vegan"), cuisine = "Italian", cookMinutes = 20)
        val b = recipe("b").copy(cookMinutes = 120)
        val prefs = PlanPreferences(4, 30, "Italian", "Vegan")
        val start = LocalDate.parse("2026-10-19")
        val plan = Planner.generate(start, listOf(a, b), emptyList(), prefs)
        assertEquals(7, plan.size)
        assertTrue(plan.all { it.recipeId == a.id && it.servings == 4 })
        assertEquals(start.plusDays(6).toString(), plan.last().date)
        assertEquals(
            plan.map { it.recipeId },
            Planner.generate(start, listOf(a, b), emptyList(), prefs).map { it.recipeId },
        )
    }

    @Test
    fun plannerReturnsEmptyWhenNoRecipeMeetsDiet() {
        assertTrue(
            Planner.generate(
                    LocalDate.now(),
                    listOf(recipe()),
                    emptyList(),
                    PlanPreferences(dietaryTag = "Vegan"),
                )
                .isEmpty()
        )
    }

    @Test
    fun missingPantryIngredientsAreVisible() {
        val rank = ShoppingEngine.suggestions(listOf(recipe()), emptyList()).single()
        assertEquals(listOf("flour"), rank.missing)
        assertEquals(0, rank.available)
    }

    @Test
    fun timerPauseDoesNotAdvanceAndDeadlineRestores() {
        val paused =
            KitchenTimer(label = "Soup", deadline = 1000, remainingMillis = 5000, paused = true)
        assertEquals(5000, paused.remaining(100000))
        assertEquals(0, paused.copy(paused = false).remaining(2000))
        assertEquals(500, paused.copy(paused = false).remaining(500))
    }

    @Test
    fun voiceCommandsAreExplicitAndRecoverable() {
        assertEquals(VoiceCommand.Repeat, VoiceParser.parse("Start cooking."))
        assertEquals(VoiceCommand.Unknown, VoiceParser.parse("Set a timer for 5.5 minutes."))
        assertEquals(
            VoiceCommand.Timer(1500),
            VoiceParser.parse("Set a timer for twenty five minutes."),
        )
        assertEquals(VoiceCommand.Unknown, VoiceParser.parse("Set a timer for one hour."))
        assertEquals(
            VoiceCommand.Unknown,
            VoiceParser.parse("Set a timer for 1 minute and 30 seconds."),
        )
        assertEquals(VoiceCommand.Next, VoiceParser.parse("Read the next step."))
        assertEquals(VoiceCommand.Timer(600), VoiceParser.parse("Set a timer for ten minutes."))
        assertEquals(VoiceCommand.Timer(30), VoiceParser.parse("timer for 30 seconds"))
        assertEquals(VoiceCommand.Previous, VoiceParser.parse("Go back one step"))
        assertEquals(VoiceCommand.Quantity("flour"), VoiceParser.parse("How much flour do I need?"))
        assertEquals(VoiceCommand.Unknown, VoiceParser.parse("Something unrelated"))
        assertEquals(VoiceCommand.Next, VoiceParser.parse("siguiente paso", "es"))
    }

    @Test
    fun parsesRecipeTextWithoutMakingUpIngredients() {
        val r =
            RecipeImport.text("A soup\nIngredients\n200 g lentils\nMethod\n1. Rinse.\n2. Simmer.")
        assertEquals("A soup", r.title)
        assertEquals(200.0, r.ingredients.single().quantity!!, 0.0)
        assertEquals(listOf("Rinse.", "Simmer."), r.steps)
        assertTrue(RecipeImport.text("Only a title").ingredients.isEmpty())
    }

    @Test
    fun jsonLdFindsRecipeInsideGraph() {
        val node =
            com.google.gson.JsonParser.parseString(
                """{"@graph":[{"@type":"WebPage"},{"@type":["Recipe"],"name":"Soup","recipeIngredient":["200 g lentils"],"recipeInstructions":[{"@type":"HowToSection","itemListElement":[{"@type":"HowToStep","text":"Simmer gently."}]}]}]}"""
            )
        val r = RecipeImport.fromJsonLd(RecipeImport.findRecipe(node)!!)
        assertEquals("Soup", r.title)
        assertEquals("Simmer gently.", r.steps.single())
    }

    private fun backup() =
        Backup(
            recipes = listOf(recipe()),
            meals = listOf(meal()),
            pantry = emptyList(),
            shopping = emptyList(),
        )

    @Test
    fun backupRoundTripRetainsData() {
        val original = backup()
        assertEquals(original, BackupCodec.decode(BackupCodec.encode(original)))
        assertEquals("Amount not specified", Ingredients.display(null))
    }

    @Test
    fun rejectsUnknownBackupVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(
                BackupCodec.encode(backup()).replace("\"version\":2", "\"version\":99")
            )
        }
    }

    @Test
    fun rejectsOrphanedMealBeforeWriting() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(BackupCodec.encode(backup().copy(recipes = emptyList())))
        }
    }

    @Test
    fun rejectsDuplicateMealSlot() {
        val b = backup()
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(BackupCodec.encode(b.copy(meals = b.meals + meal("other"))))
        }
    }

    @Test
    fun rejectsExternalPhotoPaths() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(
                BackupCodec.encode(
                    backup().copy(recipes = listOf(recipe().copy(photo = "/private/file")))
                )
            )
        }
    }

    @Test
    fun mondayBoundary() {
        assertEquals(LocalDate.parse("2026-10-12"), Planner.week(LocalDate.parse("2026-10-18")))
    }
}
