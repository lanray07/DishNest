package com.dishnest.app.data

import com.dishnest.app.domain.Backup
import com.dishnest.app.domain.bundledPhotoIds
import com.dishnest.app.platform.RecipePhotos
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Portable, bounded archive. Entry names are identifiers, never extraction paths. */
class PhotoBackups(private val photos: RecipePhotos) {
    private val photoEntry = Regex("photos/[0-9]+\\.jpg")
    private val maximumTotal = 64_000_000

    fun write(backup: Backup, output: OutputStream) {
        val paths = backup.recipes.map { it.photo }.filter { it.isNotEmpty() && it !in bundledPhotoIds }.distinct()
        require(paths.size <= 500) { "Too many photos in one backup." }
        val names = paths.mapIndexed { index, path -> path to "photos/$index.jpg" }.toMap()
        val portable = backup.copy(recipes = backup.recipes.map { it.copy(photo = names[it.photo] ?: it.photo) })
        val json = BackupCodec.encode(portable).toByteArray(Charsets.UTF_8)
        require(json.size <= 10_000_000) { "Backup data exceeds 10 MB." }
        var total = json.size
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("kitchen.json"))
            zip.write(json)
            zip.closeEntry()
            names.forEach { (path, name) ->
                val file = requireNotNull(photos.owned(path)) { "Reattach this recipe photo before exporting." }
                val bytes = file.inputStream().use { readBounded(it, 12_000_000) }
                total += bytes.size
                require(total <= maximumTotal) { "Photo backup exceeds 64 MB." }
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    suspend fun restore(input: InputStream, save: suspend (Backup) -> Unit) {
        val restored = mutableMapOf<String, String>()
        val seen = mutableSetOf<String>()
        var committed = false
        try {
            var json: String? = null
            var total = 0
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory && seen.add(entry.name) && seen.size <= 501) { "Invalid or duplicate backup entry." }
                    require(entry.name == "kitchen.json" || photoEntry.matches(entry.name)) { "Unexpected backup entry." }
                    val limit = minOf(if (entry.name == "kitchen.json") 10_000_000 else 12_000_000, maximumTotal - total)
                    val bytes = readBounded(zip, limit)
                    total += bytes.size
                    if (entry.name == "kitchen.json") json = bytes.toString(Charsets.UTF_8)
                    else restored[entry.name] = photos.import(bytes)
                    zip.closeEntry()
                }
            }
            val backup = BackupCodec.decode(requireNotNull(json) { "Missing kitchen.json." }, restored.keys)
            require(restored.keys == backup.recipes.map { it.photo }.filter { photoEntry.matches(it) }.toSet()) {
                "Missing or unreferenced backup photos."
            }
            withContext(NonCancellable) {
                save(backup.copy(recipes = backup.recipes.map { it.copy(photo = restored[it.photo] ?: it.photo) }))
                committed = true
            }
        } finally {
            if (!committed) restored.values.forEach { photos.removeIfUnused(it, emptyList()) }
        }
    }

    private fun readBounded(input: InputStream, maximum: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maximum) { "Backup exceeds its size limit." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
