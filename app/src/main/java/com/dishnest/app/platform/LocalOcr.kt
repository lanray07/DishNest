package com.dishnest.app.platform

import android.content.Context
import android.graphics.Bitmap
import com.dishnest.app.R
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

/** Offline English OCR. Call on an I/O dispatcher; the caller retains bitmap ownership. */
object LocalOcr {
    private val modelLock = Any()
    fun read(context: Context, bitmap: Bitmap): String {
        val root = File(context.filesDir, "ocr")
        synchronized(modelLock) {
            val data = File(root, "tessdata").apply { mkdirs() }
            val model = File(data, "eng.traineddata")
            if (!model.isFile || model.length() == 0L) {
                val temporary = File(data, "eng.traineddata.tmp")
                context.assets.open("tessdata/eng.traineddata").use { input -> temporary.outputStream().use { input.copyTo(it) } }
                check(temporary.renameTo(model)) { "Unable to prepare OCR model." }
            }
        }
        val engine = TessBaseAPI()
        try {
            check(engine.init(root.absolutePath, "eng")) { context.getString(R.string.copy_a6ebea75ab) }
            engine.setImage(bitmap)
            return engine.utF8Text.orEmpty().take(100000)
        } finally { engine.recycle() }
    }
}
