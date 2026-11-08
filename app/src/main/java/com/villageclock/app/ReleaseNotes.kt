package com.villageclock.app

import android.content.Context
import org.json.JSONException
import org.json.JSONObject
import java.util.Collections

class ReleaseNotes @Throws(JSONException::class) constructor(text: String) {
    class Highlight(json: JSONObject) { @JvmField val title: String = json.getString("title"); @JvmField val body: String = json.getString("body") }
    class Entry(json: JSONObject) {
        @JvmField val version: String = json.getString("version")
        @JvmField val title: String = json.getString("title")
        @JvmField val versionCode: Int = json.getInt("versionCode")
        @JvmField val highlights: List<Highlight> = json.getJSONArray("highlights").let { source -> Collections.unmodifiableList((0 until source.length()).map { Highlight(source.getJSONObject(it)) }) }
        @JvmField val steps: List<String> = json.optJSONArray("steps").let { source -> Collections.unmodifiableList(if (source == null) emptyList() else (0 until source.length()).map { source.getString(it) }) }
    }
    @JvmField val entries: List<Entry> = JSONObject(text).getJSONArray("releases").let { source -> Collections.unmodifiableList((0 until source.length()).map { Entry(source.getJSONObject(it)) }.sortedByDescending { it.versionCode }) }
    fun current(): Entry = entries.firstOrNull { it.versionCode == BuildConfig.VERSION_CODE && it.version == BuildConfig.VERSION_NAME } ?: error("Add release notes for the installed version before publishing.")
    companion object {
        private const val SEEN = "release-guide-seen-version"
        @Volatile private var bundled: ReleaseNotes? = null
        @JvmStatic @Throws(Exception::class) fun load(c: Context): ReleaseNotes = bundled ?: synchronized(ReleaseNotes::class.java) { bundled ?: c.assets.open("release-notes.json").bufferedReader(Charsets.UTF_8).use { ReleaseNotes(it.readText()) }.also { bundled = it } }
        @JvmStatic fun needsSummary(c: Context): Boolean = Store.prefs(c).getInt(SEEN, 0) < BuildConfig.VERSION_CODE
        @JvmStatic fun markSeen(c: Context) { Store.prefs(c).edit().putInt(SEEN, maxOf(BuildConfig.VERSION_CODE, Store.prefs(c).getInt(SEEN, 0))).apply() }
    }
}
