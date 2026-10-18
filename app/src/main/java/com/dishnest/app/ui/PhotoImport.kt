package com.dishnest.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dishnest.app.R
import com.dishnest.app.data.RecipeImport
import com.dishnest.app.domain.Recipe
import com.dishnest.app.platform.*
import java.io.File
import kotlinx.coroutines.*

@Composable
fun PhotoImport(onReview: (Recipe) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var cropValues by rememberSaveable(pending) { mutableStateOf(floatArrayOf(0f, 0f, 1f, 1f)) }
    val region = CropRegion(cropValues[0], cropValues[1], cropValues[2], cropValues[3])
    fun owned(path: String): File = File(path).also {
        require(it.name.startsWith("ocr-input-") && it.parentFile?.canonicalFile == context.cacheDir.canonicalFile)
    }
    fun discard() { pending?.let { owned(it).delete() }; pending = null }
    fun recognize(bitmap: Bitmap) {
        busy = true
        scope.launch {
            try {
                text = withContext(Dispatchers.IO) { LocalOcr.read(context, bitmap) }
                if (text.isBlank()) onError(context.getString(R.string.copy_cdc99bac2c)) else discard()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { onError(failure.message ?: context.getString(R.string.copy_f60047d5c0)) }
            finally { busy = false }
        }.invokeOnCompletion { bitmap.recycle() }
    }
    fun stage(bitmap: Bitmap) {
        busy = true
        scope.launch {
            try {
                val path = withContext(Dispatchers.IO) {
                    try {
                        val file = File.createTempFile("ocr-input-", ".jpg", context.cacheDir)
                        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)) }
                        file.absolutePath
                    } finally { bitmap.recycle() }
                }
                discard()
                pending = path
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { onError(context.getString(R.string.copy_f60047d5c0)) }
            finally { busy = false }
        }
    }
    val preview by produceState<Bitmap?>(null, pending) {
        value = null
        pending?.let { path ->
            try {
                value = withContext(Dispatchers.IO) {
                    RecipeImages.decode(owned(path).inputStream().use { it.readBytesBounded(12_000_000) })
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { discard(); onError(context.getString(R.string.copy_f60047d5c0)) }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytesBounded(12_000_000) }
                            ?: error(context.getString(R.string.copy_f60047d5c0))
                        RecipeImages.decode(bytes)
                    }
                    stage(bitmap)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { busy = false; onError(context.getString(R.string.copy_f60047d5c0)) }
            }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) stage(bitmap)
    }
    Text(stringResource(R.string.ui_read_a_recipe_photo_on_your_devi_ea83af), color = Muted)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { pick.launch("image/*") }, enabled = !busy) { Text(stringResource(R.string.ui_choose_photo_9c5bb7)) }
        OutlinedButton(onClick = { runCatching { camera.launch(null) }.onFailure { onError(context.getString(R.string.copy_4d68fe9e8f)) } },
            enabled = !busy) { Text(stringResource(R.string.ui_use_camera_25987b)) }
    }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    preview?.let { bitmap ->
        if (!busy) key(pending) {
            PhotoCrop(bitmap, region, onChange = { cropValues = floatArrayOf(it.left, it.top, it.right, it.bottom) }, onRead = {
                busy = true
                scope.launch {
                    try {
                        val crop = withContext(Dispatchers.IO) { RecipeImages.crop(bitmap, region) }
                        recognize(crop)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { busy = false; onError(context.getString(R.string.copy_f60047d5c0)) }
                }
            }, onCancel = { discard() })
        }
    }
    if (text.isNotBlank()) {
        Field(stringResource(R.string.ui_correct_recognised_text_add_ingr_2bc536), text, { text = it.take(100000) }, lines = 12)
        Button(onClick = {
            runCatching { RecipeImport.text(text) }.onSuccess(onReview).onFailure { onError(it.message.orEmpty()) }
        }) { Text(stringResource(R.string.ui_review_structured_recipe_4cf14b)) }
    }
}
