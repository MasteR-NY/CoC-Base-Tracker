package com.villageclock.app

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

/** Bounded, atomically replaced exports; one private file per player tag. */
class VillageArchive(private val directory: File) {
    companion object {
        @JvmStatic fun canonical(tag: String?): String {
            require(tag != null && tag.matches(Regex("#[A-Za-z0-9]{2,16}"))) { "Invalid village tag" }
            return tag.uppercase(Locale.ROOT)
        }
    }
    private fun file(tag: String): File = File(directory, canonical(tag).substring(1) + ".json")
    @Synchronized @Throws(IOException::class) fun read(tag: String): String {
        val target = file(tag)
        if (target.length() > ExportParser.MAX_CHARS * 4L) throw IOException("Export is too large")
        return String(Files.readAllBytes(target.toPath()), Charsets.UTF_8)
    }
    @Synchronized @Throws(IOException::class) fun write(tag: String, text: String?) {
        val target = file(tag)
        if (text == null || text.length > ExportParser.MAX_CHARS) throw IOException("Export is too large")
        Files.createDirectories(directory.toPath())
        val temporary = File(directory, target.name + ".tmp")
        try {
            Files.write(temporary.toPath(), text.toByteArray(Charsets.UTF_8))
            try { Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(temporary.toPath()) }
    }
    @Synchronized @Throws(IOException::class) fun remove(tag: String) { Files.deleteIfExists(file(tag).toPath()) }
    @Synchronized fun tags(): List<String> = directory.listFiles().orEmpty().filter { it.isFile && it.name.matches(Regex("[A-Z0-9]{2,16}\\.json")) }.map { "#" + it.name.dropLast(5) }.sorted()
}
