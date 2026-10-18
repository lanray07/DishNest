package com.dishnest.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import com.dishnest.app.R
import com.dishnest.app.platform.PantryReminders

@Composable
fun PantryReminderControl() {
    val context = LocalContext.current
    val reminders = remember { PantryReminders(context) }
    var enabled by remember { mutableStateOf(reminders.enabled) }
    val label = stringResource(R.string.pantry_reminders)
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        enabled = granted; reminders.enabled = granted
    }
    SoftCard {
        Row {
            Text(stringResource(R.string.pantry_reminders), modifier = Modifier.weight(1f))
            Switch(checked = enabled, modifier = Modifier.semantics { contentDescription = label }, onCheckedChange = { checked ->
                if (checked && Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    request.launch(Manifest.permission.POST_NOTIFICATIONS)
                else { enabled = checked; reminders.enabled = checked }
            })
        }
        Text(stringResource(R.string.pantry_reminder_hint), color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}
