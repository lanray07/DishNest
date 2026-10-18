package com.dishnest.app

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import com.dishnest.app.platform.RecipeImages
import com.dishnest.app.platform.CropRegion
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
    @Test fun cropUsesPixelBoundsAndDoesNotRecycleThePreview() {
        val bitmap = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888)
        for (x in 0 until 100) for (y in 0 until 50) bitmap.setPixel(x, y,
            if (x < 50) android.graphics.Color.RED else android.graphics.Color.BLUE)
        val crop = RecipeImages.crop(bitmap, CropRegion(left = 0.5f))
        assertEquals(50, crop.width); assertEquals(50, crop.height)
        assertEquals(android.graphics.Color.BLUE, crop.getPixel(0, 0))
        crop.recycle()
        val whole = RecipeImages.crop(bitmap, CropRegion())
        assertNotSame(bitmap, whole); whole.recycle()
        assertFalse(bitmap.isRecycled)
        bitmap.recycle()
    }

    @Test fun emptyNonFiniteAndOutOfBoundsCropsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { CropRegion(left = Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { CropRegion(left = 0.8f, right = 0.2f) }
        assertThrows(IllegalArgumentException::class.java) { CropRegion(bottom = 0f) }
        assertThrows(IllegalArgumentException::class.java) { CropRegion(right = 1.1f) }
    }

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
