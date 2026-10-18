package com.dishnest.app.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream

/** Bounded local decoding applies all eight EXIF orientations before OCR. */
object RecipeImages {
    fun decode(bytes: ByteArray): Bitmap {
        require(bytes.size <= 12_000_000) { "Image is too large" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        val bitmap =
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: error("Unable to decode image")
        val orientation =
            runCatching {
                    ExifInterface(ByteArrayInputStream(bytes))
                        .getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
                }
                .getOrDefault(1)
        val matrix = Matrix()
        when (orientation) {
            2 -> matrix.setScale(-1f, 1f)
            3 -> matrix.setRotate(180f)
            4 -> matrix.setScale(1f, -1f)
            5 -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            6 -> matrix.setRotate(90f)
            7 -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            8 -> matrix.setRotate(-90f)
        }
        return try {
            val oriented =
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (oriented !== bitmap) bitmap.recycle()
            oriented
        } catch (e: Exception) {
            bitmap.recycle()
            throw e
        }
    }
}
