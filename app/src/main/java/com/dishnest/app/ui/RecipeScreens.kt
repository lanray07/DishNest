package com.dishnest.app.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dishnest.app.KitchenViewModel
import com.dishnest.app.R
import com.dishnest.app.data.RecipeImport
import com.dishnest.app.domain.*
import com.dishnest.app.platform.VoiceController
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RecipeDetail(
    r: Recipe,
    onBack: () -> Unit,
    onCook: () -> Unit,
    onEdit: () -> Unit,
    onFavourite: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val uiResources = androidx.compose.ui.platform.LocalContext.current.resources

    var servings by rememberSaveable(r.id) { mutableIntStateOf(r.servings) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                    Text(stringResource(com.dishnest.app.R.string.ui_back_to_kitchen_901962))
                }
                Row {
                    IconButton(onClick = onFavourite) {
                        Icon(
                            if (r.favourite) Icons.Outlined.Favorite
                            else Icons.Outlined.FavoriteBorder,
                            uiResources.getString(com.dishnest.app.R.string.copy_e39b24993c),
                            tint = Terra,
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Outlined.Edit,
                            uiResources.getString(com.dishnest.app.R.string.copy_481682c207),
                        )
                    }
                    IconButton(onClick = onDuplicate) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            uiResources.getString(com.dishnest.app.R.string.copy_02a9db66ee),
                        )
                    }
                    IconButton(onClick = { shareText(context, r.title, recipeText(r)) }) {
                        Icon(
                            Icons.Outlined.Share,
                            uiResources.getString(com.dishnest.app.R.string.copy_5d4570598c),
                        )
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            uiResources.getString(com.dishnest.app.R.string.copy_04bf79e202),
                        )
                    }
                }
            }
        }
        item {
            FoodImage(r, Modifier.fillMaxWidth().height(245.dp).clip(RoundedCornerShape(22.dp)))
        }
        item {
            PageTitle(
                r.collection,
                r.title,
                r.description,
                action = {
                    Button(onClick = onCook, enabled = r.steps.isNotEmpty()) {
                        Icon(Icons.Outlined.PlayArrow, null)
                        Text(stringResource(R.string.cook))
                    }
                },
            )
        }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AssistChip(
                    {},
                    label = { Text(stringResource(R.string.prep_value, uiResources.duration(r.prepMinutes))) },
                    leadingIcon = { Icon(Icons.Outlined.Schedule, null) },
                )
                AssistChip({}, label = { Text(stringResource(R.string.cook_value, uiResources.duration(r.cookMinutes))) })
                AssistChip({}, label = { Text(r.difficulty) })
                r.tags.forEach { AssistChip({}, label = { Text(it) }) }
            }
        }
        item {
            SoftCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(com.dishnest.app.R.string.ui_ingredients_87675d),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Stepper(servings, { servings = it }, stringResource(R.string.ui_servings_32939b))
                }
                r.ingredients.forEach { i ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(i.name, Modifier.weight(1f))
                        Text(
                            Ingredients.display(
                                i.quantity?.times(servings.toDouble() / r.servings),
                                i.unit,
                            ),
                            color = Sage,
                        )
                    }
                    HorizontalDivider(color = Line)
                }
            }
        }
        item {
            SoftCard {
                Text(
                    stringResource(com.dishnest.app.R.string.ui_let_s_make_something_good_8f500e),
                    style = MaterialTheme.typography.titleLarge,
                )
                r.steps.forEachIndexed { index, step ->
                    Row(
                        Modifier.padding(top = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Surface(
                            color = Cream,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${index+1}", color = Sage)
                            }
                        }
                        Text(step, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        if (r.notes.isNotBlank())
            item {
                SoftCard {
                    Text(
                        stringResource(com.dishnest.app.R.string.ui_notes_from_your_kitchen_043bc7),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(r.notes)
                }
            }
        if (r.sourceUrl.startsWith("https://"))
            item {
                TextButton(
                    onClick = {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(r.sourceUrl),
                            )
                        )
                    }
                ) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null)
                    Text(stringResource(com.dishnest.app.R.string.ui_view_original_source_4dd847))
                }
            }
    }
    if (confirmDelete)
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Text(stringResource(com.dishnest.app.R.string.ui_delete_this_recipe_4a5731))
            },
            text = {
                Text(
                    stringResource(
                        com.dishnest.app.R.string.ui_this_also_removes_it_from_planne_c25d45
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    }
                ) {
                    Text(stringResource(com.dishnest.app.R.string.ui_delete_e2d0a5))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(com.dishnest.app.R.string.ui_cancel_19766e))
                }
            },
        )
}

fun recipeText(r: Recipe) =
    "${r.title}\nServes ${r.servings}\n\nIngredients\n${r.ingredients.joinToString("\n") { "${Ingredients.display(it.quantity,it.unit)} ${it.name}" }}\n\nMethod\n${r.steps.mapIndexed {i,v -> "${i+1}. $v"}.joinToString("\n")}\n\n${r.notes}\n${r.sourceUrl}"

@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, label: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange((value - 1).coerceAtLeast(1)) }, enabled = value > 1) {
            Icon(Icons.Outlined.Remove, stringResource(R.string.decrease_value, label))
        }
        Text("$value $label", style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = { onChange((value + 1).coerceAtMost(100)) }, enabled = value < 100) {
            Icon(Icons.Outlined.Add, stringResource(R.string.increase_value, label))
        }
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    lines: Int = 1,
) {
    OutlinedTextField(
        value,
        onChange,
        modifier.fillMaxWidth(),
        label = { Text(label) },
        minLines = lines,
        singleLine = lines == 1,
        shape = RoundedCornerShape(10.dp),
    )
}

@Composable
fun RecipeEditor(initial: Recipe, onDismiss: () -> Unit, onSave: (Recipe) -> Unit) {
    val uiResources = androidx.compose.ui.platform.LocalContext.current.resources

    var title by rememberSaveable(initial.id) { mutableStateOf(initial.title) }
    var description by rememberSaveable { mutableStateOf(initial.description) }
    var servings by rememberSaveable { mutableIntStateOf(initial.servings) }
    var prep by rememberSaveable {
        mutableStateOf(initial.prepMinutes.takeIf { it >= 0 }?.toString().orEmpty())
    }
    var cook by rememberSaveable {
        mutableStateOf(initial.cookMinutes.takeIf { it >= 0 }?.toString().orEmpty())
    }
    var ingredients by rememberSaveable {
        mutableStateOf(
            initial.ingredients.joinToString("\n") {
                listOfNotNull(
                        it.quantity?.let { q ->
                            java.text
                                .DecimalFormat("0.###", java.text.DecimalFormatSymbols(Locale.US))
                                .format(q)
                        },
                        it.unit.takeIf { u -> u.isNotBlank() },
                        it.name,
                    )
                    .joinToString(" ")
            }
        )
    }
    var steps by rememberSaveable { mutableStateOf(initial.steps.joinToString("\n")) }
    var collection by rememberSaveable { mutableStateOf(initial.collection) }
    var notes by rememberSaveable { mutableStateOf(initial.notes) }
    var source by rememberSaveable { mutableStateOf(initial.sourceUrl) }
    var photo by rememberSaveable { mutableStateOf(initial.photo) }
    var cuisine by rememberSaveable { mutableStateOf(initial.cuisine) }
    var tags by rememberSaveable { mutableStateOf(initial.tags.joinToString(", ")) }
    var difficulty by rememberSaveable { mutableStateOf(initial.difficulty) }
    var error by remember { mutableStateOf("") }
    val context = LocalContext.current
    val photoScope = rememberCoroutineScope()
    var photoBusy by remember { mutableStateOf(false) }
    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                photoBusy = true
                photoScope.launch {
                    try {
                        photo = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val bytes = context.contentResolver.openInputStream(uri)?.use {
                                it.readBytesBounded(12_000_000)
                            } ?: error(uiResources.getString(R.string.copy_f60047d5c0))
                            com.dishnest.app.platform.RecipePhotos(context.filesDir).import(bytes)
                        }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = failure.message.orEmpty()
                    } finally {
                        photoBusy = false
                    }
                }
            }
        }
    FullDialog(onDismiss, stringResource(if (initial.title.isBlank()) R.string.ui_add_recipe_8d014e else R.string.copy_481682c207)) {
        Text(
            stringResource(com.dishnest.app.R.string.ui_make_it_yours_34ca8a),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            stringResource(com.dishnest.app.R.string.ui_review_every_ingredient_and_step_5bcc99),
            color = Muted,
        )
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Field(
            stringResource(com.dishnest.app.R.string.ui_recipe_name_c343c4),
            title,
            { title = it },
        )
        Field(
            stringResource(com.dishnest.app.R.string.ui_a_little_description_e30ea8),
            description,
            { description = it },
            lines = 2,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(com.dishnest.app.R.string.ui_servings_32939b), Modifier.weight(1f))
            Stepper(servings, { servings = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(
                stringResource(com.dishnest.app.R.string.ui_prep_minutes_87773e),
                prep,
                { prep = it.filter(Char::isDigit) },
                Modifier.weight(1f),
            )
            Field(
                stringResource(com.dishnest.app.R.string.ui_cook_minutes_8b80a8),
                cook,
                { cook = it.filter(Char::isDigit) },
                Modifier.weight(1f),
            )
        }
        Field(
            stringResource(com.dishnest.app.R.string.ui_ingredients_one_per_line_e_g_200_d4d092),
            ingredients,
            { ingredients = it },
            lines = 5,
        )
        Field(
            stringResource(com.dishnest.app.R.string.ui_method_one_step_per_line_322349),
            steps,
            { steps = it },
            lines = 5,
        )
        Field(
            stringResource(com.dishnest.app.R.string.ui_collection_7b7907),
            collection,
            { collection = it },
        )
        Field(
            stringResource(com.dishnest.app.R.string.ui_cuisine_6814ff),
            cuisine,
            { cuisine = it },
        )
        Field(
            stringResource(com.dishnest.app.R.string.ui_dietary_tags_comma_separated_7222ad),
            tags,
            { tags = it },
        )
        Field(
            stringResource(com.dishnest.app.R.string.ui_difficulty_be4413),
            difficulty,
            { difficulty = it },
        )
        Field(
            stringResource(com.dishnest.app.R.string.ui_personal_notes_210f34),
            notes,
            { notes = it },
            lines = 2,
        )
        Field(
            stringResource(com.dishnest.app.R.string.ui_source_url_1adf66),
            source,
            { source = it },
        )
        OutlinedButton(onClick = { photoPicker.launch("image/*") }, enabled = !photoBusy) {
            Icon(Icons.Outlined.AddPhotoAlternate, null)
            Text(
                if (photo.isBlank())
                    uiResources.getString(com.dishnest.app.R.string.copy_49b8bc2c67)
                else uiResources.getString(com.dishnest.app.R.string.copy_f46668cb02)
            )
        }
        if (photoBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(
            enabled = !photoBusy,
            onClick = {
                val p = prep.toIntOrNull()
                val c = cook.toIntOrNull()
                val ing = ingredients.lines().filter { it.isNotBlank() }.map(Ingredients::parse)
                val method = steps.lines().filter { it.isNotBlank() }
                if (title.isBlank() || ing.isEmpty() || method.isEmpty())
                    error = uiResources.getString(com.dishnest.app.R.string.copy_ab00db4db2)
                else if (servings !in 1..100)
                    error = uiResources.getString(com.dishnest.app.R.string.copy_579e329ab4)
                else if (p == null || c == null || p !in 0..10080 || c !in 0..10080)
                    error = uiResources.getString(com.dishnest.app.R.string.copy_ca07f55dcb)
                else if (source.isNotBlank() && !source.startsWith("https://"))
                    error = uiResources.getString(R.string.source_https_required)
                else
                    onSave(
                        initial.copy(
                            title = title.trim(),
                            description = description.trim(),
                            servings = servings,
                            prepMinutes = p,
                            cookMinutes = c,
                            ingredients = ing,
                            steps = method,
                            collection = collection.ifBlank { "Try Soon" },
                            notes = notes,
                            sourceUrl = source,
                            photo = photo,
                            cuisine = cuisine,
                            tags = tags.split(',').map { it.trim() }.filter { it.isNotBlank() },
                            difficulty = difficulty.trim(),
                        )
                    )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(com.dishnest.app.R.string.ui_save_recipe_5622a7))
        }
    }
}

fun java.io.InputStream.readBytesBounded(maximum: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var count = 0
    while (true) {
        val n = read(buffer)
        if (n < 0) break
        count += n
        require(count <= maximum) { "File is too large (maximum ${maximum/1000000} MB)." }
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

@Composable
fun FullDialog(onDismiss: () -> Unit, title: String, content: @Composable ColumnScope.() -> Unit) {
    val uiResources = androidx.compose.ui.platform.LocalContext.current.resources

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            Modifier.widthIn(max = 720.dp).fillMaxWidth().fillMaxHeight(0.94f).imePadding(),
            shape = RoundedCornerShape(22.dp),
            color = Ivory,
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Outlined.Close,
                            uiResources.getString(com.dishnest.app.R.string.copy_7d9eb7acb1),
                        )
                    }
                }
                HorizontalDivider(color = Line)
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
fun ImportDialog(onDismiss: () -> Unit, onReview: (Recipe) -> Unit) {
    val uiResources = androidx.compose.ui.platform.LocalContext.current.resources

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var raw by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    FullDialog(onDismiss, uiResources.getString(com.dishnest.app.R.string.copy_ab524fd1c2)) {
        Text(
            stringResource(com.dishnest.app.R.string.ui_good_recipes_deserve_a_place_to__f1bd5e),
            style = MaterialTheme.typography.headlineMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                    uiResources.getString(com.dishnest.app.R.string.copy_9328724b4c),
                    uiResources.getString(com.dishnest.app.R.string.copy_47b0904ec9),
                    uiResources.getString(com.dishnest.app.R.string.copy_d84eebada9),
                )
                .forEachIndexed { i, label ->
                    FilterChip(
                        tab == i,
                        {
                            tab = i
                            error = ""
                        },
                        label = { Text(label) },
                    )
                }
        }
        if (tab == 2) {
            PhotoImport(onReview = onReview, onError = { error = it })
        } else {
            Text(
                if (tab == 0) uiResources.getString(com.dishnest.app.R.string.copy_9f472a48e0)
                else uiResources.getString(com.dishnest.app.R.string.copy_a0910e26a9),
                color = Muted,
            )
            Field(
                if (tab == 0) uiResources.getString(com.dishnest.app.R.string.copy_27809578e7)
                else "https://…",
                raw,
                { raw = it },
                lines = if (tab == 0) 10 else 1,
            )
            Button(
                onClick = {
                    busy = true
                    error = ""
                    scope.launch {
                        runCatching {
                                if (tab == 0) RecipeImport.text(raw) else RecipeImport.url(raw)
                            }
                            .onSuccess(onReview)
                            .onFailure {
                                error =
                                    it.message
                                        ?: uiResources.getString(
                                            com.dishnest.app.R.string.copy_76c65b7344
                                        )
                            }
                        busy = false
                    }
                },
                enabled = raw.isNotBlank() && !busy,
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (busy) uiResources.getString(com.dishnest.app.R.string.copy_9d3b335d3c)
                    else uiResources.getString(com.dishnest.app.R.string.copy_03ba74aae3)
                )
            }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Text(
            stringResource(com.dishnest.app.R.string.ui_always_check_quantities_serving__0d0645),
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CookingScreen(
    r: Recipe,
    timers: List<KitchenTimer>,
    vm: KitchenViewModel,
    onFinish: () -> Unit,
) {
    val uiResources = androidx.compose.ui.platform.LocalContext.current.resources

    var step by rememberSaveable(r.id) { mutableIntStateOf(0) }
    var servings by rememberSaveable(r.id) { mutableIntStateOf(r.servings) }
    var ingredients by rememberSaveable { mutableStateOf(false) }
    var timerDialog by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var commandRevision by remember { mutableIntStateOf(0) }
    var speak by rememberSaveable { mutableStateOf(false) }
    var finish by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val context = LocalContext.current
    val latestCommand =
        rememberUpdatedState<(String) -> Unit> { text ->
            commandRevision++
            vm.metrics.record("voice_used")
            when (val command = VoiceParser.parse(text, uiResources.configuration.locales[0].toLanguageTag())) {
                VoiceCommand.Next -> {
                    step = (step + 1).coerceAtMost((r.steps.size - 1).coerceAtLeast(0))
                    status = uiResources.getString(com.dishnest.app.R.string.copy_298a9207a7)
                }
                VoiceCommand.Previous -> {
                    step = (step - 1).coerceAtLeast(0)
                    status = uiResources.getString(com.dishnest.app.R.string.copy_318c1da8d9)
                }
                VoiceCommand.Repeat -> {
                    status =
                        r.steps.getOrElse(step) {
                            uiResources.getString(com.dishnest.app.R.string.copy_24486a0cb6)
                        }
                }
                VoiceCommand.Ingredients -> {
                    ingredients = true
                    status = uiResources.getString(com.dishnest.app.R.string.copy_6be2cd1944)
                }
                VoiceCommand.Finish -> onFinish()
                is VoiceCommand.Timer -> {
                    vm.timer("${r.title} · step ${step+1}", command.seconds)
                    status = uiResources.getString(R.string.timer_set, formatTime(command.seconds * 1000))
                }
                VoiceCommand.TimeLeft ->
                    status =
                        timers
                            .joinToString { "${it.label}: ${formatTime(it.remaining())}" }
                            .ifBlank {
                                uiResources.getString(com.dishnest.app.R.string.copy_9be45213e6)
                            }
                VoiceCommand.Pause -> {
                    timers.firstOrNull { !it.paused && it.remaining() > 0 }?.let { vm.pause(it) }
                    status = uiResources.getString(com.dishnest.app.R.string.copy_2f4ddffe07)
                }
                is VoiceCommand.Quantity ->
                    status =
                        r.ingredients
                            .firstOrNull { it.name.contains(command.ingredient, true) }
                            ?.let {
                                "${Ingredients.display(it.quantity?.times(servings.toDouble()/r.servings),it.unit)} ${it.name}"
                            } ?: uiResources.getString(com.dishnest.app.R.string.copy_d11fcfd774)
                VoiceCommand.Unknown ->
                    status = uiResources.getString(com.dishnest.app.R.string.copy_0070948938)
            }
        }
    val voice = remember { VoiceController(context, { latestCommand.value(it) }, { status = it }) }
    val mic =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) voice.listen()
            else status = uiResources.getString(com.dishnest.app.R.string.copy_66bf733db7)
        }
    val notifications =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) status = uiResources.getString(com.dishnest.app.R.string.copy_f15ef2ba77)
        }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) voice.stop()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) {
        (context as? Activity)?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            voice.close()
            (context as? Activity)
                ?.window
                ?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    LaunchedEffect(Unit) {
        vm.metrics.record("cooking_mode_activated")
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    LaunchedEffect(step, speak) {
        if (speak)
            voice.speak(
                r.steps.getOrElse(step) {
                    uiResources.getString(com.dishnest.app.R.string.copy_24486a0cb6)
                }
            )
    }
    LaunchedEffect(status, commandRevision) {
        // Step changes narrate the instruction above; a status phrase must not interrupt it.
        // The revision also lets consecutive repeat commands narrate the same text again.
        if (
            speak &&
                status.isNotBlank() &&
                status !in
                    listOf(
                        uiResources.getString(R.string.voice_listening),
                        uiResources.getString(R.string.voice_processing),
                        uiResources.getString(R.string.voice_focus_unavailable),
                        uiResources.getString(R.string.voice_output_unavailable),
                        uiResources.getString(com.dishnest.app.R.string.copy_298a9207a7),
                        uiResources.getString(com.dishnest.app.R.string.copy_318c1da8d9),
                    )
        )
            voice.speak(status)
    }
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { finish = true }) {
                Icon(Icons.Outlined.Close, null)
                Text(stringResource(com.dishnest.app.R.string.ui_finish_cooking_073c7a))
            }
            Text(
                r.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { speak = !speak }) {
                Icon(
                    if (speak) Icons.AutoMirrored.Outlined.VolumeUp
                    else Icons.AutoMirrored.Outlined.VolumeOff,
                    uiResources.getString(com.dishnest.app.R.string.copy_e400521d2f),
                )
            }
        }
        LinearProgressIndicator(
            progress = { if (r.steps.isEmpty()) 0f else (step + 1f) / r.steps.size },
            modifier = Modifier.fillMaxWidth(),
            color = Sage,
            trackColor = Cream,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.step_position, step + 1, r.steps.size),
                color = Sage,
                style = MaterialTheme.typography.labelLarge,
            )
            Stepper(servings, { servings = it }, stringResource(R.string.ui_servings_32939b))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                r.steps.getOrElse(step) {
                    uiResources.getString(com.dishnest.app.R.string.copy_206e1064b7)
                },
                fontSize = 30.sp,
                lineHeight = 43.sp,
                color = Ink,
            )
            if (ingredients)
                SoftCard {
                    Text(stringResource(R.string.servings_value, servings), style = MaterialTheme.typography.titleLarge)
                    r.ingredients.forEach { i ->
                        Text(
                            "${Ingredients.display(i.quantity?.times(servings.toDouble()/r.servings),i.unit)}  ${i.name}",
                            Modifier.padding(vertical = 5.dp),
                        )
                    }
                }
            timers.forEach { t ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (t.remaining(now) == 0L) Cream else Color.White,
                    border = BorderStroke(1.dp, Line),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t.label)
                            Text(
                                formatTime(t.remaining(now)) + (if (t.paused) " · " + stringResource(R.string.paused_label) else ""),
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                        IconButton(onClick = { vm.pause(t) }, enabled = t.remaining(now) > 0) {
                            Icon(
                                if (t.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                                uiResources.getString(com.dishnest.app.R.string.copy_55e1a0c898),
                            )
                        }
                        IconButton(onClick = { vm.cancel(t) }) {
                            Icon(
                                Icons.Outlined.Close,
                                uiResources.getString(com.dishnest.app.R.string.copy_357d388fdf),
                            )
                        }
                    }
                }
            }
        }
        if (status.isNotBlank())
            Text(status, color = Sage, style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.voice_examples), style = MaterialTheme.typography.bodySmall, color = Muted)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { ingredients = !ingredients }) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, null)
                Spacer(Modifier.width(7.dp))
                Text(stringResource(com.dishnest.app.R.string.ui_ingredients_87675d))
            }
            OutlinedButton(
                onClick = {
                    timerDialog = true
                    if (
                        android.os.Build.VERSION.SDK_INT >= 33 &&
                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED
                    )
                        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            ) {
                Icon(Icons.Outlined.Timer, null)
                Spacer(Modifier.width(7.dp))
                Text(stringResource(com.dishnest.app.R.string.ui_add_timer_5a6dfa))
            }
            OutlinedButton(
                onClick = {
                    voice.speak(
                        r.steps.getOrElse(step) {
                            uiResources.getString(com.dishnest.app.R.string.copy_24486a0cb6)
                        }
                    )
                }
            ) {
                Icon(Icons.AutoMirrored.Outlined.VolumeUp, null)
                Spacer(Modifier.width(7.dp))
                Text(stringResource(com.dishnest.app.R.string.ui_read_step_e03fc7))
            }
            Button(
                onClick = {
                    if (
                        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                    )
                        voice.listen()
                    else mic.launch(Manifest.permission.RECORD_AUDIO)
                }
            ) {
                Icon(Icons.Outlined.Mic, null)
                Spacer(Modifier.width(7.dp))
                Text(stringResource(com.dishnest.app.R.string.ui_tap_to_speak_8e8fa9))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(
                onClick = { step-- },
                enabled = step > 0,
                modifier = Modifier.weight(1f).height(56.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                Text(stringResource(R.string.previous))
            }
            Button(
                onClick = { if (step < r.steps.lastIndex) step++ else finish = true },
                modifier = Modifier.weight(1f).height(56.dp),
            ) {
                Text(
                    if (step < r.steps.lastIndex) stringResource(R.string.next)
                    else uiResources.getString(com.dishnest.app.R.string.copy_073c7a8f10)
                )
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
            }
        }
    }
    if (timerDialog)
        TimerDialog(
            onDismiss = { timerDialog = false },
            onSave = { label, seconds ->
                vm.timer(label, seconds)
                timerDialog = false
            },
        )
    if (finish)
        AlertDialog(
            onDismissRequest = { finish = false },
            title = {
                Text(stringResource(com.dishnest.app.R.string.ui_all_done_in_the_kitchen_a5b557))
            },
            text = {
                Text(
                    stringResource(
                        com.dishnest.app.R.string.ui_active_timers_will_continue_you__ab36a2
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        finish = false
                        onFinish()
                    }
                ) {
                    Text(stringResource(com.dishnest.app.R.string.ui_finish_cooking_073c7a))
                }
            },
            dismissButton = {
                TextButton(onClick = { finish = false }) {
                    Text(stringResource(com.dishnest.app.R.string.ui_keep_cooking_d9de3b))
                }
            },
        )
}

fun formatTime(millis: Long): String {
    val seconds = (millis + 999) / 1000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
fun TimerDialog(onDismiss: () -> Unit, onSave: (String, Long) -> Unit) {
    var label by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("10") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(com.dishnest.app.R.string.ui_give_it_a_little_time_adfe27)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(
                    stringResource(com.dishnest.app.R.string.ui_timer_label_6a43fd),
                    label,
                    { label = it },
                )
                Field(
                    stringResource(com.dishnest.app.R.string.ui_minutes_1_1440_5357e2),
                    minutes,
                    { minutes = it.filter(Char::isDigit) },
                )
                Text(
                    stringResource(
                        com.dishnest.app.R.string.ui_background_alerts_may_be_delayed_84d690
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(label, (minutes.toLongOrNull() ?: 10) * 60) },
                enabled = minutes.toLongOrNull() in 1L..1440L,
            ) {
                Text(stringResource(com.dishnest.app.R.string.ui_start_timer_4fcf31))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(com.dishnest.app.R.string.ui_cancel_19766e))
            }
        },
    )
}
