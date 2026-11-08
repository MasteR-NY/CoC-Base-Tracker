package com.villageclock.app

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.util.AtomicFile
import java.io.File
import java.io.IOException

/** Existing file names and preference keys are stable across the Kotlin migration. */
object Store {
    @Volatile private var catalog: Catalog? = null
    @Volatile private var planner: UpgradePlanner? = null
    @JvmStatic @Throws(Exception::class) fun planner(c: Context): UpgradePlanner = planner ?: synchronized(Store::class.java) {
        planner ?: c.assets.open("planner-catalog.json").bufferedReader(Charsets.UTF_8).use { reader ->
            UpgradePlanner(reader.readText()).also { loaded ->
                for (entry in ItemIdentities.load(c).items()) loaded.setDisplayName(entry.id, entry.name)
                planner = loaded
            }
        }
    }
    @JvmStatic @Synchronized @Throws(IOException::class) fun raw(c: Context, tag: String): String { migrate(c); return archive(c).read(tag) }
    @JvmStatic fun prefs(context: Context): SharedPreferences = context.getSharedPreferences("village-clock", Context.MODE_PRIVATE)
    @JvmStatic fun catalog(context: Context): Catalog = catalog ?: synchronized(Store::class.java) { catalog ?: Catalog(context.applicationContext).also { catalog = it } }
    private fun archive(c: Context) = VillageArchive(File(c.filesDir, "villages"))
    private fun key(property: String, tag: String) = "$property.${VillageArchive.canonical(tag)}"
    @Synchronized private fun migrate(c: Context) {
        if (prefs(c).getBoolean("multi-village-migrated", false)) return
        val legacy = AtomicFile(File(c.filesDir, "village.json"))
        if (legacy.baseFile.exists() || File(legacy.baseFile.path + ".bak").exists()) {
            try {
                val text = String(legacy.readFully(), Charsets.UTF_8)
                val village = ExportParser.parse(text, catalog(c), System.currentTimeMillis())
                val tag = VillageArchive.canonical(village.tag)
                val existing = read(c, tag)
                if (existing == null || existing.exportedAt <= village.exportedAt) archive(c).write(tag, text)
                if (!prefs(c).contains("active-tag")) prefs(c).edit().putString("active-tag", tag).putString(key("name", tag), prefs(c).getString("name", ""))
                    .putLong(key("notified-through", tag), prefs(c).getLong("notified-through", System.currentTimeMillis()))
                    .putLong(key("last-import", tag), prefs(c).getLong("last-import", System.currentTimeMillis())).commit()
                if (prefs(c).edit().putBoolean("multi-village-migrated", true).commit()) legacy.delete()
                return
            } catch (_: Exception) { return } // Preserve the legacy file and retry later.
        }
        prefs(c).edit().putBoolean("multi-village-migrated", true).commit()
    }
    private fun read(c: Context, tag: String): Village? = try { ExportParser.parse(archive(c).read(tag), catalog(c), System.currentTimeMillis()) } catch (_: Exception) { null }
    @JvmStatic @Synchronized fun all(c: Context): List<Village> { migrate(c); return archive(c).tags().mapNotNull { read(c, it) }.sortedWith { a, b -> title(c, a).compareTo(title(c, b), ignoreCase = true) } }
    @JvmStatic @Synchronized fun activeTag(c: Context): String {
        migrate(c)
        val active = prefs(c).getString("active-tag", "").orEmpty()
        if (active.isNotEmpty() && read(c, active) != null) return active
        for (tag in archive(c).tags()) if (read(c, tag) != null) { prefs(c).edit().putString("active-tag", tag).apply(); return tag }
        return ""
    }
    @JvmStatic fun select(c: Context, tag: String) { prefs(c).edit().putString("active-tag", VillageArchive.canonical(tag)).apply() }
    @JvmStatic @Synchronized fun load(c: Context): Village? = activeTag(c).takeIf { it.isNotEmpty() }?.let { read(c, it) }
    @JvmStatic @Synchronized fun load(c: Context, tag: String?): Village? { migrate(c); return tag?.takeIf { it.isNotEmpty() }?.let { read(c, it) } }
    @JvmStatic @Synchronized @Throws(IOException::class) fun save(c: Context, text: String, village: Village) {
        migrate(c)
        val tag = VillageArchive.canonical(village.tag)
        val previous = read(c, tag)
        if (previous != null && previous.exportedAt > village.exportedAt) throw IOException("An older export cannot replace this village.")
        archive(c).write(tag, text)
        Availability.through(c, tag, System.currentTimeMillis())
        val edit = prefs(c).edit().putString("active-tag", tag).putLong(key("notified-through", tag), System.currentTimeMillis()).putLong(key("last-import", tag), System.currentTimeMillis())
        val name = village.playerName.ifEmpty { previous?.playerName.orEmpty() }
        if (name.isNotEmpty()) edit.putString(key("imported-name", tag), name)
        edit.apply()
    }
    @JvmStatic @Synchronized @Throws(IOException::class) fun clear(c: Context) {
        val tag = activeTag(c)
        if (tag.isEmpty()) return
        archive(c).remove(tag)
        prefs(c).edit().remove("availability-through.${VillageArchive.canonical(tag)}").remove(key("name", tag)).remove(key("in-game-name", tag)).remove(key("imported-name", tag)).remove(key("notified-through", tag)).remove(key("last-import", tag)).remove("active-tag").apply()
        c.getSystemService(NotificationManager::class.java)?.let { it.cancel(tag, 7); it.cancel(tag, 8) }
    }
    @JvmStatic fun rename(c: Context, tag: String, name: String?) { prefs(c).edit().putString(key("name", tag), AccountNames.clean(name)).apply() }
    @JvmStatic fun nickname(c: Context, tag: String): String = AccountNames.clean(prefs(c).getString(key("name", tag), ""))
    @JvmStatic fun setInGameName(c: Context, tag: String, name: String?) {
        val clean = AccountNames.clean(name)
        val edit = prefs(c).edit()
        if (clean.isEmpty()) edit.remove(key("in-game-name", tag)) else edit.putString(key("in-game-name", tag), clean)
        edit.apply()
    }
    @JvmStatic fun inGameName(c: Context, village: Village?): String {
        if (village == null) return ""
        return AccountNames.clean(prefs(c).getString(key("in-game-name", village.tag), "")).ifEmpty { village.playerName.ifEmpty { AccountNames.clean(prefs(c).getString(key("imported-name", village.tag), "")) } }
    }
    @JvmStatic fun title(c: Context, village: Village?): String = if (village == null) "Choose village" else inGameName(c, village).ifEmpty { nickname(c, village.tag).ifEmpty { village.tag } }
    @JvmStatic fun identity(c: Context, village: Village?): String {
        if (village == null) return "Import an account to get started"
        val alias = nickname(c, village.tag)
        return village.tag + if (inGameName(c, village).isEmpty()) " · In-game Name not set" else if (alias.isNotEmpty() && alias != title(c, village)) " · $alias" else ""
    }
    @JvmStatic fun accountLabel(c: Context, village: Village?): String = title(c, village) + " · " + identity(c, village)
    @JvmStatic fun notifiedThrough(c: Context, tag: String): Long = prefs(c).getLong(key("notified-through", tag), System.currentTimeMillis())
    @JvmStatic fun setNotifiedThrough(c: Context, tag: String, time: Long) { prefs(c).edit().putLong(key("notified-through", tag), time).apply() }
    @JvmStatic fun widgetTag(c: Context, id: Int): String {
        val pref = "widget-tag.$id"
        if (prefs(c).contains(pref)) return prefs(c).getString(pref, "").orEmpty()
        return activeTag(c).also { if (it.isNotEmpty()) bind(c, id, it) }
    }
    @JvmStatic fun bind(c: Context, id: Int, tag: String) { prefs(c).edit().putString("widget-tag.$id", VillageArchive.canonical(tag)).commit() }
    @JvmStatic fun removeWidget(c: Context, id: Int) { prefs(c).edit().remove("widget-tag.$id").apply() }
    @JvmStatic fun age(timestamp: Long, now: Long): String {
        val minutes = ((now - timestamp) / 60000).coerceAtLeast(0)
        return when { minutes == 0L -> "just now"; minutes < 60 -> "${minutes}m ago"; minutes < 1440 -> "${minutes / 60}h ago"; else -> "${minutes / 1440}d ago" }
    }
}
