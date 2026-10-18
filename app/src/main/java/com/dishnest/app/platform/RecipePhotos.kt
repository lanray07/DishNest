package com.dishnest.app.platform

import android.graphics.Bitmap
import java.io.File
import com.dishnest.app.domain.newId

/** Own only UUID-named files inside the private photos directory; never follow arbitrary paths. */
class RecipePhotos(filesDir: File) {
    val directory = File(filesDir, "photos")
    private val filename = Regex("[0-9a-fA-F-]{36}\\.jpg")

    fun owned(path: String): File? {
        if (path.isBlank()) return null
        val file = File(path)
        return file.takeIf {
            filename.matches(it.name) && it.absoluteFile.parentFile == directory.absoluteFile &&
                it.canonicalFile.parentFile == directory.canonicalFile
        }
    }

    fun import(bytes: ByteArray): String {
        val bitmap = RecipeImages.decode(bytes)
        directory.mkdirs()
        val file = File(directory, "${newId()}.jpg")
        try {
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)) }
            return file.absolutePath
        } catch (error: Exception) {
            file.delete()
            throw error
        } finally {
            bitmap.recycle()
        }
    }

    fun removeIfUnused(path: String, references: Collection<String>) {
        val file = owned(path) ?: return
        if (references.none { owned(it)?.canonicalFile == file.canonicalFile }) file.delete()
    }

    /** Keep recent unsaved drafts across recreation/process death; remove abandoned drafts after a week. */
    fun prune(references: Collection<String>, now: Long = System.currentTimeMillis()) {
        directory.listFiles()?.filter { it.isFile && now - it.lastModified() > 7 * 86400_000L }
            ?.forEach { removeIfUnused(it.absolutePath, references) }
    }
}
