package com.dishnest.app.ui

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.dishnest.app.*
import com.dishnest.app.R
import com.dishnest.app.domain.*
import com.google.gson.Gson
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class NavItem(val id: String, val label: Int, val icon: ImageVector)

private val recipeDraftSaver =
    Saver<Recipe?, String>(
        save = { Gson().toJson(it) },
        restore = { Gson().fromJson(it, Recipe::class.java) },
    )

val navigation =
    listOf(
        NavItem("home", R.string.nav_home, Icons.Outlined.SpaceDashboard),
        NavItem("recipes", R.string.nav_recipes, Icons.AutoMirrored.Outlined.MenuBook),
        NavItem("plan", R.string.nav_plan, Icons.Outlined.CalendarMonth),
        NavItem("shop", R.string.nav_shop, Icons.Outlined.ShoppingBasket),
        NavItem("pantry", R.string.nav_pantry, Icons.Outlined.Kitchen),
    )

@Composable
fun KitchenApp(vm: KitchenViewModel = viewModel()) {
    val uiResources = androidx.compose.ui.platform.LocalContext.current.resources

    val s by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf("home") }
    var recipeId by rememberSaveable { mutableStateOf<String?>(null) }
    var cooking by rememberSaveable { mutableStateOf(false) }
    var editor by rememberSaveable(stateSaver = recipeDraftSaver) { mutableStateOf<Recipe?>(null) }
    var importing by remember { mutableStateOf(false) }
    var premium by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(message) {
        if (message.isNotBlank()) {
            snackbar.showSnackbar(message)
            vm.message.value = ""
        }
    }
    val selected = s.recipes.find { it.id == recipeId }
    BoxWithConstraints(Modifier.fillMaxSize().background(Ivory).safeDrawingPadding()) {
        val wide = maxWidth >= 840.dp
        Scaffold(
            containerColor = Ivory,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (!wide && !cooking)
                    NavigationBar(containerColor = Color.White) {
                        navigation.forEach { n ->
                            NavigationBarItem(
                                selected = screen == n.id,
                                onClick = {
                                    screen = n.id
                                    recipeId = null
                                },
                                icon = { Icon(n.icon, stringResource(n.label)) },
                                label = { Text(stringResource(n.label), maxLines = 2) },
                            )
                        }
                    }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide && !cooking)
                    Column(
                        Modifier.width(210.dp)
                            .fillMaxHeight()
                            .background(Color(0xFFF0F1E9))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Brand()
                        Spacer(Modifier.height(32.dp))
                        Text(
                            stringResource(
                                com.dishnest.app.R.string.ui_your_everyday_kitchen_f3db1b
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted,
                        )
                        navigation.forEach { n ->
                            val active = screen == n.id
                            Surface(
                                onClick = {
                                    screen = n.id
                                    recipeId = null
                                },
                                color = if (active) Sage else Color.Transparent,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth()
                                        .padding(horizontal = 13.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        n.icon,
                                        null,
                                        tint = if (active) Color.White else Muted,
                                        modifier = Modifier.size(21.dp),
                                    )
                                    Text(
                                        stringResource(n.label),
                                        color = if (active) Color.White else Ink,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Surface(
                            onClick = { premium = true },
                            color = Cream,
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Outlined.AutoAwesome, null, tint = Terra)
                                Text(
                                    stringResource(
                                        com.dishnest.app.R.string.ui_room_for_more_627976
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    stringResource(
                                        com.dishnest.app.R.string
                                            .ui_a_little_extra_for_your_kitchen_57e0a8
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Muted,
                                )
                                Text(
                                    stringResource(
                                        com.dishnest.app.R.string.ui_explore_premium_7a1440
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Sage,
                                )
                            }
                        }
                        TextButton(
                            onClick = {
                                screen = "settings"
                                recipeId = null
                            }
                        ) {
                            Icon(Icons.Outlined.Settings, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.nav_settings))
                        }
                        Text(
                            stringResource(
                                com.dishnest.app.R.string.ui_made_for_the_joy_of_cooking_c83bee
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = Muted,
                        )
                    }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (!cooking)
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(horizontal = if (wide) 32.dp else 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!wide) Brand()
                            else
                                Text(
                                    stringResource(
                                        com.dishnest.app.R.string
                                            .ui_your_kitchen_beautifully_togethe_45a076
                                    ),
                                    color = Muted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (s.demo)
                                    SuggestionChip(
                                        onClick = { screen = "settings" },
                                        label = {
                                            Text(
                                                stringResource(
                                                    com.dishnest.app.R.string.ui_demo_kitchen_e41a43
                                                )
                                            )
                                        },
                                    )
                                IconButton(
                                    onClick = {
                                        if (vm.canUsePremium()) importing = true else premium = true
                                    }
                                ) {
                                    Icon(
                                        Icons.Outlined.FileDownload,
                                        uiResources.getString(
                                            com.dishnest.app.R.string.copy_024950cf2c
                                        ),
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        screen = "settings"
                                        recipeId = null
                                    }
                                ) {
                                    Icon(
                                        Icons.Outlined.Tune,
                                        uiResources.getString(
                                            com.dishnest.app.R.string.copy_74a883a037
                                        ),
                                    )
                                }
                            }
                        }
                    when {
                        cooking && selected != null ->
                            CookingScreen(selected, s.timers, vm, onFinish = { cooking = false })
                        selected != null ->
                            RecipeDetail(
                                selected,
                                onBack = { recipeId = null },
                                onCook = { cooking = true },
                                onEdit = { editor = selected },
                                onFavourite = { vm.favourite(selected) },
                                onDuplicate = {
                                    editor =
                                        selected.copy(
                                            id = newId(),
                                            title = "${selected.title} (copy)",
                                        )
                                },
                                onDelete = {
                                    vm.delete(selected)
                                    recipeId = null
                                },
                            )
                        screen == "home" ->
                            HomeScreen(
                                s,
                                onRecipe = { recipeId = it.id },
                                onAdd = { editor = Recipe() },
                                onNavigate = { screen = it },
                                onDemo = { vm.loadDemo() },
                                onCook = { r ->
                                    recipeId = r.id
                                    cooking = true
                                },
                            )
                        screen == "recipes" ->
                            RecipesScreen(
                                s.recipes,
                                onRecipe = { recipeId = it.id },
                                onAdd = { editor = Recipe() },
                                onImport = {
                                    if (vm.canUsePremium()) importing = true else premium = true
                                },
                                onFavourite = { vm.favourite(it) },
                            )
                        screen == "plan" ->
                            PlanScreen(
                                s,
                                vm,
                                onRecipe = { recipeId = it.id },
                                onShopping = { screen = "shop" },
                            )
                        screen == "shop" -> ShoppingScreen(s, vm)
                        screen == "pantry" -> PantryScreen(s, vm, onRecipe = { recipeId = it.id })
                        else -> SettingsScreen(s, vm, onPremium = { premium = true })
                    }
                }
            }
        }
        editor?.let { r ->
            RecipeEditor(
                r,
                onDismiss = { editor = null },
                onSave = { vm.save(it) { editor = null } },
            )
        }
        if (importing)
            ImportDialog(
                onDismiss = { importing = false },
                onReview = {
                    vm.metrics.record("recipe_import_success")
                    importing = false
                    editor = it
                },
            )
        if (premium) PremiumDialog(vm, onDismiss = { premium = false })
    }
}

@Composable
fun Brand() {
    val uiResources = androidx.compose.ui.platform.LocalContext.current.resources

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(color = Sage, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(40.dp)) {
            Image(
                painterResource(R.drawable.ic_nest),
                uiResources.getString(com.dishnest.app.R.string.copy_ff59fa9de2),
            )
        }
        Text(
            stringResource(com.dishnest.app.R.string.ui_dishnest_80ec64),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun PageTitle(
    eyebrow: String,
    title: String,
    subtitle: String = "",
    action: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 540.dp
        val heading: @Composable () -> Unit = {
            Column {
                Text(
                    eyebrow.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = Sage,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    title,
                    style =
                        if (compact) MaterialTheme.typography.headlineMedium
                        else MaterialTheme.typography.headlineLarge,
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(subtitle, color = Muted)
                }
            }
        }
        if (compact)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                heading()
                action?.invoke()
            }
        else
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { heading() }
                if (action != null) {
                    Spacer(Modifier.width(12.dp))
                    action()
                }
            }
    }
}

@Composable
fun SectionTitle(title: String, link: String = "", onLink: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (link.isNotEmpty())
            TextButton(onClick = onLink) {
                Text(link)
                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp))
            }
    }
}

@Composable
fun SoftCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier,
        border = BorderStroke(1.dp, Line),
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
fun EmptyState(title: String, body: String, button: String = "", onClick: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(
            painterResource(R.drawable.ic_nest),
            null,
            Modifier.size(84.dp).background(Sage, RoundedCornerShape(24.dp)),
        )
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, color = Muted)
        if (button.isNotEmpty()) Button(onClick = onClick) { Text(button) }
    }
}

@Composable
fun FoodImage(recipe: Recipe, modifier: Modifier = Modifier) {
    val bundledImage =
        when (recipe.photo) {
            "demo" -> R.drawable.lemon_chicken
            "demo-pasta" -> R.drawable.food_pasta
            "demo-soup" -> R.drawable.food_soup
            "demo-oats" -> R.drawable.food_oats
            else -> null
        }
    if (bundledImage != null)
        Image(
            painterResource(bundledImage),
            recipe.title,
            modifier,
            contentScale = ContentScale.Crop,
        )
    else if (recipe.photo.isNotEmpty())
        AsyncImage(
            model = recipe.photo,
            contentDescription = recipe.title,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    else
        Box(
            modifier.background(
                when (recipe.collection) {
                    "Quick Meals" -> Color(0xFFEADFCB)
                    "Batch Cooking" -> Color(0xFFEAD6CB)
                    else -> Color(0xFFE5EADF)
                }
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Restaurant, null, Modifier.size(46.dp), tint = Sage)
        }
}

@Composable
fun RecipeCard(
    r: Recipe,
    onClick: () -> Unit,
    onFavourite: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val uiResources = androidx.compose.ui.platform.LocalContext.current.resources

    Surface(
        onClick = onClick,
        modifier = modifier,
        border = BorderStroke(1.dp, Line),
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column {
            Box {
                FoodImage(r, Modifier.fillMaxWidth().height(145.dp))
                if (onFavourite != null)
                    IconButton(
                        onClick = onFavourite,
                        modifier =
                            Modifier.align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(34.dp)
                                .background(Color.White, RoundedCornerShape(50)),
                    ) {
                        Icon(
                            if (r.favourite) Icons.Outlined.Favorite
                            else Icons.Outlined.FavoriteBorder,
                            uiResources.getString(com.dishnest.app.R.string.copy_3621a99bef),
                            tint = if (r.favourite) Terra else Ink,
                            modifier = Modifier.size(19.dp),
                        )
                    }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    r.collection.uppercase(),
                    color = Sage,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    r.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Schedule, null, Modifier.size(15.dp), tint = Muted)
                    Text(
                        uiResources.recipeSummary(r) + " · " + r.difficulty,
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    s: KitchenState,
    onRecipe: (Recipe) -> Unit,
    onAdd: () -> Unit,
    onNavigate: (String) -> Unit,
    onDemo: () -> Unit,
    onCook: (Recipe) -> Unit,
) {
    val uiResources = androidx.compose.ui.platform.LocalContext.current.resources

    val today = LocalDate.now()
    val planned = s.meals.filter { it.date == today.toString() }
    val hero =
        planned.firstNotNullOfOrNull { m -> s.recipes.find { it.id == m.recipeId } }
            ?: s.recipes.firstOrNull()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            PageTitle(
                today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                uiResources.getString(com.dishnest.app.R.string.copy_8b566d5081),
                stringResource(R.string.greeting),
                action = {
                    FilledTonalButton(onClick = onAdd) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(stringResource(R.string.add_recipe))
                    }
                },
            )
        }
        item {
            if (hero == null)
                SoftCard {
                    EmptyState(
                        stringResource(R.string.empty_recipes),
                        stringResource(R.string.empty_hint),
                        uiResources.getString(com.dishnest.app.R.string.copy_adda900c1c),
                        onAdd,
                    )
                    TextButton(
                        onClick = onDemo,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(
                            stringResource(
                                com.dishnest.app.R.string.ui_explore_a_demo_kitchen_6bdc4e
                            )
                        )
                    }
                }
            else
                BoxWithConstraints {
                    val wide = maxWidth >= 620.dp
                    Surface(color = Color(0xFFE9EDDF), shape = RoundedCornerShape(22.dp)) {
                        if (wide)
                            Row(Modifier.height(IntrinsicSize.Min)) {
                                HeroCopy(hero, planned.isNotEmpty(), onCook, Modifier.weight(1f))
                                FoodImage(hero, Modifier.weight(0.95f).height(282.dp))
                            }
                        else
                            Column {
                                FoodImage(hero, Modifier.fillMaxWidth().height(190.dp))
                                HeroCopy(
                                    hero,
                                    planned.isNotEmpty(),
                                    onCook,
                                    Modifier.fillMaxWidth(),
                                )
                            }
                    }
                }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(
                    "${s.recipes.size}",
                    uiResources.getString(com.dishnest.app.R.string.copy_4304f7be23),
                    Icons.AutoMirrored.Outlined.MenuBook,
                    { onNavigate("recipes") },
                    Modifier.weight(1f),
                )
                Stat(
                    "${s.meals.count {it.date>=Planner.week(today).toString() && it.date<=Planner.week(today).plusDays(6).toString()}}",
                    uiResources.getString(com.dishnest.app.R.string.copy_793b69cce2),
                    Icons.Outlined.CalendarMonth,
                    { onNavigate("plan") },
                    Modifier.weight(1f),
                )
                Stat(
                    "${s.shopping.count {!it.checked}}",
                    uiResources.getString(com.dishnest.app.R.string.copy_9ee92f81e3),
                    Icons.Outlined.ShoppingBasket,
                    { onNavigate("shop") },
                    Modifier.weight(1f),
                )
            }
        }
        if (s.recipes.isNotEmpty())
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionTitle(
                        stringResource(com.dishnest.app.R.string.ui_a_little_inspiration_4c35e1),
                        uiResources.getString(com.dishnest.app.R.string.copy_e5e04f3aa7),
                        { onNavigate("recipes") },
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        s.recipes.take(4).forEach { r ->
                            RecipeCard(r, { onRecipe(r) }, modifier = Modifier.width(255.dp))
                        }
                    }
                }
            }
        item {
            BoxWithConstraints {
                val wide = maxWidth > 650.dp
                if (wide)
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        WeekPreview(s, onNavigate, Modifier.weight(1f))
                        PantryPreview(s, onNavigate, Modifier.weight(1f))
                    }
                else
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        WeekPreview(s, onNavigate, Modifier.fillMaxWidth())
                        PantryPreview(s, onNavigate, Modifier.fillMaxWidth())
                    }
            }
        }
        if (s.recipes.any { it.favourite })
            item {
                SectionTitle(
                    stringResource(com.dishnest.app.R.string.ui_made_again_loved_again_f1ebfd),
                    uiResources.getString(com.dishnest.app.R.string.copy_d97d51d370),
                    { onNavigate("recipes") },
                )
                s.recipes
                    .filter { it.favourite }
                    .take(3)
                    .forEach { r ->
                        ListItem(
                            headlineContent = { Text(r.title) },
                            supportingContent = {
                                Text(uiResources.duration(r.prepMinutes + r.cookMinutes) + " · " + r.collection)
                            },
                            leadingContent = {
                                Icon(Icons.Outlined.FavoriteBorder, null, tint = Terra)
                            },
                            modifier = Modifier.clickable { onRecipe(r) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
            }
    }
}

@Composable
private fun HeroCopy(r: Recipe, planned: Boolean, onCook: (Recipe) -> Unit, modifier: Modifier) {
    val uiResources = LocalContext.current.resources
    Column(modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(if (planned) R.string.hero_planned else R.string.hero_saved),
            style = MaterialTheme.typography.labelSmall,
            color = Sage,
        )
        Text(r.title, style = MaterialTheme.typography.headlineLarge)
        Text(
            r.description.ifBlank { uiResources.getString(R.string.hero_inspiration) },
            color = Muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            uiResources.recipeSummary(r),
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
        Button(onClick = { onCook(r) }, shape = RoundedCornerShape(10.dp)) {
            Icon(Icons.Outlined.PlayArrow, null)
            Spacer(Modifier.width(7.dp))
            Text(stringResource(R.string.cook))
        }
    }
}

@Composable
private fun Stat(
    value: String,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Line),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(icon, null, tint = Sage, modifier = Modifier.size(20.dp))
                Text(value, style = MaterialTheme.typography.titleLarge)
            }
            Text(label, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun WeekPreview(s: KitchenState, onNavigate: (String) -> Unit, modifier: Modifier) {
    SoftCard(modifier) {
        SectionTitle(
            stringResource(com.dishnest.app.R.string.ui_a_calmer_week_188aa5),
            stringResource(R.string.plan_meals),
            { onNavigate("plan") },
        )
        val start = Planner.week(LocalDate.now())
        (0..2).forEach { d ->
            val date = start.plusDays(d.toLong())
            val meals = s.meals.filter { it.date == date.toString() }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    date.format(DateTimeFormatter.ofPattern("EEE")),
                    color = Sage,
                    modifier = Modifier.width(36.dp),
                )
                Text(
                    meals.firstOrNull()?.let { m -> s.recipes.find { it.id == m.recipeId }?.title }
                        ?: stringResource(R.string.meal_inspiration),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (meals.isEmpty()) Muted else Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(color = Line)
        }
    }
}

@Composable
private fun PantryPreview(s: KitchenState, onNavigate: (String) -> Unit, modifier: Modifier) {
    val uiResources = LocalContext.current.resources
    SoftCard(modifier) {
        SectionTitle(
            stringResource(com.dishnest.app.R.string.ui_good_food_less_waste_4eb429),
            stringResource(R.string.open_pantry),
            { onNavigate("pantry") },
        )
        val soon =
            s.pantry.filter {
                it.expiry.isNotEmpty() && it.expiry <= LocalDate.now().plusDays(3).toString()
            }
        if (soon.isEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(
                    com.dishnest.app.R.string.ui_a_little_pantry_check_goes_a_lon_d0a60c
                ),
                color = Muted,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(
                    com.dishnest.app.R.string.ui_keep_track_of_what_you_already_h_9f24a1
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
        soon.take(3).forEach { p ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(p.name.replaceFirstChar { it.titlecase() })
                Text(
                    uiResources.getString(R.string.check_date_value, p.expiry),
                    style = MaterialTheme.typography.bodySmall,
                    color = Terra,
                )
            }
            HorizontalDivider(color = Line)
        }
    }
}

@Composable
fun RecipesScreen(
    recipes: List<Recipe>,
    onRecipe: (Recipe) -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onFavourite: (Recipe) -> Unit,
) {
    val uiResources = androidx.compose.ui.platform.LocalContext.current.resources

    var query by rememberSaveable { mutableStateOf("") }
    var collection by rememberSaveable { mutableStateOf<String?>(null) }
    var favouritesOnly by rememberSaveable { mutableStateOf(false) }
    var quick by rememberSaveable { mutableStateOf(false) }
    var cuisine by rememberSaveable { mutableStateOf("") }
    var tag by rememberSaveable { mutableStateOf("") }
    var difficulty by rememberSaveable { mutableStateOf("") }
    var sortByTitle by rememberSaveable { mutableStateOf(false) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    val collections = recipes.map { it.collection }.filter { it.isNotBlank() }.distinct().sorted()
    val visible = RecipeQuery(query, collection, favouritesOnly, if (quick) 30 else null,
        cuisine, tag, difficulty, sortByTitle).apply(recipes)
    if (filtersOpen) FullDialog({ filtersOpen = false }, stringResource(R.string.recipe_filters)) {
        listOf(
            Triple(uiResources.getString(R.string.ui_cuisine_6814ff), recipes.map { it.cuisine }, cuisine),
            Triple(uiResources.getString(R.string.ui_dietary_tags_comma_separated_7222ad), recipes.flatMap { it.tags }, tag),
            Triple(uiResources.getString(R.string.ui_difficulty_be4413), recipes.map { it.difficulty }, difficulty),
        ).forEachIndexed { index, (label, values, selected) ->
            Text(label, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("") + values.filter { it.isNotBlank() }.distinct().sorted()).forEach { value ->
                    FilterChip(selected == value, {
                        when (index) { 0 -> cuisine = value; 1 -> tag = value; else -> difficulty = value }
                    }, label = { Text(value.ifEmpty { uiResources.getString(R.string.any_filter) }) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!sortByTitle, { sortByTitle = false }, label = { Text(stringResource(R.string.sort_recent)) })
            FilterChip(sortByTitle, { sortByTitle = true }, label = { Text(stringResource(R.string.sort_title)) })
        }
        OutlinedButton(onClick = {
            collection = null; favouritesOnly = false; quick = false
            cuisine = ""; tag = ""; difficulty = ""; sortByTitle = false; query = ""
        }) { Text(stringResource(R.string.clear_filters)) }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            PageTitle(
                stringResource(com.dishnest.app.R.string.ui_collected_with_love_4b0a34),
                uiResources.getString(com.dishnest.app.R.string.copy_e5e04f3aa7),
                uiResources.getString(com.dishnest.app.R.string.copy_241171761e),
                action = {
                    Button(onClick = onAdd) {
                        Icon(Icons.Outlined.Add, null)
                        Text(stringResource(com.dishnest.app.R.string.ui_add_recipe_8d014e))
                    }
                },
            )
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    query,
                    { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.search)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                OutlinedButton(onClick = onImport) {
                    Icon(Icons.Outlined.FileDownload, null)
                    Text(stringResource(com.dishnest.app.R.string.ui_import_2cff9b))
                }
            }
        }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(collection == null && !favouritesOnly, { collection = null; favouritesOnly = false },
                    label = { Text(stringResource(R.string.copy_c7bb32b3d5)) })
                FilterChip(favouritesOnly, { favouritesOnly = !favouritesOnly },
                    label = { Text(stringResource(R.string.copy_d97d51d370)) })
                collections.forEach { c ->
                    FilterChip(collection == c, { collection = if (collection == c) null else c }, label = { Text(c) })
                }
                AssistChip(onClick = { filtersOpen = true }, label = { Text(stringResource(R.string.recipe_filters)) })
                FilterChip(
                    quick,
                    { quick = !quick },
                    label = {
                        Text(stringResource(com.dishnest.app.R.string.ui_under_30_min_c9922c))
                    },
                )
            }
        }
        if (visible.isEmpty())
            item {
                EmptyState(
                    stringResource(
                        com.dishnest.app.R.string.ui_a_fresh_page_in_your_recipe_book_91f362
                    ),
                    uiResources.getString(com.dishnest.app.R.string.copy_b0059bc190),
                    uiResources.getString(com.dishnest.app.R.string.copy_8d014e1c45),
                    onAdd,
                )
            }
        item {
            BoxWithConstraints {
                val columns = if (maxWidth > 900.dp) 3 else if (maxWidth > 540.dp) 2 else 1
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    visible.chunked(columns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            row.forEach { r ->
                                RecipeCard(
                                    r,
                                    { onRecipe(r) },
                                    { onFavourite(r) },
                                    Modifier.weight(1f),
                                )
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

fun shareText(context: android.content.Context, title: String, text: String) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, title)
                .putExtra(Intent.EXTRA_TEXT, text),
            "Share from DishNest",
        )
    )
}
