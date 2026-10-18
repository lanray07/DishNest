package com.dishnest.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dishnest.app.data.PhotoBackups
import com.dishnest.app.domain.Backup
import com.dishnest.app.domain.Recipe
import com.dishnest.app.platform.RecipePhotos
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeReadinessTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun photoArchiveUsesRealDeviceBitmapCodecs() = runBlocking {
        val root = File(context.cacheDir, "photo-device-test").apply { mkdirs() }
        try {
            val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.GREEN)
            val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            bitmap.recycle()
            val source = RecipePhotos(File(root, "source"))
            val target = RecipePhotos(File(root, "target"))
            val photo = source.import(bytes)
            val backup = Backup(recipes = listOf(Recipe(title = "Device recipe", photo = photo)),
                meals = emptyList(), pantry = emptyList(), shopping = emptyList())
            val zip = ByteArrayOutputStream()
            PhotoBackups(source).write(backup, zip)
            var restored: Backup? = null
            PhotoBackups(target).restore(ByteArrayInputStream(zip.toByteArray())) { restored = it }
            val restoredPhoto = requireNotNull(restored).recipes.single().photo
            assertNotEquals(photo, restoredPhoto)
            val actual = BitmapFactory.decodeFile(restoredPhoto)
            assertEquals(80, actual.width); assertEquals(40, actual.height)
            actual.recycle()
        } finally { root.deleteRecursively() }
    }

    @Test fun bundledOcrEngineRecognisesASyntheticPrintedFixtureOffline() {
        val root = File(context.cacheDir, "ocr-device-test").apply { mkdirs() }
        val bitmap = Bitmap.createBitmap(1200, 300, Bitmap.Config.ARGB_8888)
        val engine = TessBaseAPI()
        try {
            val model = File(root, "tessdata/eng.traineddata")
            model.parentFile!!.mkdirs()
            context.assets.open("tessdata/eng.traineddata").use { input -> model.outputStream().use { input.copyTo(it) } }
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 80f }
            canvas.drawText("LENTIL SOUP", 60f, 110f, paint)
            canvas.drawText("200 g lentils", 60f, 220f, paint)
            assertTrue(engine.init(root.absolutePath, "eng"))
            engine.setImage(bitmap)
            val text = engine.utF8Text.orEmpty()
            assertTrue("Synthetic printed fixture was not recognised", text.contains("SOUP", true) && text.contains("lentil", true))
        } finally { engine.recycle(); bitmap.recycle(); root.deleteRecursively() }
    }

    @Test fun actualResourcesFormatArabicAndHindiWithoutEnglishFallback() {
        listOf("ar", "hi").forEach { language ->
            val config = android.content.res.Configuration(context.resources.configuration)
            config.setLocale(java.util.Locale.forLanguageTag(language))
            val localized = context.createConfigurationContext(config)
            val label = localized.getString(R.string.step_position, 2, 4)
            assertFalse(label.contains("Step"))
            assertTrue(label.isNotBlank())
        }
    }
}
