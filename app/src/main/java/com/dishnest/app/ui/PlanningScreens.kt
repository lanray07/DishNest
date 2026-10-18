package com.dishnest.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dishnest.app.*
import com.dishnest.app.R
import com.dishnest.app.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun PlanScreen(
    s: KitchenState,
    vm: KitchenViewModel,
    onRecipe: (Recipe) -> Unit,
    onShopping: () -> Unit,
) {
    val uiResources = androidx.compose.ui.platform.LocalContext.current.resources

    var swapOpen by rememberSaveable { mutableStateOf(false) }
    var firstSwapId by rememberSaveable { mutableStateOf<String?>(null) }
    var offset by rememberSaveable { mutableLongStateOf(0) }
    val start = Planner.week(LocalDate.now()).plusWeeks(offset)
    var assign by remember { mutableStateOf<Pair<LocalDate, String>?>(null) }
    var smart by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf("") }
    var saveTemplate by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            PageTitle(
                stringResource(com.dishnest.app.R.string.ui_make_room_for_good_food_e392e4),
                uiResources.getString(com.dishnest.app.R.string.copy_477ef9ae15),
                uiResources.getString(com.dishnest.app.R.string.copy_f03f0e4ef3),
                action = {
                    Button(onClick = { smart = true }, enabled = s.recipes.isNotEmpty()) {
                        Icon(Icons.Outlined.AutoAwesome, null)
                        Text(stringResource(com.dishnest.app.R.string.ui_plan_for_me_c13f33))
                    }
                },
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { offset-- }) {
                    Icon(
                        Icons.Outlined.ChevronLeft,
                        uiResources.getString(com.dishnest.app.R.string.copy_c0f8bbcd41),
                    )
                }
                Text(
                    "${start.format(DateTimeFormatter.ofPattern("d MMM"))} – ${start.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM yyyy"))}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { offset++ }) {
                    Icon(
                        Icons.Outlined.ChevronRight,
                        uiResources.getString(com.dishnest.app.R.string.copy_21fbde19cf),
                    )
                }
            }
        }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = { vm.copyPrevious(start) }) {
                    Text(stringResource(com.dishnest.app.R.string.ui_copy_previous_week_d774bf))
                }
                OutlinedButton(onClick = { saveTemplate = true }) {
                    Text(stringResource(com.dishnest.app.R.string.ui_save_template_47f72a))
                }
                OutlinedButton(onClick = { swapOpen = true; firstSwapId = null },
                    enabled = s.meals.count { it.date >= start.toString() && it.date <= start.plusDays(6).toString() } >= 2) {
                    Text(stringResource(R.string.swap_meals))
                }
                OutlinedButton(onClick = { clear = true }) {
                    Text(stringResource(com.dishnest.app.R.string.ui_clear_week_32148a))
                }
                Button(
                    onClick = {
                        vm.generateShopping(start)
                        onShopping()
                    }
                ) {
                    Icon(Icons.Outlined.ShoppingBasket, null)
                    Text(stringResource(com.dishnest.app.R.string.ui_make_shopping_list_ad0a17))
                }
            }
        }
        if (s.templates.isNotEmpty())
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    s.templates.forEach { t ->
                        AssistChip(
                            onClick = { vm.applyTemplate(t, start) },
                            label = { Text(stringResource(R.string.use_template, t.name)) },
                            leadingIcon = { Icon(Icons.Outlined.BookmarkBorder, null) },
                        )
                    }
                }
            }
        items(7) { day ->
            val date = start.plusDays(day.toLong())
            val meals = s.meals.filter { it.date == date.toString() }
            SoftCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        color = if (date == LocalDate.now()) Sage else Cream,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Column(
                            Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                date.format(DateTimeFormatter.ofPattern("EEE")),
                                color = if (date == LocalDate.now()) Color.White else Sage,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(
                                "${date.dayOfMonth}",
                                style = MaterialTheme.typography.titleLarge,
                                color = if (date == LocalDate.now()) Color.White else Ink,
                            )
                        }
                    }
                    Text(
                        date.format(DateTimeFormatter.ofPattern("EEEE", uiResources.configuration.locales[0])),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (date == LocalDate.now())
                        Text(
                            stringResource(com.dishnest.app.R.string.ui_today_2b065c),
                            color = Sage,
                        )
                }
                Spacer(Modifier.height(12.dp))
                Planner.slots.forEach { slot ->
                    val meal = meals.find { it.slot == slot }
                    val r = s.recipes.find { it.id == meal?.recipeId }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            uiResources.mealSlot(slot),
                            Modifier.widthIn(min = 80.dp, max = 115.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = Muted,
                        )
                        if (r == null)
                            TextButton(
                                onClick = { assign = date to slot },
                                enabled = s.recipes.isNotEmpty(),
                            ) {
                                Icon(Icons.Outlined.Add, null, Modifier.size(17.dp))
                                Text(stringResource(com.dishnest.app.R.string.ui_add_a_meal_fe642f))
                            }
                        else {
                            TextButton(onClick = { onRecipe(r) }, modifier = Modifier.weight(1f)) {
                                Text(r.title, modifier = Modifier.fillMaxWidth())
                            }
                            Text(
                                uiResources.servings(meal!!.servings),
                                style = MaterialTheme.typography.bodySmall,
                                color = Muted,
                            )
                            IconButton(onClick = { assign = date to slot }) {
                                Icon(
                                    Icons.Outlined.SwapHoriz,
                                    uiResources.getString(com.dishnest.app.R.string.copy_3927421bab),
                                )
                            }
                            IconButton(onClick = { vm.removeMeal(meal) }) {
                                Icon(
                                    Icons.Outlined.Close,
                                    uiResources.getString(com.dishnest.app.R.string.copy_93aa52c0ba),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (swapOpen) FullDialog({ swapOpen = false }, stringResource(R.string.swap_meals)) {
        Text(stringResource(R.string.swap_pick))
        s.meals.filter { it.date >= start.toString() && it.date <= start.plusDays(6).toString() }.forEach { meal ->
            val recipe = s.recipes.find { it.id == meal.recipeId }
            FilterChip(selected = firstSwapId == meal.id, onClick = {
                val first = firstSwapId
                if (first == meal.id) firstSwapId = null
                else if (first == null) firstSwapId = meal.id
                else { vm.swapMeals(first, meal.id); swapOpen = false; firstSwapId = null }
            }, label = { Text("${meal.date} · ${uiResources.mealSlot(meal.slot)} · ${recipe?.title.orEmpty()} · ${uiResources.servings(meal.servings)}") })
        }
    }
    assign?.let { (date, slot) ->
        var servings by remember {
            mutableIntStateOf(
                s.meals.find { it.date == date.toString() && it.slot == slot }?.servings ?: 2
            )
        }
        FullDialog(
            onDismiss = { assign = null },
            title = "${uiResources.mealSlot(slot)} · ${date.format(DateTimeFormatter.ofPattern("EEE d MMM", uiResources.configuration.locales[0]))}",
        ) {
            Stepper(servings, { servings = it }, stringResource(R.string.ui_servings_32939b))
            s.recipes.forEach { r ->
                OutlinedCard(
                    onClick = {
                        vm.meal(
                            Meal(
                                date = date.toString(),
                                slot = slot,
                                recipeId = r.id,
                                servings = servings,
                            )
                        )
                        assign = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(Icons.Outlined.Restaurant, null, tint = Sage)
                        Column {
                            Text(r.title, style = MaterialTheme.typography.titleMedium)
                            Text(uiResources.duration(r.prepMinutes + r.cookMinutes), color = Muted)
                        }
                    }
                }
            }
        }
    }
    if (smart) {
        var household by remember { mutableIntStateOf(2) }
        var maxTime by remember { mutableStateOf("60") }
        var cuisine by remember { mutableStateOf("") }
        var diet by remember { mutableStateOf("") }
        FullDialog(
            onDismiss = { smart = false },
            title = uiResources.getString(com.dishnest.app.R.string.copy_9f6ac50566),
        ) {
            Text(
                stringResource(
                    com.dishnest.app.R.string.ui_seven_dinners_from_your_saved_re_db57ba
                ),
                color = Muted,
            )
            Stepper(household, { household = it }, stringResource(R.string.people_label))
            Field(
                stringResource(com.dishnest.app.R.string.ui_maximum_total_minutes_4e76ea),
                maxTime,
                { maxTime = it.filter(Char::isDigit) },
            )
            Field(
                stringResource(com.dishnest.app.R.string.ui_cuisine_leave_blank_for_any_1adbae),
                cuisine,
                { cuisine = it },
            )
            Field(
                stringResource(
                    com.dishnest.app.R.string.ui_required_dietary_tag_exact_saved_84dad6
                ),
                diet,
                { diet = it },
            )
            Text(
                stringResource(
                    com.dishnest.app.R.string.ui_check_recipe_suitability_and_all_e97821
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
            Button(
                onClick = {
                    vm.smartPlan(
                        start,
                        PlanPreferences(household, maxTime.toIntOrNull() ?: 60, cuisine, diet),
                    )
                    smart = false
                },
                enabled = maxTime.toIntOrNull() in 1..10080,
            ) {
                Text(stringResource(com.dishnest.app.R.string.ui_plan_seven_dinners_a453e5))
            }
        }
    }
    if (saveTemplate)
        AlertDialog(
            onDismissRequest = { saveTemplate = false },
            title = {
                Text(stringResource(com.dishnest.app.R.string.ui_save_this_week_s_rhythm_229a2e))
            },
            text = {
                Field(
                    stringResource(com.dishnest.app.R.string.ui_template_name_7b905e),
                    templateName,
                    { templateName = it },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.saveTemplate(templateName, start)
                        saveTemplate = false
                    },
                    enabled = templateName.isNotBlank(),
                ) {
                    Text(stringResource(com.dishnest.app.R.string.ui_save_template_47f72a))
                }
            },
            dismissButton = {
                TextButton(onClick = { saveTemplate = false }) {
                    Text(stringResource(com.dishnest.app.R.string.ui_cancel_19766e))
                }
            },
        )
    if (clear)
        AlertDialog(
            onDismissRequest = { clear = false },
            title = { Text(stringResource(com.dishnest.app.R.string.ui_clear_this_week_0389b3)) },
            text = {
                Text(
                    stringResource(
                        com.dishnest.app.R.string.ui_your_recipes_and_saved_templates_56c9f9
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearWeek(start)
                        clear = false
                    }
                ) {
                    Text(stringResource(com.dishnest.app.R.string.ui_clear_week_32148a))
                }
            },
            dismissButton = {
                TextButton(onClick = { clear = false }) {
                    Text(stringResource(com.dishnest.app.R.string.ui_cancel_19766e))
                }
            },
        )
}

@Composable
fun ShoppingScreen(s: KitchenState, vm: KitchenViewModel) {
    val uiResources = androidx.compose.ui.platform.LocalContext.current.resources

    var editing by remember { mutableStateOf<ShoppingItem?>(null) }
    val context = LocalContext.current
    val export =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")
        ) { uri ->
            if (uri != null)
                vm.run {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                        it.write(shoppingText(s.shopping))
                    }
                    vm.message.value =
                        uiResources.getString(com.dishnest.app.R.string.copy_6a38c0b66f)
                }
        }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            PageTitle(
                stringResource(com.dishnest.app.R.string.ui_one_list_a_lighter_shop_ba8c84),
                uiResources.getString(com.dishnest.app.R.string.copy_e2a2406740),
                "${s.shopping.count {it.checked}} of ${s.shopping.size} items picked up",
                action = {
                    Button(onClick = { editing = ShoppingItem(name = "") }) {
                        Icon(Icons.Outlined.Add, null)
                        Text(stringResource(com.dishnest.app.R.string.ui_add_item_d1a0b3))
                    }
                },
            )
        }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = { vm.generateShopping(Planner.week(LocalDate.now())) }) {
                    Icon(Icons.Outlined.AutoAwesome, null)
                    Text(stringResource(com.dishnest.app.R.string.ui_from_this_week_s_plan_d1be4f))
                }
                OutlinedButton(
                    onClick = {
                        shareText(
                            context,
                            uiResources.getString(com.dishnest.app.R.string.copy_9f375d0d3d),
                            shoppingText(s.shopping),
                        )
                    }
                ) {
                    Icon(Icons.Outlined.Share, null)
                    Text(stringResource(com.dishnest.app.R.string.ui_share_list_33f118))
                }
                OutlinedButton(onClick = { export.launch("dishnest-shopping.txt") }) {
                    Text(stringResource(com.dishnest.app.R.string.ui_export_text_e4c312))
                }
            }
        }
        item {
            Surface(color = Color(0xFFE9EDDF), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.CheckCircleOutline, null, tint = Sage)
                    Text(
                        stringResource(
                            com.dishnest.app.R.string.ui_generated_quantities_reflect_pla_61a80d
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Sage,
                    )
                }
            }
        }
        if (s.shopping.isEmpty())
            item {
                EmptyState(
                    stringResource(
                        com.dishnest.app.R.string.ui_a_good_shop_starts_with_a_plan_dfb246
                    ),
                    uiResources.getString(com.dishnest.app.R.string.copy_2198638b49),
                    uiResources.getString(com.dishnest.app.R.string.copy_d944203610),
                    { editing = ShoppingItem(name = "") },
                )
            }
        s.shopping
            .groupBy { it.category }
            .forEach { (category, items) ->
                item {
                    SoftCard {
                        Text(category, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(10.dp))
                        items.forEach { item ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(item.checked, { vm.shopping(item.copy(checked = it)) })
                                Column(
                                    Modifier.weight(1f).clickable { editing = item }.padding(8.dp)
                                ) {
                                    Text(
                                        item.name,
                                        color = if (item.checked) Muted else Ink,
                                        style = MaterialTheme.typography.titleMedium,
                                        textDecoration =
                                            if (item.checked)
                                                androidx.compose.ui.text.style.TextDecoration
                                                    .LineThrough
                                            else null,
                                    )
                                    Text(
                                        Ingredients.display(item.quantity, item.unit),
                                        color = Sage,
                                    )
                                    if (item.contributions.isNotEmpty())
                                        Text(
                                            item.contributions.distinct().joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Muted,
                                        )
                                }
                                IconButton(onClick = { vm.deleteShopping(item) }) {
                                    Icon(Icons.Outlined.Close, "Remove ${item.name}")
                                }
                            }
                            HorizontalDivider(color = Line)
                        }
                    }
                }
            }
    }
    editing?.let { item ->
        ItemEditor(
            uiResources.getString(com.dishnest.app.R.string.copy_97a804fd48),
            item.name,
            item.quantity,
            item.unit,
            "",
            onDismiss = { editing = null },
            onSave = { name, q, u, _ ->
                vm.shopping(
                    item.copy(
                        name = name,
                        quantity = q,
                        unit = u,
                        category = Ingredients.category(name),
                    )
                )
                editing = null
            },
        )
    }
}

fun shoppingText(items: List<ShoppingItem>) =
    "DishNest shopping list\n\n" +
        items
            .groupBy { it.category }
            .entries
            .joinToString("\n\n") { (category, rows) ->
                category +
                    "\n" +
                    rows.joinToString("\n") {
                        "${if(it.checked) "[x]" else "[ ]"} ${it.name} · ${Ingredients.display(it.quantity,it.unit)}"
                    }
            }

@Composable
fun PantryScreen(s: KitchenState, vm: KitchenViewModel, onRecipe: (Recipe) -> Unit) {
    val uiResources = androidx.compose.ui.platform.LocalContext.current.resources

    var editing by remember { mutableStateOf<PantryItem?>(null) }
    val today = LocalDate.now()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            PageTitle(
                stringResource(com.dishnest.app.R.string.ui_a_little_less_waste_b8146c),
                uiResources.getString(com.dishnest.app.R.string.copy_ca2fa599a0),
                uiResources.getString(com.dishnest.app.R.string.copy_7ec446a336),
                action = {
                    Button(onClick = { editing = PantryItem(name = "") }) {
                        Icon(Icons.Outlined.Add, null)
                        Text(stringResource(com.dishnest.app.R.string.ui_add_item_d1a0b3))
                    }
                },
            )
        }
        if (s.pantry.isEmpty())
            item {
                EmptyState(
                    stringResource(com.dishnest.app.R.string.ui_what_s_in_your_kitchen_577097),
                    uiResources.getString(com.dishnest.app.R.string.copy_591303470a),
                    uiResources.getString(com.dishnest.app.R.string.copy_48432b3843),
                    { editing = PantryItem(name = "") },
                )
            }
        if (s.pantry.isNotEmpty())
            item {
                SoftCard {
                    s.pantry.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                color = Cream,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(42.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.Eco, null, tint = Sage)
                                }
                            }
                            Column(
                                Modifier.weight(1f)
                                    .clickable { editing = p }
                                    .padding(horizontal = 14.dp)
                            ) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium)
                                Text(Ingredients.display(p.quantity, p.unit), color = Muted)
                                if (p.expiry.isNotBlank())
                                    Text(
                                        uiResources.getString(if (p.expiry < today.toString()) R.string.past_date_value else R.string.check_date_value, p.expiry),
                                        color =
                                            if (p.expiry <= today.plusDays(3).toString()) Terra
                                            else Muted,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                            }
                            IconButton(onClick = { editing = p }) {
                                Icon(
                                    Icons.Outlined.Edit,
                                    uiResources.getString(com.dishnest.app.R.string.copy_e5fc1da796),
                                )
                            }
                            IconButton(onClick = { vm.deletePantry(p) }) {
                                Icon(
                                    Icons.Outlined.Close,
                                    uiResources.getString(com.dishnest.app.R.string.copy_86b1fdfa00),
                                )
                            }
                        }
                        HorizontalDivider(color = Line)
                    }
                }
            }
        item {
            Text(
                stringResource(
                    com.dishnest.app.R.string.ui_dates_are_reminders_to_check_you_fdb98c
                ),
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (s.recipes.isNotEmpty()) {
            item {
                SectionTitle(stringResource(com.dishnest.app.R.string.ui_use_what_you_have_37fd8b))
            }
            ShoppingEngine.suggestions(s.recipes, s.pantry).take(6).forEach { match ->
                item {
                    OutlinedCard(
                        onClick = { onRecipe(match.recipe) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                    ) {
                        Column(
                            Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(match.recipe.title, style = MaterialTheme.typography.titleLarge)
                            Text(
                                stringResource(R.string.pantry_match, match.available, match.recipe.ingredients.size),
                                color = Sage,
                            )
                            Text(
                                if (match.missing.isEmpty())
                                    uiResources.getString(com.dishnest.app.R.string.copy_89ca7aafe1)
                                else "Missing: ${match.missing.joinToString()}",
                                color = Muted,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
    editing?.let { p ->
        ItemEditor(
            uiResources.getString(com.dishnest.app.R.string.copy_b5b34f89ff),
            p.name,
            p.quantity,
            p.unit,
            p.expiry,
            onDismiss = { editing = null },
            pantry = true,
            onSave = { name, q, u, date ->
                vm.pantry(p.copy(name = name, quantity = q, unit = u, expiry = date))
                editing = null
            },
        )
    }
}

@Composable
fun ItemEditor(
    title: String,
    initialName: String,
    initialQuantity: Double?,
    initialUnit: String,
    initialDate: String,
    onDismiss: () -> Unit,
    pantry: Boolean = false,
    onSave: (String, Double?, String, String) -> Unit,
) {
    val uiResources = androidx.compose.ui.platform.LocalContext.current.resources

    var name by remember { mutableStateOf(initialName) }
    var q by remember { mutableStateOf(initialQuantity?.toString().orEmpty()) }
    var unit by remember { mutableStateOf(initialUnit) }
    var date by remember { mutableStateOf(initialDate) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(
                    stringResource(com.dishnest.app.R.string.ui_item_name_70d5f4),
                    name,
                    { name = it },
                )
                Field(
                    stringResource(com.dishnest.app.R.string.ui_quantity_blank_if_unknown_1d8e1a),
                    q,
                    { q = it },
                )
                Field(
                    stringResource(com.dishnest.app.R.string.ui_unit_g_kg_ml_l_tsp_tbsp_6e0535),
                    unit,
                    { unit = it },
                )
                if (pantry)
                    Field(
                        stringResource(
                            com.dishnest.app.R.string.ui_check_date_yyyy_mm_dd_optional_ebbc43
                        ),
                        date,
                        { date = it },
                    )
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val quantity = q.replace(',', '.').toDoubleOrNull()
                    when {
                        name.isBlank() ->
                            error = uiResources.getString(com.dishnest.app.R.string.copy_68669895ac)
                        q.isNotBlank() &&
                            (quantity == null || !quantity.isFinite() || quantity < 0) ->
                            error = uiResources.getString(com.dishnest.app.R.string.copy_2db80e2a1d)
                        pantry &&
                            date.isNotBlank() &&
                            runCatching { LocalDate.parse(date) }.isFailure ->
                            error = "Enter a date as YYYY-MM-DD."
                        else -> onSave(name.trim(), quantity, Ingredients.unit(unit), date)
                    }
                }
            ) {
                Text(stringResource(com.dishnest.app.R.string.ui_save_1509f5))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(com.dishnest.app.R.string.ui_cancel_19766e))
            }
        },
    )
}
