package com.dishnest.app.domain

/** Stable failure codes cross the data/UI boundary; internal exception text is never UI copy. */
enum class KitchenProblem {
    RECIPE_FIELDS, QUANTITY, NO_MATCH, PREVIOUS_WEEK, EMPTY_WEEK, MISSING_RECORD,
    PLAN_NAME, PLAN_LIMIT, KEEP_PLAN, SAME_PLAN, RECIPE_LIMIT, EMPTY_KITCHEN, PREMIUM,
}

class KitchenFailure(val problem: KitchenProblem) : IllegalArgumentException(problem.name)

fun requireKitchen(condition: Boolean, problem: KitchenProblem) {
    if (!condition) throw KitchenFailure(problem)
}
