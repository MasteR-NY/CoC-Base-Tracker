package com.villageclock.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import org.json.JSONException
import org.json.JSONObject

object OfficialUpdates {
    @JvmStatic fun show(activity: Activity) {
        try {
            val notes = activity.assets.open("official-updates.json").bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
            val entries = notes.getJSONArray("notes")
            val titles = Array(entries.length()) { entries.getJSONObject(it).getString("title") }
            AlertDialog.Builder(activity).setTitle("Game Updates · ${notes.getString("reviewed")}").setItems(titles) { _, which ->
                try { val entry = entries.getJSONObject(which); AlertDialog.Builder(activity).setTitle(entry.getString("title")).setMessage(entry.getString("date") + "\n\n" + entry.getString("summary") + "\n\nIncluded with this release; not a live feed.").setPositiveButton("Official Source") { _, _ -> open(activity, entry.optString("url")) }.setNegativeButton("Close", null).show() }
                catch (_: JSONException) { }
            }.setPositiveButton("Official Blog") { _, _ -> open(activity, notes.optString("blog")) }.setNeutralButton("Official Channels") { _, _ -> open(activity, notes.optString("channels")) }.setNegativeButton("Close", null).show()
        } catch (_: Exception) { Toast.makeText(activity, "Source notes are unavailable in this build.", Toast.LENGTH_LONG).show() }
    }
    private fun open(activity: Activity, address: String) {
        val uri = Uri.parse(address)
        if (uri.scheme != "https" || uri.host !in setOf("supercell.com", "sc-clashofclans.zendesk.com")) return
        try { activity.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)) }
        catch (_: ActivityNotFoundException) { Toast.makeText(activity, address, Toast.LENGTH_LONG).show() }
    }
}
