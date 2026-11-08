package com.villageclock.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.text.TextUtils
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.function.LongFunction
import java.util.function.LongSupplier

/** Offline Build Menu with current-hall scope and exact-level artwork. */
class PlannerActivity : Activity() {
    private lateinit var planner: UpgradePlanner
    private lateinit var village: Village
    private lateinit var raw: String
    private var base = "home"
    private var sample = false
    private var hall = 0
    private var tab = UpgradePlanner.Tab.BUILDINGS
    private val filters = mutableMapOf<String, UpgradePlanner.Filter>()
    private lateinit var root: LinearLayout
    private lateinit var top: LinearLayout
    private lateinit var bottom: LinearLayout
    private lateinit var list: ListView
    private var all: List<UpgradePlanner.Row> = emptyList()
    private var visible: List<UpgradePlanner.Row> = emptyList()
    private val display = mutableListOf<Any?>()
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshTimers = Runnable {
        if (::list.isInitialized) { val position = list.firstVisiblePosition; val offset = list.getChildAt(0)?.top ?: 0; render(); list.setSelectionFromTop(position, offset) }
    }
    override fun onResume() { super.onResume(); if (::root.isInitialized) refreshTimers.run() }
    override fun onPause() { refreshHandler.removeCallbacks(refreshTimers); super.onPause() }
    override fun onDestroy() { refreshHandler.removeCallbacksAndMessages(null); super.onDestroy() }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        try {
            planner = Store.planner(this); sample = intent.getBooleanExtra("sample", false)
            if (sample) { raw = sampleExport(); village = ExportParser.parse(raw, Store.catalog(this), System.currentTimeMillis()) }
            else { val saved = Store.load(this, intent.getStringExtra("tag")); if (saved == null) { finish(); return }; village = saved; raw = Store.raw(this, village.tag) }
            if (state != null) {
                base = state.getString("base", "home"); tab = UpgradePlanner.Tab.entries[state.getInt("tab").coerceIn(0, 3)]
                val saved = JSONObject(state.getString("filters", "{}")); for (key in saved.keys()) filters[key] = readFilter(saved.getJSONObject(key))
            }
            if (base == "builder" && tab.ordinal > 1) tab = UpgradePlanner.Tab.BUILDINGS
            shell(); render()
        } catch (_: Exception) { setContentView(label("Could not load upgrade data. Import a fresh export and try again.", 18, WHITE, true).apply { setPadding(dp(24), dp(40), dp(24), dp(24)); setBackgroundColor(INK) }) }
    }
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("base", base); out.putInt("tab", tab.ordinal)
        val saved = JSONObject(); for ((key, filter) in filters) saved.put(key, writeFilter(filter))
        out.putString("filters", saved.toString()); super.onSaveInstanceState(out)
    }
    private fun shell() {
        root = column().apply { setBackgroundColor(0xFF68452E.toInt()); setPadding(dp(8), dp(10), dp(8), dp(8)) }; setContentView(root)
        root.setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= 30) { val bars = insets.getInsets(WindowInsets.Type.systemBars()); root.setPadding(dp(8) + bars.left, dp(10) + bars.top, dp(8) + bars.right, dp(8) + bars.bottom) }
            else root.setPadding(dp(8), dp(10) + insets.systemWindowInsetTop, dp(8), dp(8) + insets.systemWindowInsetBottom)
            insets
        }
        top = column(); root.addView(top)
        list = ListView(this).apply { id = R.id.planner_list; divider = null; dividerHeight = dp(5); clipToPadding = false; setPadding(0, dp(5), 0, dp(5)) }; root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        bottom = column(); root.addView(bottom)
        list.setOnItemClickListener { parent, _, position, _ -> (parent.getItemAtPosition(position) as? UpgradePlanner.Row)?.let(::details) }
    }
    private fun filter(): UpgradePlanner.Filter = filters.getOrPut("$base:$tab") { UpgradePlanner.Filter() }
    private fun render() {
        try { all = planner.rows(raw, village, base, tab, System.currentTimeMillis()); hall = VillageOverview(raw).hall(base == "builder") } catch (_: Exception) { all = emptyList(); hall = 0 }
        refreshHandler.removeCallbacks(refreshTimers)
        val now = System.currentTimeMillis(); val next = minOf(village.nextFinish(now).takeIf { it != 0L } ?: Long.MAX_VALUE, ActiveEffects.nextChange(village, now))
        if (next != Long.MAX_VALUE) refreshHandler.postDelayed(refreshTimers, maxOf(100, next - now + 50))
        visible = UpgradePlanner.select(all, filter()); sections(); top.removeAllViews()
        top.addView(label(if (sample) "SAMPLE VILLAGE" else Store.title(this, village), 13, GOLD, true).apply { setSingleLine(true); ellipsize = TextUtils.TruncateAt.END }); space(top, 5)
        val bases = row()
        for (selected in listOf("home", "builder")) bases.addView(control(if (selected == "home") "Village Base" else "Builder Base", base == selected) { base = selected; if (base == "builder" && tab.ordinal > 1) tab = UpgradePlanner.Tab.BUILDINGS; render(); list.setSelection(0) }, LinearLayout.LayoutParams(0, dp(44), 1f))
        top.addView(bases); space(top, 7)
        val header = row().apply { setBackgroundResource(R.drawable.build_header); setPadding(dp(5), dp(4), dp(5), dp(4)) }
        header.addView(ImageButton(this).apply { id = R.id.planner_filters; setImageResource(R.drawable.ic_filter); setBackgroundResource(R.drawable.build_header); contentDescription = "Sort and filter upgrades"; setOnClickListener { showFilters() } }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(label(tab.label, 19, WHITE, true).apply { gravity = Gravity.CENTER; setShadowLayer(dp(1).toFloat(), 0f, dp(2).toFloat(), 0xFF191512.toInt()) }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(control("✕", false) { finish() }.apply { textSize = 25f; setPadding(0, 0, 0, 0); contentDescription = "Close Build Menu"; background = panel(0xFFE9534A.toInt(), 0xFF961B16.toInt()) }, LinearLayout.LayoutParams(dp(44), dp(48))); top.addView(header)
        val remaining = visible.count { !it.active() && it.next != null }
        val scope = if (filter().currentHallOnly) (if (base == "home") "Town Hall " else "Builder Hall ") + hall else "Catalog Max"
        top.addView(label("$scope · $remaining remaining groups", 12, GOLD, false).apply { id = R.id.planner_scope; setPadding(dp(5), dp(5), 0, 0) })
        val snapshot = display.toList()
        list.adapter = object : BaseAdapter() {
            override fun getCount() = snapshot.size
            override fun getItem(n: Int) = snapshot[n]
            override fun getItemId(n: Int) = n.toLong()
            override fun isEnabled(n: Int) = snapshot[n] is UpgradePlanner.Row
            override fun getViewTypeCount() = 5
            override fun getItemViewType(n: Int) = when (snapshot[n]) { is UpgradePlanner.Row -> 0; null -> 2; is ActiveEffects.Effect -> 3; is Village.Queue -> 4; else -> 1 }
            override fun getView(n: Int, old: View?, parent: ViewGroup?): View = when (val item = snapshot[n]) { is UpgradePlanner.Row -> card(item); null -> empty(); is ActiveEffects.Effect -> TimerPanels.effect(this@PlannerActivity, item); is Village.Queue -> freeRow(item); else -> section(item as String) }
        }
        bottom.removeAllViews(); val tabs = row(); val art = intArrayOf(1000015, 4000000, 90000000, 73000000)
        for (i in 0 until if (base == "builder") 2 else 4) {
            val selected = UpgradePlanner.Tab.entries[i]
            val button = control(selected.label, tab == selected) { tab = selected; render(); list.setSelection(0) }.apply { textSize = 11f; setPadding(dp(2), dp(4), dp(2), dp(4)) }
            val image = getDrawable(ItemArt.icon(this, art[i]))!!; image.setBounds(0, 0, dp(21), dp(21)); button.setCompoundDrawables(null, image, null, null)
            tabs.addView(button, LinearLayout.LayoutParams(0, dp(64), 1f))
        }
        bottom.addView(tabs); bottom.addView(label("Catalog prices before discounts · sync to refresh levels", 10, MUTED, false).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) })
    }
    private fun sections() {
        display.clear()
        val effects = ActiveEffects.active(village, System.currentTimeMillis()).filter { it.builderBase == (base == "builder") }
        if (effects.isNotEmpty()) { display.add("Active Boosts"); display.addAll(effects) }
        val queue = when { tab == UpgradePlanner.Tab.ARMY -> if (base == "builder") Village.Queue.STAR_LAB else Village.Queue.LAB; tab == UpgradePlanner.Tab.PETS -> Village.Queue.PETS; base == "builder" -> Village.Queue.BASE; else -> Village.Queue.BUILDERS }
        if (village.nextFree(queue, System.currentTimeMillis()) > 0 && tab != UpgradePlanner.Tab.EQUIPMENT) display.add(queue)
        val active = mutableListOf<UpgradePlanner.Row>(); val rest = mutableListOf<UpgradePlanner.Row>(); val halls = mutableListOf<UpgradePlanner.Row>()
        for (item in visible) when { item.active() -> active.add(item); filter().currentHallOnly && item.item.category in setOf("town-hall", "builder-hall") && item.next != null -> halls.add(item); else -> rest.add(item) }
        addSection("Current Upgrades", active); addSection(if (base == "home") "Next Town Hall" else "Next Builder Hall", halls)
        if (tab == UpgradePlanner.Tab.EQUIPMENT) { addSection("Owned Equipment", rest.filter { !it.fresh }); addSection("Not in Export", rest.filter { it.fresh }) }
        else {
            val suggested = if (filter().sort == UpgradePlanner.Sort.DEFAULT) rest.filter { it.state == UpgradePlanner.State.AVAILABLE && !it.fresh && it.item.category != "wall" }.sortedWith(compareBy<UpgradePlanner.Row> { it.seconds() }.thenBy { it.price(filter().resources) }).take(3) else emptyList()
            rest.removeAll(suggested.toSet())
            val unlocks = if (tab == UpgradePlanner.Tab.ARMY) rest.filter { it.fresh } else emptyList(); rest.removeAll(unlocks.toSet())
            addSection("Suggested Upgrades", suggested); addSection(if (suggested.isEmpty()) "Remaining Upgrades" else "Other Upgrades", rest); addSection("Unlocks / Not in Export", unlocks)
        }
        if (visible.isEmpty()) display.add(null)
    }
    private fun freeRow(queue: Village.Queue): View = TimerPanels.nextFree(this, village, queue, System.currentTimeMillis()) ?: View(this)
    private fun addSection(title: String, rows: List<UpgradePlanner.Row>) { if (rows.isNotEmpty()) { display.add(title); display.addAll(rows) } }
    private fun section(title: String) = label(title, 15, MUTED, true).apply { gravity = Gravity.CENTER; setPadding(dp(5), dp(5), dp(5), dp(5)); background = panel(0xFF4C3122.toInt(), 0xFF4C3122.toInt()) }
    private fun empty(): View = column().apply {
        setPadding(dp(20), dp(30), dp(20), dp(30))
        if (tab == UpgradePlanner.Tab.PETS && base == "home" && hall < 14 && filter().currentHallOnly) { addView(ImageView(this@PlannerActivity).apply { setImageResource(ItemArt.icon(this@PlannerActivity, 73000000)); scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(-1, dp(110))); space(this, 15); addView(label("Unlock the Pet House at Town Hall 14", 22, WHITE, true)) }
        else addView(label(if (all.isEmpty()) "This base was not included in the export." else "No upgrades match this view.", 20, WHITE, true))
        space(this, 10); addView(label("Choose another category, reset filters, or sync a fresh export. Catalog Max is available in the filter menu.", 14, MUTED, false)); space(this, 16); addView(control("Reset Filters", false) { filter().clear(); render(); list.setSelection(0) })
    }
    private fun card(item: UpgradePlanner.Row): View {
        val frame = FrameLayout(this).apply { tag = item; minimumHeight = dp(48); setBackgroundResource(R.drawable.build_card) }
        var bar: ProgressBar? = null
        if (item.active()) { bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { progressDrawable = getDrawable(R.drawable.row_bar); max = 1000; progress = VillageWidget.progress(village.exportedAt, item.finishAt, System.currentTimeMillis()); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }; frame.addView(bar, FrameLayout.LayoutParams(-1, -1)) }
        val box = column().apply { setPadding(dp(7), dp(6), dp(7), dp(6)) }; val line = row()
        val portrait = FrameLayout(this)
        portrait.addView(ImageView(this).apply { id = R.id.planner_item_icon; setImageResource(ItemArt.icon(this@PlannerActivity, item.item.id, item.targetLevel())); tag = item.targetLevel(); contentDescription = "${item.item.name} target level ${item.targetLevel()}"; scaleType = ImageView.ScaleType.FIT_CENTER }, FrameLayout.LayoutParams(-1, -1))
        portrait.addView(label(if (item.fresh) "New" else item.level.toString(), 11, WHITE, true).apply { setPadding(dp(3), 0, dp(3), 0); gravity = Gravity.CENTER; background = panel(0xFF27211A.toInt(), 0xFFBAA58A.toInt()) }, FrameLayout.LayoutParams(-2, dp(17), Gravity.BOTTOM or Gravity.START))
        line.addView(portrait, LinearLayout.LayoutParams(dp(40), dp(44)))
        val names = column().apply { setPadding(dp(7), 0, dp(3), 0) }
        names.addView(label(item.item.name + if (item.count > 1) " × ${item.count}" else "", 16, WHITE, true).apply { setShadowLayer(dp(1).toFloat(), 0f, dp(1).toFloat(), 0xFF2B1D13.toInt()); if (HiredWorkers.plannerRow(village, item)) HiredWorkers.mark(this) })
        names.addView(label(if (item.next == null && !item.active()) "Level ${item.level}" else if (item.fresh) "New · Level ${item.targetLevel()}" else "${item.level} → ${item.targetLevel()}", 11, WHITE, false))
        if (tab == UpgradePlanner.Tab.EQUIPMENT && item.item.hero.isNotEmpty()) {
            val owner = row(); val id = heroId(item.item.hero)
            if (id > 0) owner.addView(ImageView(this).apply { setImageResource(ItemArt.icon(this@PlannerActivity, id)) }, LinearLayout.LayoutParams(dp(17), dp(20)))
            owner.addView(label(item.item.hero, 10, INK, true)); names.addView(owner)
        }
        line.addView(names, LinearLayout.LayoutParams(0, -2, 1f))
        val quote = column().apply { gravity = Gravity.END; setPadding(dp(4), 0, 0, 0) }
        if (item.active()) {
            quote.addView(label("Finishes in", 10, WHITE, false))
            val timer = label("", 12, WHITE, true).apply { gravity = Gravity.END }
            val clock = label("", 10, WHITE, false).apply { gravity = Gravity.END }
            CountdownText.bind(clock, item.finishAt, "Ended", LongSupplier(System::currentTimeMillis), LongFunction { time -> ClockText.at(this, item.finishAt, time) })
            val runningBar = bar
            CountdownText.bind(timer, item.finishAt, "Sync to confirm", LongSupplier(System::currentTimeMillis), LongFunction { now -> runningBar?.progress = VillageWidget.progress(village.exportedAt, item.finishAt, now); DurationText.remaining(item.finishAt, now) })
            quote.addView(timer); quote.addView(clock)
        } else if (item.fresh && item.item.tab == UpgradePlanner.Tab.ARMY) quote.addView(label("Check Unlock", 12, INK, true))
        else if (item.next != null) {
            if (item.seconds() > 0) quote.addView(label(duration(item.seconds()) + " ◷", 12, WHITE, true).apply { gravity = Gravity.END })
            for ((resource, amount) in item.costs()) addCost(quote, amount, resource)
            if (item.costs().isEmpty()) quote.addView(label("Check in game", 12, INK, false))
        } else quote.addView(label(if (item.state == UpgradePlanner.State.MAXED) "Max Level" else "Check in game", 12, INK, true))
        line.addView(quote, LinearLayout.LayoutParams(dp(if (resources.configuration.screenWidthDp < 380) 115 else 128), -2)); box.addView(line)
        if (item.active()) {
            village.upgrades.firstOrNull { it.dataId == item.item.id && it.finishAt == item.finishAt }?.takeIf { it.hasBoosts() }?.let { upgrade -> box.addView(label("", 12, WHITE, true).also { CountdownText.bindWork(it, upgrade, "Timer ended · sync to confirm") }) }
        } else if (item.missing.isNotEmpty()) box.addView(label("Requires ${item.missing}", 11, INK, true).apply { maxLines = 2 })
        else if (item.state == UpgradePlanner.State.UNKNOWN && item.item.max() == 0 || item.state == UpgradePlanner.State.UNOWNED) box.addView(label(item.status(), 10, INK, false))
        frame.addView(box, FrameLayout.LayoutParams(-1, -2)); return frame
    }
    private fun addCost(parent: LinearLayout, amount: Long, currency: String) {
        val line = row().apply { gravity = Gravity.END }; line.addView(label(String.format(Locale.US, "%,d", amount).replace(',', ' '), 15, WHITE, true))
        for (name in currency.split(" or ")) { val art = LevelArt.currency(this, name); if (art != 0) line.addView(ImageView(this).apply { setImageResource(art); contentDescription = name; scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(dp(17), dp(19))) else line.addView(label(" $name", 10, WHITE, false)) }; parent.addView(line)
    }
    private fun heroId(name: String): Int = planner.items().firstOrNull { it.category == "hero" && it.name == name }?.id ?: 0
    private fun details(item: UpgradePlanner.Row) {
        val text = buildString {
            append(item.status()).append("\n\nCurrent level: ${item.level}")
            if (item.next != null) append("\nNext level: ${item.targetLevel()}\nBaseline cost: ${item.costText()}\nBaseline duration: ${duration(item.seconds())}")
            append("\n\nRequirements: ").append(item.requirements.ifEmpty { "Check special conditions in game." }).append("\nCurrent hall target: ").append(if (item.hallCap > 0) item.hallCap.toString() else "Unverified").append("\nCatalog max: ${item.item.max()}")
            if (item.count > 1) append("\n\nThis group contains ${item.count} items at the same level. Costs and times are per item.")
            if (item.item.seasonal() || item.item.category.endsWith("hall") || item.item.data.optString("id").contains("multi-") || item.item.data.optString("id").contains("ricochet")) append("\n\nMerge recipes, crafting, hall prerequisites and special unlock conditions require confirmation in game.")
            if (item.fresh && item.item.tab == UpgradePlanner.Tab.ARMY) append("\n\nThis army item was not included in the export. Its first catalog level is an unlock record, not a confirmed research job. Check its production-building and event conditions in game.")
            append("\n\nArtwork previews the next level. Exported levels change after a fresh sync. Prices and future times are baseline catalog values before event or Gold Pass reductions. Running timers use exported boosts. Resource balances and equipped equipment are not supplied by this export; affordability and equipped status are not inferred.\n\nSuggested upgrades are up to three owned, requirement-met upgrades with the shortest baseline durations. These are local suggestions, independent of Supercell’s recommendations.\n\nCatalog checked 8 Nov 2026 · ").append(item.item.data.optString("source", "No catalog entry"))
        }
        AlertDialog.Builder(this).setTitle(item.item.name).setMessage(text).setPositiveButton("Close", null).setNeutralButton("Open Clash") { _, _ -> startActivity(Intent(this, MainActivity::class.java).setAction("com.villageclock.app.OPEN_GAME").putExtra("tag", village.tag)) }.show()
    }
    private fun showFilters() {
        val staged = filter().copy(); val scroll = ScrollView(this); val body = column().apply { setPadding(dp(14), dp(12), dp(14), dp(12)); setBackgroundColor(0xFF805C3C.toInt()) }; scroll.addView(body)
        if (tab != UpgradePlanner.Tab.PETS && !(base == "builder" && tab == UpgradePlanner.Tab.ARMY)) {
            val resources = when { base == "builder" -> listOf("Builder Gold", "Builder Elixir"); tab == UpgradePlanner.Tab.EQUIPMENT -> listOf("Shiny Ore", "Glowing Ore", "Starry Ore"); tab == UpgradePlanner.Tab.ARMY -> listOf("Elixir", "Dark Elixir"); else -> listOf("Gold", "Elixir", "Dark Elixir") }
            grid(body, "Resource", resources, staged.resources, true, false)
            if (tab == UpgradePlanner.Tab.EQUIPMENT) grid(body, "Heroes", listOf("Barbarian King", "Archer Queen", "Minion Prince", "Grand Warden", "Royal Champion", "Dragon Duke"), staged.heroes, false, true)
            else grid(body, "Type", if (tab == UpgradePlanner.Tab.ARMY) listOf("Troops", "Spells", "Siege") else listOf("Army", "Defense", "Hero", "Resource", "New", "Permanent"), staged.types, false, false)
        }
        space(body, 12); body.addView(label("Sorting", 23, WHITE, true)); space(body, 7)
        val sort = row(); var suggestedChoice: Button? = null
        for (value in listOf(UpgradePlanner.Sort.COST_MAX, UpgradePlanner.Sort.COST_MIN)) {
            val button = filterButton(if (value == UpgradePlanner.Sort.COST_MAX) "Cost Max" else "Cost Min", staged.sort == value, null, 0)
            sort.addView(button, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
            button.setOnClickListener { staged.sort = value; suggestedChoice?.let { filterStyle(it, false) }; for (i in 0 until sort.childCount) filterStyle(sort.getChildAt(i) as Button, (i == 0) == (value == UpgradePlanner.Sort.COST_MAX)) }
        }
        body.addView(sort); space(body, 17)
        body.addView(Switch(this).apply { id = R.id.planner_current_hall; text = "Current Hall Only"; setTextColor(WHITE); textSize = 16f; isChecked = staged.currentHallOnly; minHeight = dp(48); setOnCheckedChangeListener { _, on -> staged.currentHallOnly = on } })
        val advanced = column().apply { visibility = View.GONE }
        body.addView(filterButton("More Options ▾", false, { advanced.visibility = if (advanced.visibility == View.VISIBLE) View.GONE else View.VISIBLE }, 0)); space(advanced, 9); advanced.addView(label("Status", 18, WHITE, true))
        val values = arrayOf("Remaining", "All", "Requirements met", "In progress", "Locked / unowned", "Maxed")
        val status = Spinner(this).apply { adapter = ArrayAdapter(this@PlannerActivity, android.R.layout.simple_spinner_dropdown_item, values); setSelection(values.indexOf(staged.status).coerceAtLeast(0)) }
        status.onItemSelectedListener = object : AdapterView.OnItemSelectedListener { override fun onNothingSelected(parent: AdapterView<*>?) {}; override fun onItemSelected(parent: AdapterView<*>?, view: View?, n: Int, id: Long) { staged.status = values[n] } }; advanced.addView(status)
        suggestedChoice = filterButton("Suggested Order", staged.sort == UpgradePlanner.Sort.DEFAULT, { staged.sort = UpgradePlanner.Sort.DEFAULT; suggestedChoice?.let { filterStyle(it, true) }; for (i in 0 until sort.childCount) filterStyle(sort.getChildAt(i) as Button, false) }, 0); advanced.addView(suggestedChoice)
        body.addView(advanced); space(body, 10); body.addView(label("Cost is per item. Choose one resource to compare amounts. Current upgrades stay first. Turn off Current Hall Only for Catalog Max.", 12, MUTED, false))
        AlertDialog.Builder(this).setView(scroll).setPositiveButton("Apply") { _, _ -> filters["$base:$tab"] = staged; render(); list.setSelection(0) }.setNeutralButton("Reset") { _, _ -> filter().clear(); render(); list.setSelection(0) }.setNegativeButton("Cancel", null).show()
    }
    private fun grid(body: LinearLayout, heading: String, values: List<String>, selected: MutableSet<String>, currencies: Boolean, heroes: Boolean) {
        body.addView(label(heading, 23, WHITE, true)); space(body, 7); var line: LinearLayout? = null
        for ((i, value) in values.withIndex()) {
            if (i % 3 == 0) { line = row(); body.addView(line); space(body, 5) }
            val art = if (currencies) LevelArt.currency(this, value) else if (heroes) ItemArt.icon(this, heroId(value)) else 0
            val button = filterButton(if (currencies) "" else value, value in selected, null, art).apply { contentDescription = value + if (value in selected) " selected" else "" }
            if (currencies && art != 0) { val image = getDrawable(art)!!; image.setBounds(0, 0, dp(35), dp(35)); button.text = ""; button.setCompoundDrawables(null, image, null, null); button.setPadding(0, dp(8), 0, dp(7)) }
            button.setOnClickListener { if (!selected.add(value)) selected.remove(value); filterStyle(button, value in selected); button.contentDescription = value + if (value in selected) " selected" else "" }
            requireNotNull(line).addView(button, LinearLayout.LayoutParams(0, dp(if (heroes) 82 else if (currencies) 62 else 55), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
            if (i == values.lastIndex) for (n in i % 3 + 1 until 3) line.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        }
        space(body, 8)
    }
    private fun filterButton(value: String, selected: Boolean, listener: View.OnClickListener?, art: Int): Button = control(value, selected, listener).apply { textSize = 16f; setPadding(dp(3), dp(4), dp(3), dp(4)); filterStyle(this, selected); if (art > 0) { val image = getDrawable(art)!!; image.setBounds(0, 0, dp(32), dp(32)); setCompoundDrawables(null, image, null, null); textSize = 11f } }
    private fun filterStyle(button: Button, selected: Boolean) { button.isSelected = selected; button.setTextColor(WHITE); button.background = panel(if (selected) 0xFFB89459.toInt() else 0xFF97714D.toInt(), if (selected) 0xFFFFD363.toInt() else 0xFF38281C.toInt()) }
    private fun panel(color: Int, edge: Int) = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(color, color)).apply { cornerRadius = dp(7).toFloat(); setStroke(dp(2), edge) }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
    private fun label(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(color); typeface = if (bold) resources.getFont(R.font.lilita_one) else Typeface.create("sans-serif", Typeface.NORMAL) }
    private fun control(value: String, selected: Boolean, listener: View.OnClickListener?): Button = Button(this).apply { text = value; isAllCaps = false; textSize = 15f; minWidth = 0; minimumWidth = 0; typeface = resources.getFont(R.font.lilita_one); setTextColor(if (selected) INK else WHITE); setBackgroundResource(if (selected) R.drawable.btn_primary else R.drawable.build_header); setOnClickListener(listener) }
    private fun space(parent: LinearLayout, size: Int) { parent.addView(View(this), LinearLayout.LayoutParams(1, dp(size))) }
    private fun dp(value: Int) = (resources.displayMetrics.density * value + .5f).toInt()
    companion object {
        private val WHITE = 0xFFFFF7E8.toInt(); private val GOLD = 0xFFFFD15A.toInt(); private val INK = 0xFF38281C.toInt(); private val MUTED = 0xFFE1C8AE.toInt()
        private fun writeFilter(filter: UpgradePlanner.Filter): JSONObject = JSONObject().put("resources", JSONArray(filter.resources)).put("types", JSONArray(filter.types)).put("heroes", JSONArray(filter.heroes)).put("status", filter.status).put("sort", filter.sort.name).put("hall", filter.currentHallOnly)
        private fun readFilter(json: JSONObject): UpgradePlanner.Filter = UpgradePlanner.Filter().apply {
            for ((key, target) in listOf("resources" to resources, "types" to types, "heroes" to heroes)) json.optJSONArray(key)?.let { source -> for (n in 0 until source.length()) target.add(source.optString(n)) }
            status = json.optString("status", "Remaining"); currentHallOnly = json.optBoolean("hall", true); sort = try { UpgradePlanner.Sort.valueOf(json.optString("sort", "DEFAULT")) } catch (_: Exception) { UpgradePlanner.Sort.DEFAULT }
        }
        @JvmStatic fun duration(seconds: Long): String = if (seconds == 0L) "Instant / unlock" else DurationText.seconds(seconds)
        // SAMPLE_EXPORT is filled from the previous synthetic fixture during migration.
        @JvmStatic fun sampleExport(): String = JSONObject(SAMPLE_EXPORT).put("timestamp", System.currentTimeMillis() / 1000 - 900).toString()
        private const val SAMPLE_EXPORT = "{\"tag\":\"#SAMPLE\",\"name\":\"Anonymous\",\"timestamp\":0,\"buildings\":[{\"data\":1000001,\"lvl\":15},{\"data\":1000015,\"lvl\":2,\"cnt\":5},{\"data\":1000009,\"lvl\":18,\"cnt\":3},{\"data\":1000008,\"lvl\":19,\"timer\":96000},{\"data\":1000007,\"lvl\":11},{\"data\":1000070,\"lvl\":7},{\"data\":1000071,\"lvl\":7},{\"data\":1000068,\"lvl\":4},{\"data\":1000010,\"lvl\":15,\"cnt\":50},{\"data\":1000006,\"lvl\":17},{\"data\":1000026,\"lvl\":12},{\"data\":1000020,\"lvl\":8},{\"data\":1000029,\"lvl\":8},{\"data\":1000059,\"lvl\":7}],\"heroes\":[{\"data\":28000000,\"lvl\":50}],\"units\":[{\"data\":4000000,\"lvl\":8},{\"data\":4000005,\"lvl\":8},{\"data\":4000008,\"lvl\":8}],\"equipment\":[{\"data\":90000000,\"lvl\":9},{\"data\":90000015,\"lvl\":17}],\"pets\":[{\"data\":73000000,\"lvl\":5}],\"buildings2\":[{\"data\":1000034,\"lvl\":8},{\"data\":1000044,\"lvl\":7,\"cnt\":3}],\"units2\":[{\"data\":4000031,\"lvl\":14}],\"spells\":[{\"data\":26000002,\"lvl\":5,\"timer\":60000}]}"
    }
}
