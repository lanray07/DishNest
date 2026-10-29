package com.dishnest.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dishnest.app.R
import com.dishnest.app.platform.AppLanguages

@Composable
fun LanguageDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val current = AppLanguages.selected(context)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_title)) },
        text = {
            val options = linkedMapOf("" to stringResource(R.string.language_system)) + AppLanguages.choices
            LazyColumn {
                options.forEach { (tag, label) ->
                    item(key = tag) {
                        Row(Modifier.fillMaxWidth().selectable(current == tag, role = Role.RadioButton) {
                            AppLanguages.choose(context, tag)
                            onDismiss()
                        }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RadioButton(selected = current == tag, onClick = null)
                            Text(label, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
