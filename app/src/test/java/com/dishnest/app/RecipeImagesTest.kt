package com.dishnest.app

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import com.dishnest.app.platform.RecipeImages
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecipeImagesTest {
    @Test
    fun rotatedExifImageIsUprightBeforeOcr() {
        val bitmap = Bitmap.createBitmap(60, 30, Bitmap.Config.ARGB_8888)
        val bytes =
            ByteArrayOutputStream()
                .also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                .toByteArray()
        bitmap.recycle()
        val file = File.createTempFile("dishnest-exif", ".jpg")
        try {
            file.writeBytes(bytes)
            ExifInterface(file.absolutePath).apply {
                setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_ROTATE_90.toString(),
                )
                saveAttributes()
            }
            val decoded = RecipeImages.decode(file.readBytes())
            assertEquals(30, decoded.width)
            assertEquals(60, decoded.height)
            decoded.recycle()
        } finally {
            file.delete()
        }
    }

    @Test
    fun invalidAndOversizedImagesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            RecipeImages.decode(byteArrayOf(1, 2, 3))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecipeImages.decode(ByteArray(12_000_001))
        }
    }
}
