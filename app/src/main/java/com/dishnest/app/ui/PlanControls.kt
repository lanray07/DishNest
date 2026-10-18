package com.dishnest.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dishnest.app.*
import com.dishnest.app.R
import com.dishnest.app.domain.*

@Composable
fun PlanControls(state: KitchenState, vm: KitchenViewModel) {
    var manage by rememberSaveable { mutableStateOf(false) }
    val resources = LocalContext.current.resources
    fun label(plan: MealPlan) = if (plan.id == DEFAULT_PLAN_ID && plan.name == "My meal plan")
        resources.getString(R.string.default_plan) else plan.name
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.plans.forEach { plan ->
            FilterChip(selected = state.activePlanId == plan.id, onClick = { vm.selectPlan(plan.id) },
                label = { Text(label(plan)) })
        }
        AssistChip(onClick = { manage = true }, label = { Text(stringResource(R.string.manage_plans)) })
    }
    if (manage) {
        val active = state.plans.find { it.id == state.activePlanId } ?: defaultPlan()
        var name by rememberSaveable(active.id, active.name) { mutableStateOf(label(active)) }
        var newName by rememberSaveable { mutableStateOf("") }
        var delete by remember { mutableStateOf(false) }
        FullDialog({ manage = false }, stringResource(R.string.manage_plans)) {
            Field(stringResource(R.string.plan_name), name, { name = it.take(200) })
            OutlinedButton(onClick = { vm.renamePlan(active.id, name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.rename_plan))
            }
            OutlinedButton(onClick = { delete = true }, enabled = state.plans.size > 1) {
                Text(stringResource(R.string.delete_plan))
            }
            HorizontalDivider()
            Field(stringResource(R.string.new_plan_name), newName, { newName = it.take(200) })
            Button(onClick = { vm.createPlan(newName); newName = "" }, enabled = newName.isNotBlank() && vm.canUsePremium()) {
                Text(stringResource(R.string.create_plan))
            }
            if (!vm.canUsePremium()) Text(stringResource(R.string.more_plans_premium), color = Muted)
        }
        if (delete) AlertDialog(
            onDismissRequest = { delete = false },
            title = { Text(stringResource(R.string.delete_plan)) },
            text = { Text(stringResource(R.string.delete_plan_warning, label(active))) },
            confirmButton = { TextButton(onClick = { vm.deletePlan(active.id); delete = false }) {
                Text(stringResource(R.string.ui_delete_e2d0a5))
            } },
            dismissButton = { TextButton(onClick = { delete = false }) { Text(stringResource(R.string.ui_cancel_19766e)) } },
        )
    }
}
