package com.dishnest.app

import com.dishnest.app.domain.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class QuantityNumbersTest {
    @Test fun typedAmountsAcceptUnicodeDigitsAndUnambiguousDecimals() {
        listOf("٢٥٫٥", "२५.५", "２５．５", "25,5", "25.5").forEach {
            assertEquals(it, 25.5, QuantityNumbers.decimal(it)!!, 0.0)
        }
        assertEquals(0.0, QuantityNumbers.decimal("٠")!!, 0.0)
        listOf(".5", ",5", "٫٥").forEach {
            assertEquals(it, 0.5, QuantityNumbers.decimal(it)!!, 0.0)
        }
        listOf("1,000", "١٬٠٠٠", "1 000", "1.000,5", "NaN", "Infinity", "-1", "1e4", "1/2", "").forEach {
            assertNull(it, QuantityNumbers.decimal(it))
        }
    }

    @Test fun localizedUnitsAndNumbersConsolidateAndDeductWithoutChangingNames() {
        val name = "عدس B１２"
        val a = Ingredients.parse("٢٥٠ غرام $name")
        val b = Ingredients.parse("०.५ किलोग्राम $name")
        assertEquals(name, a.name); assertEquals(name, b.name)
        val recipes = listOf(Recipe(id = "a", ingredients = listOf(a)), Recipe(id = "b", ingredients = listOf(b)))
        val meals = listOf(Meal(date = "2030-01-01", slot = "Lunch", recipeId = "a"), Meal(date = "2030-01-01", slot = "Dinner", recipeId = "b"))
        val shopping = ShoppingEngine.generate(meals, recipes, listOf(PantryItem(name = name, quantity = 0.1, unit = "kg"))).single()
        assertEquals(name, shopping.name)
        assertEquals("g", shopping.unit)
        assertEquals(650.0, shopping.quantity!!, 0.000001)
    }

    @Test fun invalidFractionsAndAmbiguousQuantitiesRemainReviewable() {
        listOf("1/0 tsp salt", "1 1/0 tsp salt", "١,٠٠٠ غرام عدس", "١٬٠٠٠ غرام عدس", "0 g salt").forEach {
            val parsed = Ingredients.parse(it)
            assertNull(it, parsed.quantity)
            assertEquals(it, parsed.name)
            assertEquals("", parsed.unit)
        }
        assertNull(QuantityNumbers.ingredient("1 2"))
        assertEquals(1.5, Ingredients.parse("١½ cups milk").quantity!!, 0.0)
        assertEquals(0.5, Ingredients.parse("१/२ tsp salt").quantity!!, 0.0)
    }

    @Test fun metricAliasesDoNotInferCupVolumes() {
        listOf("grammes", "gramm", "grammi", "gramos", "gramas", "grammen", "gramów", "غرام", "ग्राम").forEach {
            assertEquals(it, 500.0, Ingredients.convert(0.5, "kg", it)!!, 0.0)
        }
        assertNull(Ingredients.convert(1.0, "cups", "ml"))
        assertNull(Ingredients.convert(1.0, "unknown", "g"))
    }

    @Test fun ingredientMatchingDoesNotDependOnTheDeviceLanguage() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("milk", Ingredients.name("MILK"))
            assertEquals("l", Ingredients.unit("LITERS"))
        } finally { Locale.setDefault(old) }
    }
}
