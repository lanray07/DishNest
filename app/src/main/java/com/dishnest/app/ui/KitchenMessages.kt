package com.dishnest.app.ui

import android.content.res.Resources
import com.dishnest.app.R
import com.dishnest.app.domain.KitchenFailure
import com.dishnest.app.domain.KitchenProblem

/** Only authored messages reach users. Provider paths, URLs and diagnostics stay private. */
fun Resources.kitchenFailure(failure: Throwable, fallback: Int = R.string.error_action_retry): String =
    getString(when ((failure as? KitchenFailure)?.problem) {
        KitchenProblem.RECIPE_FIELDS -> R.string.error_recipe_fields
        KitchenProblem.QUANTITY -> R.string.error_quantity
        KitchenProblem.NO_MATCH -> R.string.error_no_match
        KitchenProblem.PREVIOUS_WEEK -> R.string.error_previous_week
        KitchenProblem.EMPTY_WEEK -> R.string.error_empty_week
        KitchenProblem.MISSING_RECORD -> R.string.error_missing_record
        KitchenProblem.PLAN_NAME -> R.string.error_plan_name
        KitchenProblem.PLAN_LIMIT -> R.string.error_plan_limit
        KitchenProblem.KEEP_PLAN -> R.string.error_keep_plan
        KitchenProblem.SAME_PLAN -> R.string.error_same_plan
        KitchenProblem.RECIPE_LIMIT -> R.string.error_recipe_limit
        KitchenProblem.EMPTY_KITCHEN -> R.string.error_empty_kitchen
        KitchenProblem.PREMIUM -> R.string.premium_required
        null -> if (failure is java.time.format.DateTimeParseException) R.string.error_invalid_date else fallback
    })
