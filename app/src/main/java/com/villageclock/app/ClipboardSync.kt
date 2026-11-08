package com.villageclock.app

import android.content.ClipData
import android.content.Context
import java.security.MessageDigest

/** Foreground-only export detection; never follows clipboard URIs. */
object ClipboardSync {
    const val PREF = "auto-sync-on-return"
    const val LAST = "auto-sync-last-export"
    @JvmStatic fun enabled(c: Context): Boolean = Store.prefs(c).getBoolean(PREF, true)
    @JvmStatic fun candidate(clip: ClipData?): String? {
        if (clip == null || clip.itemCount != 1) return null
        val value = clip.getItemAt(0).text ?: return null
        if (value.length > ExportParser.MAX_CHARS) return null
        val text = value.toString().trim()
        return text.takeIf { it.startsWith("{") && it.contains("\"tag\"") && it.contains("\"timestamp\"") && (it.contains("\"buildings\"") || it.contains("\"buildings2\"")) }
    }
    @JvmStatic fun fingerprint(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    @JvmStatic fun newer(incoming: Village, saved: Village?): Boolean = saved == null || incoming.exportedAt > saved.exportedAt
}
