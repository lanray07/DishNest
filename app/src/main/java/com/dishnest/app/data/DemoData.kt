package com.dishnest.app.data

import com.dishnest.app.domain.*
import java.time.LocalDate

object DemoData {
    fun recipes() =
        listOf(
                Recipe(
                    id = "demo-lemon",
                    title = "Lemon & herb chicken",
                    description =
                        "A little sunshine on your supper table. Golden chicken, sweet tomatoes and a bright squeeze of lemon.",
                    servings = 2,
                    prepMinutes = 10,
                    cookMinutes = 25,
                    cuisine = "Mediterranean",
                    tags = listOf("High protein"),
                    photo = "demo",
                    favourite = true,
                    collection = "Weeknight Dinners",
                    ingredients =
                        listOf(
                                "300 g chicken",
                                "200 g chickpeas",
                                "150 g cherry tomatoes",
                                "1 lemon",
                                "15 ml olive oil",
                                "1 courgette",
                            )
                            .map(Ingredients::parse),
                    steps =
                        listOf(
                            "Heat the oven to 200°C. Cut the courgette into thick slices and halve the tomatoes.",
                            "Toss the vegetables and chickpeas with olive oil in a roasting dish. Lay the chicken on top.",
                            "Squeeze over the lemon and season to your taste. Roast for 25 minutes, until the chicken is fully cooked; check the thickest part with a food thermometer.",
                            "Rest briefly, scatter with fresh herbs and serve warm.",
                        ),
                ),
                Recipe(
                    id = "demo-pasta",
                    photo = "demo-pasta",
                    title = "Slow Sunday tomato pasta",
                    description = "Simple ingredients. A very good dinner.",
                    servings = 4,
                    prepMinutes = 10,
                    cookMinutes = 20,
                    cuisine = "Italian",
                    tags = listOf("Vegetarian"),
                    collection = "Family Favourites",
                    favourite = true,
                    ingredients =
                        listOf(
                                "400 g pasta",
                                "600 g tomatoes",
                                "2 cloves garlic",
                                "30 ml olive oil",
                            )
                            .map(Ingredients::parse),
                    steps =
                        listOf(
                            "Chop the tomatoes and finely slice the garlic.",
                            "Warm the olive oil, add garlic and tomatoes, and simmer gently for 20 minutes.",
                            "Cook pasta following the packet instructions. Reserve a little cooking water, then drain.",
                            "Toss the pasta with the sauce, loosening with cooking water as needed.",
                        ),
                ),
                Recipe(
                    id = "demo-oats",
                    photo = "demo-oats",
                    title = "Apple & cinnamon overnight oats",
                    description = "Tomorrow morning, taken care of.",
                    servings = 2,
                    prepMinutes = 5,
                    cookMinutes = 0,
                    tags = listOf("Vegetarian"),
                    collection = "Quick Meals",
                    ingredients =
                        listOf("100 g oats", "200 ml milk", "1 apple").map(Ingredients::parse),
                    steps =
                        listOf(
                            "Mix the oats and milk in a covered container.",
                            "Grate in the apple and add cinnamon to taste.",
                            "Refrigerate overnight and serve chilled.",
                        ),
                ),
                Recipe(
                    id = "demo-soup",
                    photo = "demo-soup",
                    title = "Cosy red lentil soup",
                    description = "A comforting bowl for a quiet evening.",
                    servings = 4,
                    prepMinutes = 10,
                    cookMinutes = 30,
                    tags = listOf("Vegan"),
                    collection = "Batch Cooking",
                    ingredients =
                        listOf(
                                "250 g red lentils",
                                "1 onion",
                                "2 carrots",
                                "1000 ml vegetable stock",
                            )
                            .map(Ingredients::parse),
                    steps =
                        listOf(
                            "Rinse the lentils. Dice the onion and carrots.",
                            "Add everything to a large saucepan and bring to a gentle simmer.",
                            "Cook for 30 minutes until the lentils are tender. Blend if you prefer a smoother soup.",
                        ),
                ),
            )
            .mapIndexed { index, recipe ->
                recipe.copy(updatedAt = System.currentTimeMillis() - index * 1000)
            }

    fun pantry() =
        listOf(
            PantryItem(
                name = "chickpeas",
                quantity = 200.0,
                unit = "g",
                expiry = LocalDate.now().plusDays(2).toString(),
            ),
            PantryItem(name = "olive oil", quantity = 100.0, unit = "ml"),
            PantryItem(
                name = "lemon",
                quantity = 1.0,
                expiry = LocalDate.now().plusDays(3).toString(),
            ),
        )
}
