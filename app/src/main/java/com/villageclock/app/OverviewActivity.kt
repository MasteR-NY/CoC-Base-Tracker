package com.villageclock.app

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.function.LongFunction
import java.util.function.LongSupplier

/** Per-account progress, crafting, availability and collections. */
class OverviewActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var header: LinearLayout
    private lateinit var footer: LinearLayout
    private lateinit var list: ListView
    private lateinit var village: Village
    private lateinit var overview: VillageOverview
    private lateinit var planner: UpgradePlanner
    private var tag = ""
    private var tab = "Progress"
    private var collection = "equipment"
    private var builder = false
    private var global = false
    private var sample = false
    private val cards = mutableListOf<View>()

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        tag = intent.getStringExtra("tag").orEmpty(); sample = intent.getBooleanExtra("sample", false)
        intent.getStringExtra("tab")?.takeIf { it in listOf("Progress", "Crafting", "Helpers", "Collection") }?.let { tab = it }
        builder = intent.getBooleanExtra("builder", false)
        if (saved != null) { tab = saved.getString("tab", "Progress"); builder = saved.getBoolean("builder"); global = saved.getBoolean("global"); collection = saved.getString("collection", "equipment") }
        shell()
    }
    override fun onResume() {
        super.onResume()
        try {
            planner = Store.planner(this)
            val raw = if (sample) sampleExport() else Store.raw(this, tag)
            village = ExportParser.parse(raw, Store.catalog(this), System.currentTimeMillis()); overview = VillageOverview(raw)
            render()
            if (!sample) { Reminders.deliver(this); Reminders.schedule(this) }
        } catch (_: Exception) { cards.clear(); cards.add(label("Village unavailable. Return to the app and sync an export.", 17, TEXT, false)); showCards() }
    }
    override fun onSaveInstanceState(out: Bundle) { out.putString("tab", tab); out.putBoolean("builder", builder); out.putBoolean("global", global); out.putString("collection", collection); super.onSaveInstanceState(out) }
    private fun shell() {
        root = column().apply { setPadding(dp(12), dp(12), dp(12), dp(10)); setBackgroundColor(0xFF24180F.toInt()) }; setContentView(root)
        root.setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= 30) { val bars = insets.getInsets(WindowInsets.Type.systemBars()); root.setPadding(dp(12) + bars.left, dp(12) + bars.top, dp(12) + bars.right, dp(12) + bars.bottom) }
            else root.setPadding(dp(12), dp(12) + insets.systemWindowInsetTop, dp(12), dp(12) + insets.systemWindowInsetBottom)
            insets
        }
        header = column(); root.addView(header)
        list = ListView(this).apply { id = R.id.overview_list; divider = null; dividerHeight = dp(5); clipToPadding = false; setPadding(0, dp(6), 0, dp(6)) }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f)); footer = column(); root.addView(footer)
    }
    private fun render() {
        cards.clear(); header.removeAllViews(); footer.removeAllViews()
        val title = row()
        title.addView(button("‹", false) { finish() }, LinearLayout.LayoutParams(dp(43), dp(43)))
        title.addView(label(if (sample) "SAMPLE · OVERVIEW" else Store.title(this, village), 22, GOLD, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(dp(9), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
        title.addView(button("News", false) { OfficialUpdates.show(this) }, LinearLayout.LayoutParams(dp(55), dp(43)))
        header.addView(title); space(header, 6)
        val bases = row()
        for (selected in listOf(false, true)) bases.addView(button(if (selected) "Builder Base" else "Village Base", builder == selected) {
            builder = selected
            if (builder && collection == "equipment") collection = "skins2"
            if (!builder && collection.endsWith("2")) collection = "equipment"
            render(); list.setSelection(0)
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        header.addView(bases); space(header, 6); header.addView(label(tab, 17, GOLD, true).apply { id = R.id.overview_heading })
        when (tab) { "Progress" -> renderProgress(); "Crafting" -> renderCrafting(); "Helpers" -> renderHelpers(); else -> renderCollection() }
        val tabs = row()
        for (value in listOf("Progress", "Crafting", "Helpers", "Collection")) tabs.addView(button(value, tab == value) { tab = value; render(); list.setSelection(0) }.apply { textSize = 12f; setPadding(dp(2), 0, dp(2), 0); contentDescription = "Overview $value" }, LinearLayout.LayoutParams(0, dp(48), 1f))
        footer.addView(tabs); footer.addView(label("Synced " + Store.age(village.exportedAt, System.currentTimeMillis()), 11, MUTED, false).apply { gravity = Gravity.CENTER })
        showCards()
    }
    private fun renderProgress() {
        val choices = row()
        choices.addView(button(if (global) "Catalog Max ▾" else "Current Hall ▾", false) { global = !global; render() }, LinearLayout.LayoutParams(0, dp(42), 1f))
        choices.addView(button("ⓘ", false) { AlertDialog.Builder(this).setTitle("Level Progress").setMessage("Progress counts exported level steps, not time or cost. Current Hall targets the catalog’s standard levels and quantities for your hall, including facility gates. Catalog Max uses the full offline catalog. Running timers keep their exported level until you sync. Seasonal modules, supercharges, uncertain merges and missing catalog entries do not receive an invented target. The catalog currently ends at TH18 / BH10.").setPositiveButton("Close", null).show() }, LinearLayout.LayoutParams(dp(44), dp(42)))
        header.addView(choices)
        if (!overview.hasBase(builder)) { empty("This base was not included in the export."); return }
        for (progress in overview.progress(planner, builder, global)) {
            val card = card()
            card.addView(label(progress.name, 18, TEXT, true))
            card.addView(label(if (progress.goal > 0) "${progress.percent()}% · ${progress.maxed} / ${progress.target} at target" else "Target unavailable", 17, GOLD, true))
            if (progress.goal > 0) card.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; this.progress = progress.percent(); progressTintList = ColorStateList.valueOf(0xFF8EE04A.toInt()) }, LinearLayout.LayoutParams(-1, dp(10)))
            card.addView(label("${progress.owned} owned · ${progress.levels} / ${progress.goal} level steps", 13, MUTED, false))
            if (progress.unknown > 0) card.addView(label("${progress.unknown} items need catalog data", 12, GOLD, false))
            card.setOnClickListener { showProgressItems(progress.name) }; cards.add(card)
        }
    }
    private fun showProgressItems(group: String) {
        val text = buildString {
            for (owned in overview.owned(builder)) {
                val item = planner.item(owned.id)
                val ownedGroup = if (item == null) "Buildings" else VillageOverview.group(item)
                if (group != ownedGroup) continue
                append(item?.name ?: "Item #${owned.id}").append(" · Lv ").append(owned.level).append(" × ").append(owned.count).append('\n')
            }
        }.ifEmpty { "No owned items in this category. Targets can include catalog items not built yet." }
        AlertDialog.Builder(this).setTitle("$group · Exported Levels").setMessage(text).setPositiveButton("Close", null).show()
    }
    private fun renderCrafting() {
        if (builder) { empty("Crafted defenses belong to Village Base. Switch bases to view them."); return }
        val modules = overview.crafts()
        if (modules.isEmpty()) { empty("No crafting modules were included in this export."); return }
        header.addView(label("${modules.map { it.type }.toSet().size} defense types · ${modules.size} modules", 13, MUTED, false))
        var last = -1; var ordinal = 0
        for (module in modules) {
            if (module.type != last) { last = module.type; ordinal = 0; cards.add(label(itemName(module.type, "Defense"), 20, GOLD, true)) }; ordinal++
            val card = card(); val identity = ItemIdentities.load(this).item(module.id)
            portraitTitle(card, module.id, identity?.shortName ?: itemName(module.id, "Module $ordinal"))
            val line = row()
            if (identity != null) line.addView(ImageView(this).apply { setImageResource(ItemIdentities.moduleSymbol(identity.shortName)); contentDescription = identity.shortName }, LinearLayout.LayoutParams(dp(22), dp(22)))
            line.addView(label("Level ${module.level}" + if (identity != null && identity.cap > 0) " / ${identity.cap}" else "", 17, GOLD, true)); card.addView(line)
            val cap = identity?.cap ?: planner.item(module.id)?.max() ?: 0
            card.addView(label(if (cap > 0) "${(cap - module.level).coerceAtLeast(0)} levels to catalog max" else "Maximum level not verified", 12, MUTED, false))
            for (upgrade in village.upgrades) if (upgrade.dataId == module.id) card.addView(label("", 14, TEXT, true).also { CountdownText.bind(it, upgrade.finishAt, "Timer ended · sync to confirm") })
            card.setOnClickListener { AlertDialog.Builder(this).setTitle(itemName(module.id, "Crafting Module")).setMessage("Defense: ${itemName(module.type, "Defense")}\nModule ID: ${module.id}\nExported level: ${module.level}" + (if (cap > 0) "\nCatalog max: $cap" else "") + "\n\nOnly modules present in the export are shown. Portraits identify the defense, with a symbol for the module’s stat. Phase resets and future prices are not guessed.").setPositiveButton("Close", null).show() }; cards.add(card)
        }
    }
    private fun renderHelpers() {
        if (!sample) {
            header.addView(Switch(this).apply {
                id = R.id.availability_alerts; text = "Helper & Clock Tower Alerts"; setTextColor(TEXT); textSize = 14f; isChecked = Store.prefs(this@OverviewActivity).getBoolean(Availability.PREF, true)
                setOnCheckedChangeListener { _, on -> Store.prefs(this@OverviewActivity).edit().putBoolean(Availability.PREF, on).apply(); Reminders.deliver(this@OverviewActivity); Reminders.schedule(this@OverviewActivity)
                    if (on && (!Reminders.canNotify(this@OverviewActivity) || !Store.prefs(this@OverviewActivity).getBoolean("notify", true))) AlertDialog.Builder(this@OverviewActivity).setMessage("Turn on Notifications for all saved villages and allow Android notifications in the app’s Settings to receive availability alerts.").setPositiveButton("Close", null).show() }
            })
            if (!Reminders.canNotify(this) || !Store.prefs(this).getBoolean("notify", true)) header.addView(label("Notifications are off · enable in Settings", 12, GOLD, false))
        }
        val entries = try { Availability.parse(overview.root.toString(), village.exportedAt) } catch (_: Exception) { emptyList() }
        for (entry in entries) {
            if (builder && entry.key != "clock-tower" || !builder && entry.key == "clock-tower") continue
            val card = card(); portraitTitle(card, entry.id, entry.name)
            if (entry.level >= 0) card.addView(label("Level ${entry.level}", 13, MUTED, false))
            val status = label(entry.status(System.currentTimeMillis()), 14, GOLD, true)
            if (entry.readyAt > System.currentTimeMillis()) CountdownText.bind(status, entry.readyAt, "Ready · sync to confirm", LongSupplier(System::currentTimeMillis), LongFunction(entry::status))
            card.addView(status)
            if (entry.readyAt > 0) card.addView(label(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.readyAt)), 12, MUTED, false))
            cards.add(card)
        }
        if (cards.isEmpty()) empty("No helper or Clock Tower cooldown data was included for this base.")
        cards.add(label("Sync after using a helper or Clock Tower. These are availability estimates from your export.", 12, MUTED, false))
    }
    private fun renderCollection() {
        val keys = if (builder) listOf("skins2", "sceneries2", "decos2", "obstacles2") else listOf("equipment", "skins", "sceneries", "decos", "obstacles", "house_parts")
        val labels = if (builder) arrayOf("Hero Skins", "Sceneries", "Decorations", "Obstacles") else arrayOf("Hero Equipment", "Hero Skins", "Sceneries", "Decorations", "Obstacles", "House Parts")
        val selected = keys.indexOf(collection).coerceAtLeast(0); collection = keys[selected]
        val select = Spinner(this).apply { id = R.id.collection_selector; adapter = ArrayAdapter(this@OverviewActivity, android.R.layout.simple_spinner_dropdown_item, labels); setSelection(selected) }; header.addView(select)
        select.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { if (collection != keys[position]) { collection = keys[position]; render(); list.setSelection(0) } }
        }
        val items = overview.collection(collection)
        header.addView(label("${items.sumOf { it.count.toLong() }} owned · ${items.size} distinct entries", 13, MUTED, false))
        for (item in items) {
            val card = card(); portraitTitle(card, item.id, itemName(item.id, if (collection == "equipment") "Equipment" else "Collectible"), item.level)
            if (item.level >= 0) {
                val meta = planner.item(item.id); val cap = meta?.max() ?: 0
                card.addView(label("Level ${item.level}" + if (cap > 0) " / $cap" else "", 16, GOLD, true))
                if (meta != null && meta.hero.isNotEmpty()) card.addView(label("${meta.hero} · ${UpgradePlanner.title(meta.data.optString("rarity"))}", 12, MUTED, false))
                card.setOnClickListener { AlertDialog.Builder(this).setTitle(itemName(item.id, "Equipment")).setMessage("Exported level: ${item.level}\n" + (if (cap > 0) "Catalog max: $cap" else "Maximum level unavailable") + "\n\nEquipment costs and requirements are available in Build Menu → Hero Equipment. Ore balances are not included in the export.").setPositiveButton("Close", null).show() }
            } else {
                card.addView(label("Owned" + if (item.count > 1) " × ${item.count}" else "", 15, GOLD, true))
                card.setOnClickListener {
                    val identity = ItemIdentities.load(this).item(item.id)
                    AlertDialog.Builder(this).setTitle(itemName(item.id, "Collectible")).setMessage("Data ID: ${item.id}\nOwned quantity: ${item.count}\n\n" + when { identity == null -> "A verified name and artwork are not yet included for this ID."; identity.sharedArtwork -> "The source uses a shared name or artwork for these variants. The data ID identifies this exported record."; else -> "Name and artwork are included with the app for offline use." }).setPositiveButton("Close", null).show()
                }
            }
            cards.add(card)
        }
        if (items.isEmpty()) empty("No entries in this collection were included in the export.")
    }
    private fun itemName(id: Int, fallback: String): String = Store.catalog(this).name(id)?.takeIf { it.isNotEmpty() } ?: "$fallback #$id"
    private fun portraitTitle(card: LinearLayout, id: Int, name: String, level: Int = -1) {
        val line = row(); val icon = if (level >= 0) ItemArt.icon(this, id, level) else ItemArt.icon(this, id)
        if (icon != R.drawable.ic_village) line.addView(ImageView(this).apply { setImageResource(icon); scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = name }, LinearLayout.LayoutParams(dp(36), dp(36)))
        line.addView(label(name, 16, TEXT, true).apply { setPadding(dp(8), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f)); card.addView(line)
    }
    private fun showCards() {
        val snapshot = cards.toList()
        list.adapter = object : BaseAdapter() { override fun getCount() = snapshot.size; override fun getItem(n: Int) = snapshot[n]; override fun getItemId(n: Int) = n.toLong(); override fun getView(n: Int, old: View?, parent: ViewGroup?) = snapshot[n] }
    }
    private fun empty(value: String) { cards.add(label(value, 16, MUTED, false)) }
    private fun card() = column().apply { setPadding(dp(10), dp(8), dp(10), dp(8)); setBackgroundResource(R.drawable.card_bg) }
    private fun label(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(color); typeface = if (bold) resources.getFont(R.font.lilita_one) else Typeface.create("sans-serif", Typeface.NORMAL); setLineSpacing(dp(1).toFloat(), 1f) }
    private fun button(value: String, active: Boolean, click: View.OnClickListener) = Button(this).apply { text = value; isAllCaps = false; textSize = 13f; typeface = resources.getFont(R.font.lilita_one); setTextColor(if (active) 0xFF352108.toInt() else GOLD); setBackgroundResource(if (active) R.drawable.btn_primary else R.drawable.btn_secondary); setPadding(dp(6), 0, dp(6), 0); setOnClickListener(click) }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
    private fun space(parent: LinearLayout, size: Int) { parent.addView(View(this), LinearLayout.LayoutParams(1, dp(size))) }
    private fun dp(value: Int) = (resources.displayMetrics.density * value + .5f).toInt()
    companion object {
        private val GOLD = 0xFFFFC531.toInt(); private val TEXT = 0xFFFFF6E6.toInt(); private val MUTED = 0xFFCDBDA5.toInt()
        @JvmStatic fun sampleExport(): String {
            val root = JSONObject(PlannerActivity.sampleExport()); val types = JSONArray()
            repeat(3) { type -> val modules = JSONArray(); repeat(3) { m -> modules.put(JSONObject().put("data", 102000033 + type * 3 + m).put("lvl", m + 1)) }; types.put(JSONObject().put("data", 103000011 + type).put("modules", modules)) }
            root.getJSONArray("buildings").put(JSONObject().put("data", 1000097).put("types", types))
            val helpers = JSONArray().put(JSONObject().put("data", 93000001).put("lvl", 3).put("helper_cooldown", 7200)).put(JSONObject().put("data", 93000002).put("lvl", 1).put("helper_cooldown", 7200)).put(JSONObject().put("data", 93000003).put("lvl", 1).put("helper_cooldown", 7200))
            root.put("helpers", helpers); root.put("boosts", JSONObject().put("clocktower_cooldown", 3600)); root.put("skins", JSONArray().put(52000136).put(52000226))
            val equipment = JSONArray(); listOf(90000000, 90000001, 90000002, 90000003, 90000008, 90000016).forEachIndexed { i, id -> equipment.put(JSONObject().put("data", id).put("lvl", 9 + i)) }; root.put("equipment", equipment)
            return root.toString()
        }
    }
}
