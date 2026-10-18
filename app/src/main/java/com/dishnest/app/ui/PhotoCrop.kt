package com.dishnest.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.dishnest.app.R
import com.dishnest.app.platform.CropRegion
import java.text.NumberFormat

@Composable
fun PhotoCrop(bitmap: Bitmap, region: CropRegion, onChange: (CropRegion) -> Unit, onRead: () -> Unit, onCancel: () -> Unit) {
    val left = region.left; val top = region.top; val right = region.right; val bottom = region.bottom
    val format = NumberFormat.getPercentInstance(LocalConfiguration.current.locales[0])
    Text(stringResource(R.string.crop_photo), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.crop_hint), color = Muted)
    Box(Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height)) {
        Image(bitmap.asImageBitmap(), stringResource(R.string.crop_preview), Modifier.matchParentSize())
        Canvas(Modifier.matchParentSize()) {
            val l = left * size.width; val t = top * size.height
            val r = right * size.width; val b = bottom * size.height
            val shade = Color.Black.copy(alpha = 0.55f)
            drawRect(shade, Offset.Zero, Size(size.width, t))
            drawRect(shade, Offset(0f, b), Size(size.width, size.height - b))
            drawRect(shade, Offset(0f, t), Size(l, b - t))
            drawRect(shade, Offset(r, t), Size(size.width - r, b - t))
            drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(3.dp.toPx()))
        }
    }
    @Composable fun edge(label: String, value: Float, range: ClosedFloatingPointRange<Float>, change: (Float) -> Unit) {
        val percent = format.format(value)
        Text("$label · $percent")
        Slider(value = value, onValueChange = change, valueRange = range,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label; stateDescription = percent })
    }
    edge(stringResource(R.string.crop_left), left, 0f..(right - 0.05f)) { onChange(region.copy(left = it)) }
    edge(stringResource(R.string.crop_top), top, 0f..(bottom - 0.05f)) { onChange(region.copy(top = it)) }
    edge(stringResource(R.string.crop_right), right, (left + 0.05f)..1f) { onChange(region.copy(right = it)) }
    edge(stringResource(R.string.crop_bottom), bottom, (top + 0.05f)..1f) { onChange(region.copy(bottom = it)) }
    OutlinedButton(onClick = { onChange(CropRegion()) }) { Text(stringResource(R.string.crop_reset)) }
    Button(onClick = onRead) { Text(stringResource(R.string.crop_read)) }
    TextButton(onClick = onCancel) { Text(stringResource(R.string.ui_cancel_19766e)) }
}
