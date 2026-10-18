package com.dishnest.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dishnest.app.data.RecipeImport
import com.dishnest.app.domain.Recipe
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import kotlinx.coroutines.*

@Composable
fun PhotoImport(onReview: (Recipe) -> Unit, onError: (String) -> Unit) {
    val uiResources = androidx.compose.ui.platform.LocalContext.current.resources

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    fun recognize(bitmap: Bitmap) {
        busy = true
        scope.launch {
            try {
                text =
                    withContext(Dispatchers.IO) {
                        val root = File(context.filesDir, "ocr")
                        val data = File(root, "tessdata").apply { mkdirs() }
                        val model = File(data, "eng.traineddata")
                        if (!model.exists())
                            context.assets.open("tessdata/eng.traineddata").use { input ->
                                model.outputStream().use { input.copyTo(it) }
                            }
                        val engine = TessBaseAPI()
                        try {
                            check(engine.init(root.absolutePath, "eng")) {
                                uiResources.getString(com.dishnest.app.R.string.copy_a6ebea75ab)
                            }
                            engine.setImage(bitmap)
                            engine.utF8Text.orEmpty()
                        } finally {
                            engine.recycle()
                            bitmap.recycle()
                        }
                    }
                if (text.isBlank())
                    onError(uiResources.getString(com.dishnest.app.R.string.copy_cdc99bac2c))
            } catch (e: Exception) {
                onError(
                    e.message ?: uiResources.getString(com.dishnest.app.R.string.copy_f60047d5c0)
                )
            } finally {
                busy = false
            }
        }
    }
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null)
                scope.launch {
                    runCatching {
                            withContext(Dispatchers.IO) {
                                val bytes =
                                    context.contentResolver.openInputStream(uri)?.use {
                                        it.readBytesBounded(12_000_000)
                                    }
                                        ?: error(
                                            uiResources.getString(
                                                com.dishnest.app.R.string.copy_f60047d5c0
                                            )
                                        )
                                com.dishnest.app.platform.RecipeImages.decode(bytes)
                            }
                        }
                        .onSuccess { recognize(it) }
                        .onFailure { onError(it.message.orEmpty()) }
                }
        }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) recognize(bitmap)
        }
    Text(
        stringResource(com.dishnest.app.R.string.ui_read_a_recipe_photo_on_your_devi_ea83af),
        color = Muted,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { pick.launch("image/*") }, enabled = !busy) {
            Text(stringResource(com.dishnest.app.R.string.ui_choose_photo_9c5bb7))
        }
        OutlinedButton(
            onClick = {
                runCatching { camera.launch(null) }
                    .onFailure {
                        onError(uiResources.getString(com.dishnest.app.R.string.copy_4d68fe9e8f))
                    }
            },
            enabled = !busy,
        ) {
            Text(stringResource(com.dishnest.app.R.string.ui_use_camera_25987b))
        }
    }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (text.isNotBlank()) {
        Field(
            stringResource(com.dishnest.app.R.string.ui_correct_recognised_text_add_ingr_2bc536),
            text,
            { text = it },
            lines = 12,
        )
        Button(
            onClick = {
                runCatching { RecipeImport.text(text) }
                    .onSuccess(onReview)
                    .onFailure { onError(it.message.orEmpty()) }
            }
        ) {
            Text(stringResource(com.dishnest.app.R.string.ui_review_structured_recipe_4cf14b))
        }
    }
}
