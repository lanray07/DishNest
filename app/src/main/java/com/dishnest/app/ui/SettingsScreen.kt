package com.dishnest.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dishnest.app.*
import com.dishnest.app.R
import java.time.LocalDate

@Composable
fun SettingsScreen(s: KitchenState, vm: KitchenViewModel, onPremium: () -> Unit) {
    var noticesOpen by remember { mutableStateOf(false) }
    val uiResources = androidx.compose.ui.platform.LocalContext.current.resources

    val billingState by vm.billing.status.collectAsStateWithLifecycle()
    var usageEnabled by remember { mutableStateOf(vm.metrics.enabled) }
    var delete by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null)
                vm.run {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                        it.write(vm.export())
                    } ?: error(uiResources.getString(com.dishnest.app.R.string.copy_672e21cbc1))
                    vm.message.value =
                        uiResources.getString(com.dishnest.app.R.string.copy_a985a43f15)
                }
        }
    val textExport =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {
            uri ->
            if (uri != null)
                vm.run {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                        it.write(
                            s.recipes.joinToString("\n\n————————\n\n", transform = ::recipeText)
                        )
                    } ?: error(uiResources.getString(com.dishnest.app.R.string.copy_5ecc81d67a))
                    vm.message.value =
                        uiResources.getString(com.dishnest.app.R.string.copy_89ea751a62)
                }
        }
    val read =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                vm.run {
                    importText =
                        context.contentResolver.openInputStream(uri)?.use {
                            String(it.readBytesBounded(10_000_000), Charsets.UTF_8)
                        } ?: error(uiResources.getString(com.dishnest.app.R.string.copy_cf31825cce))
                }
        }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            PageTitle(
                stringResource(com.dishnest.app.R.string.ui_make_yourself_at_home_a0548a),
                uiResources.getString(com.dishnest.app.R.string.copy_553628331c),
                uiResources.getString(com.dishnest.app.R.string.copy_eacb35734f),
            )
        }
        item {
            SoftCard {
                Text(
                    stringResource(com.dishnest.app.R.string.ui_your_recipe_book_e5e04f),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    pluralStringResource(R.plurals.recipe_count, s.recipes.size, s.recipes.size) +
                        " · " +
                        stringResource(
                            if (s.demo) R.string.ui_demo_kitchen_e41a43
                            else if (billingState.premium) R.string.edition_premium
                            else R.string.edition_free
                        ),
                    color = Muted,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onPremium) {
                    Text(
                        stringResource(com.dishnest.app.R.string.ui_explore_dishnest_premium_5a187f)
                    )
                }
            }
        }
        item {
            SoftCard {
                Text(
                    stringResource(com.dishnest.app.R.string.ui_keep_a_copy_e53d3b),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(
                        com.dishnest.app.R.string.ui_your_recipes_plans_pantry_and_sh_135c7c
                    ),
                    color = Muted,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = { export.launch("dishnest-${LocalDate.now()}.json") }) {
                    Icon(Icons.Outlined.FileUpload, null)
                    Text(stringResource(com.dishnest.app.R.string.ui_export_json_backup_0db468))
                }
                OutlinedButton(onClick = { textExport.launch("dishnest-recipe-book.txt") }) {
                    Text(
                        stringResource(
                            com.dishnest.app.R.string.ui_export_recipe_book_as_text_f6ad1f
                        )
                    )
                }
                OutlinedButton(
                    onClick = {
                        read.launch(
                            arrayOf("application/json", "text/plain", "application/octet-stream")
                        )
                    }
                ) {
                    Icon(Icons.Outlined.FileDownload, null)
                    Text(stringResource(com.dishnest.app.R.string.ui_import_json_backup_fe0cd1))
                }
                Text(
                    stringResource(
                        com.dishnest.app.R.string.ui_import_validates_the_entire_back_86e01c
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
        }
        item {
            SoftCard {
                Text(
                    stringResource(com.dishnest.app.R.string.ui_privacy_permissions_77c242),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(
                        com.dishnest.app.R.string.ui_no_registration_advertising_or_a_00a2f4
                    ),
                    color = Muted,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(
                        com.dishnest.app.R.string.ui_speech_recognition_and_language__01e307
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
        }
        item {
            SoftCard {
                Text(
                    stringResource(com.dishnest.app.R.string.ui_optional_usage_counts_ea8511),
                    style = MaterialTheme.typography.titleLarge,
                )
                Row {
                    Text(
                        stringResource(
                            com.dishnest.app.R.string.ui_keep_aggregate_usage_counts_on_t_a9093e
                        ),
                        Modifier.weight(1f),
                    )
                    Switch(
                        usageEnabled,
                        {
                            usageEnabled = it
                            vm.metrics.enabled = it
                        },
                    )
                }
                Text(
                    stringResource(
                        com.dishnest.app.R.string.ui_off_by_default_counts_contain_no_6ec589
                    ),
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            SoftCard {
                Text(
                    stringResource(com.dishnest.app.R.string.ui_language_accessibility_24f044),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(
                        com.dishnest.app.R.string.ui_the_app_follows_your_device_s_la_2ed1cc
                    ),
                    color = Muted,
                )
            }
        }
        item {
            SoftCard {
                Text(
                    stringResource(com.dishnest.app.R.string.ui_explore_dishnest_b5a56c),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(
                        com.dishnest.app.R.string.ui_demo_recipes_are_original_exampl_069377
                    ),
                    color = Muted,
                )
                OutlinedButton(
                    onClick = { vm.loadDemo() },
                    enabled = s.recipes.isEmpty() && s.pantry.isEmpty(),
                ) {
                    Text(stringResource(com.dishnest.app.R.string.ui_load_demo_kitchen_df8474))
                }
            }
        }
        item {
            SoftCard {
                Text(
                    stringResource(com.dishnest.app.R.string.ui_a_clean_slate_cc5380),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(
                        com.dishnest.app.R.string.ui_permanently_remove_all_local_rec_62eb3a
                    ),
                    color = Muted,
                )
                TextButton(onClick = { delete = true }) {
                    Icon(Icons.Outlined.DeleteOutline, null)
                    Text(
                        stringResource(com.dishnest.app.R.string.ui_delete_all_kitchen_data_013193)
                    )
                }
            }
        }
        item {
            TextButton(onClick = { noticesOpen = true }) {
                Text(stringResource(R.string.third_party_notices))
            }
        }
        item {
            Text(
                stringResource(
                    com.dishnest.app.R.string.ui_dishnest_0_1_0_development_previ_c14494
                ),
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (noticesOpen)
        FullDialog(
            onDismiss = { noticesOpen = false },
            title = stringResource(R.string.third_party_notices),
        ) {
            val content = remember {
                context.assets.open("third-party-notices.txt").bufferedReader().use {
                    it.readText()
                }
            }
            SelectionContainer { Text(content, style = MaterialTheme.typography.bodySmall) }
        }
    if (delete)
        AlertDialog(
            onDismissRequest = { delete = false },
            title = {
                Text(stringResource(com.dishnest.app.R.string.ui_delete_all_kitchen_data_3aa3db))
            },
            text = {
                Text(
                    stringResource(
                        com.dishnest.app.R.string.ui_this_cannot_be_undone_export_a_b_dfe37b
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteAll()
                        delete = false
                    }
                ) {
                    Text(stringResource(com.dishnest.app.R.string.ui_permanently_delete_9003e1))
                }
            },
            dismissButton = {
                TextButton(onClick = { delete = false }) {
                    Text(stringResource(com.dishnest.app.R.string.ui_keep_my_kitchen_79ce76))
                }
            },
        )
    importText?.let { raw ->
        AlertDialog(
            onDismissRequest = { importText = null },
            title = {
                Text(stringResource(com.dishnest.app.R.string.ui_import_this_backup_ecd47c))
            },
            text = {
                Text(
                    stringResource(
                        com.dishnest.app.R.string.ui_the_backup_will_be_validated_and_1a7df3
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.restore(raw)
                        importText = null
                    }
                ) {
                    Text(stringResource(com.dishnest.app.R.string.ui_validate_and_import_c02c3a))
                }
            },
            dismissButton = {
                TextButton(onClick = { importText = null }) {
                    Text(stringResource(com.dishnest.app.R.string.ui_cancel_19766e))
                }
            },
        )
    }
}

@Composable
fun PremiumDialog(vm: KitchenViewModel, onDismiss: () -> Unit) {
    val uiResources = androidx.compose.ui.platform.LocalContext.current.resources

    val state by vm.billing.status.collectAsStateWithLifecycle()
    FullDialog(onDismiss, uiResources.getString(com.dishnest.app.R.string.copy_a25d1bda32)) {
        Icon(Icons.Outlined.AutoAwesome, null, tint = Terra)
        Text(
            stringResource(com.dishnest.app.R.string.ui_a_little_extra_room_for_the_way__59991a),
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            stringResource(com.dishnest.app.R.string.ui_premium_is_being_prepared_this_d_c133a3),
            color = Muted,
        )
        Text(
            stringResource(com.dishnest.app.R.string.ui_planned_premium_features_821e28),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(stringResource(com.dishnest.app.R.string.ui_unlimited_recipes_meal_plan_temp_05c133))
        state.offers.forEach { offer ->
            val cadence =
                when (offer.sku) {
                    "dishnest.premium.monthly" -> R.string.billing_monthly
                    "dishnest.premium.annual" -> R.string.billing_annual
                    else -> R.string.billing_one_time
                }
            OutlinedButton(
                onClick = { vm.billing.purchase(offer.sku) },
                enabled = state.ready && !state.busy && !state.premium,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(offer.title)
                    Text(offer.price + " · " + stringResource(cadence))
                }
            }
        }
        Surface(color = Cream) { Text(stringResource(state.message), Modifier.padding(16.dp)) }
        OutlinedButton(onClick = { vm.billing.restore() }) {
            Text(stringResource(com.dishnest.app.R.string.ui_restore_amazon_purchases_3b7842))
        }
        Text(
            stringResource(com.dishnest.app.R.string.ui_no_store_prices_free_trials_or_p_dbbbec),
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
    }
}
