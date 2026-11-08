package com.villageclock.app

import android.Manifest
import android.app.*
import android.appwidget.AppWidgetManager
import android.content.*
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.*
import android.text.style.ForegroundColorSpan
import android.view.*
import android.widget.*
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors
import java.util.function.LongFunction
import java.util.function.LongSupplier

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val updateWorker = Executors.newSingleThreadExecutor()
    private val refresh = Runnable { render() }
    private lateinit var content: LinearLayout
    private var demo: Village? = null
    private var pendingClipboard = false
    private var importing = false
    private var resumed = false
    private var settingsExpanded = false
    private var message = ""
    private var syncTarget = ""
    private var checkingUpdates = false
    private var updateResultPending = false
    private var updateStatus = ""
    private var pendingUpdateError: String? = null
    private var pendingRelease: UpdateChecker.Release? = null
    private var clipboardListening = false
    private var autoChecking = false
    private val autoClipboard = Runnable { tryAutoClipboard() }
    private val clipboardChanged = ClipboardManager.OnPrimaryClipChangedListener { queueAutoClipboard() }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        try { if (UpdateDownloads.id(this) != -1L && UpdateChecker.compare(UpdateDownloads.version(this), BuildConfig.VERSION_NAME) <= 0) UpdateDownloads.cancel(this) } catch (_: Exception) { }
        createShell(); handleIntent(intent)
    }
    private fun createShell() {
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(BG) }
        content = vertical().apply { setPadding(dp(16), dp(18), dp(16), dp(18)) }
        scroll.addView(content); setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= 30) { val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime()); content.setPadding(dp(16) + bars.left, dp(18) + bars.top, dp(16) + bars.right, dp(18) + bars.bottom) }
            else content.setPadding(dp(22), dp(18) + insets.systemWindowInsetTop, dp(22), dp(18) + insets.systemWindowInsetBottom)
            insets
        }
    }
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        pendingClipboard = intent.getBooleanExtra("sync", false)
        val requested = intent.getStringExtra("tag")
        if (requested != null && Store.load(this, requested) != null) Store.select(this, requested)
        syncTarget = if (pendingClipboard) requested.orEmpty() else ""
        intent.removeExtra("sync")
        if (intent.action == "com.villageclock.app.OPEN_GAME") { intent.action = Intent.ACTION_MAIN; handler.post { openGame() } }
        if (intent.action == Intent.ACTION_SEND) { val text = intent.getStringExtra(Intent.EXTRA_TEXT); intent.action = Intent.ACTION_MAIN; if (text != null) importText(text) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent) }
    override fun onResume() { super.onResume(); resumed = true; updateClipboardListener(); Reminders.deliver(this); VillageWidget.refresh(this); Reminders.schedule(this); render(); if (updateResultPending) handler.post { deliverUpdateResult() } }
    override fun onPause() { resumed = false; updateClipboardListener(); handler.removeCallbacks(autoClipboard); handler.removeCallbacks(refresh); super.onPause() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); worker.shutdown(); updateWorker.shutdownNow(); super.onDestroy() }
    override fun onWindowFocusChanged(focused: Boolean) {
        super.onWindowFocusChanged(focused)
        if (focused && pendingClipboard) { pendingClipboard = false; handler.removeCallbacks(autoClipboard); handler.post { importClipboard() } }
        else if (focused) queueAutoClipboard() else handler.removeCallbacks(autoClipboard)
    }
    private fun render() {
        if (isFinishing || isDestroyed || !::content.isInitialized) return
        val scrollY = (content.parent as ScrollView).scrollY
        handler.removeCallbacks(refresh); content.removeAllViews()
        val village = demo ?: Store.load(this); val now = System.currentTimeMillis()
        content.addView(text(getString(R.string.app_title_version, getString(R.string.app_name), BuildConfig.VERSION_NAME), 30, GOLD, true).apply { id = R.id.app_version_title }); gap(content, 14)
        if (demo == null && ReleaseNotes.needsSummary(this)) releaseSummary()
        if (demo == null && Store.all(this).isNotEmpty()) { button(content, "Accounts (${Store.all(this).size}) ▾", false) { chooseVillage() }; gap(content, 10) }
        if (demo != null) { content.addView(text("SAMPLE VILLAGE · PREVIEW ONLY", 12, GOLD, true)); gap(content, 8) }
        if (message.isNotEmpty()) { gap(content, 14); content.addView(text(message, 13, GOLD, false).apply { setPadding(dp(14), dp(12), dp(14), dp(12)); background = shape(CARD, 12) }) }
        gap(content, 8)
        if (village == null) emptyState() else {
            val tools = horizontal()
            val planner = button(tools, "Build Menu", true) { startActivity(Intent(this, PlannerActivity::class.java).putExtra("tag", village.tag).putExtra("sample", demo != null)) }
            val overview = button(tools, "Overview", false) { startActivity(Intent(this, OverviewActivity::class.java).putExtra("tag", village.tag).putExtra("sample", demo != null)) }.apply { id = R.id.open_overview }
            val boosts = button(tools, "Boosts", false) { showBoosts(village) }
            for (action in listOf(planner, overview, boosts)) { action.layoutParams = LinearLayout.LayoutParams(0, dp(50), 1f).apply { setMargins(dp(2), 0, dp(2), 0) }; action.textSize = 14f; action.setPadding(dp(3), 0, dp(3), 0) }
            content.addView(tools); gap(content, 12); villageState(village, now)
        }
        gap(content, 16); button(content, if (importing) "Importing…" else "Sync / Add Account", true) { syncTarget = ""; importClipboard() }.isEnabled = !importing; gap(content, 8)
        val actions = horizontal()
        button(actions, "Add widget", false) { pinWidget() }.layoutParams = LinearLayout.LayoutParams(0, dp(50), 1f).apply { rightMargin = dp(5) }
        button(actions, "Open Clash ↗", false) { openGame() }.layoutParams = LinearLayout.LayoutParams(0, dp(50), 1f).apply { leftMargin = dp(5) }; content.addView(actions)
        if (demo != null) { gap(content, 8); button(content, "Leave sample preview", false) { demo = null; message = ""; render() } }
        else if (village == null) { gap(content, 8); button(content, "Explore a sample village", false) { demo = sample(); message = ""; render() } }
        if (demo == null) settings(village)
        gap(content, 12); button(content, "Help & About", false) { showHelp() }; gap(content, 8)
        button(content, "What’s New", false) { openReleaseGuide() }.id = R.id.whats_new; gap(content, 8)
        button(content, if (checkingUpdates) "Checking for Updates…" else "Check for Updates", false) { checkForUpdates() }.apply { id = R.id.check_updates; isEnabled = !checkingUpdates }
        if (UpdateDownloads.id(this) != -1L) { gap(content, 8); button(content, "Update Download", false) { startActivity(UpdateActivity.intent(this, null)) } }
        val note = text(updateStatus, 12, MUTED, false).apply { id = R.id.update_status; visibility = if (updateStatus.isEmpty()) View.GONE else View.VISIBLE }; gap(content, 6); content.addView(note)
        if (village != null && resumed) {
            val next = minOf(village.nextFinish(now).takeIf { it != 0L } ?: Long.MAX_VALUE, ActiveEffects.nextChange(village, now))
            if (next != Long.MAX_VALUE && next > now) handler.postDelayed(refresh, minOf(60000, maxOf(1000, next - now + 100)))
        }
        if (scrollY > 0) content.post { (content.parent as ScrollView).scrollTo(0, scrollY) }
    }
    private fun emptyState() { content.addView(card().apply { setBackgroundResource(R.drawable.widget_bg); addView(text("Your village", 24, TEXT, true)); gap(this, 10); addView(text("In Clash, copy Settings → More Settings → Export village data, then tap Sync / Add Account here.", 14, MUTED, false)) }) }
    private fun releaseSummary() {
        try {
            val release = ReleaseNotes.load(this).current(); val card = card().apply { id = R.id.release_summary }
            card.addView(text("What’s New in v${release.version}", 22, GOLD, true)); gap(card, 8); card.addView(text(release.title, 14, TEXT, false)); gap(card, 7)
            val actions = horizontal(); val read = button(actions, "Read Guide", true) { openReleaseGuide() }.apply { id = R.id.release_summary_open }
            val dismiss = button(actions, "Got It", false) { ReleaseNotes.markSeen(this); render() }.apply { id = R.id.release_summary_dismiss }
            equalButtons(read, dismiss); card.addView(actions); content.addView(card); gap(content, 14)
        } catch (_: Exception) { /* The manual guide retains its release-page fallback. */ }
    }
    private fun openReleaseGuide() { startActivity(Intent(this, WhatsNewActivity::class.java)); ReleaseNotes.markSeen(this); render() }
    private fun checkForUpdates() {
        if (checkingUpdates) return
        checkingUpdates = true; updateStatus = "Checking the latest GitHub release…"; render()
        updateWorker.execute {
            var release: UpdateChecker.Release? = null; var error: String? = null
            try { release = UpdateDownloads.fetchLatest(applicationContext) } catch (failure: UpdateChecker.CheckException) { error = failure.message } catch (failure: Exception) { error = UpdateChecker.failureDetail(failure) }
            val result = release; val failure = error; handler.post { finishUpdateCheck(result, failure) }
        }
    }
    fun finishUpdateCheck(release: UpdateChecker.Release?, error: String?) {
        if (isDestroyed || isFinishing) return
        checkingUpdates = false; pendingRelease = release; pendingUpdateError = error; updateResultPending = true
        if (resumed) deliverUpdateResult()
    }
    private fun deliverUpdateResult() {
        if (!resumed || !updateResultPending || isDestroyed || isFinishing) return
        updateResultPending = false; val offered = pendingRelease
        if (pendingUpdateError != null || offered == null) {
            val reason = pendingUpdateError ?: "Couldn’t read the release information."
            updateStatus = "Update check failed. Retry or open Releases."; render()
            val body = vertical().apply { setPadding(dp(20), dp(8), dp(20), dp(8)) }
            body.addView(text(UpdateChecker.userMessage(reason), 14, TEXT, false).apply { id = R.id.update_error_summary }); gap(body, 8)
            val technical = text(reason, 11, MUTED, false).apply { visibility = View.GONE }
            val scroll = ScrollView(this).apply { addView(technical); visibility = View.GONE }
            body.addView(Button(this).apply { text = "Technical Details ▾"; isAllCaps = false; textSize = 12f; setOnClickListener { val visible = scroll.visibility != View.VISIBLE; technical.visibility = if (visible) View.VISIBLE else View.GONE; scroll.visibility = technical.visibility } }); body.addView(scroll, LinearLayout.LayoutParams(-1, dp(180)))
            AlertDialog.Builder(this).setTitle("Update Check Unavailable").setView(body).setPositiveButton("Retry") { _, _ -> checkForUpdates() }.setNegativeButton("Close", null).setNeutralButton("Open Releases") { _, _ -> openReleases() }.show(); return
        }
        try {
            val comparison = UpdateChecker.compare(offered.version, BuildConfig.VERSION_NAME)
            if (comparison > 0) {
                updateStatus = "Update available: v${offered.version}" + if (offered.apkStatusKnown && !offered.hasApk) ". The APK has not been uploaded yet." else "."; render()
                val prompt = AlertDialog.Builder(this).setTitle("Update Available").setMessage("CoC Base Tracker v${offered.version} is available." + (if (offered.apkStatusKnown && !offered.hasApk) " The release has no downloadable APK yet." else " Download it here, then tap Install Update. Android will ask you to confirm installation.") + (if (!offered.apkStatusKnown) "\n\nThe release version is confirmed. APK availability will be checked during download." else "") + (if (offered.bytes > 0) "\n\nDownload: " + String.format(Locale.getDefault(), "%.1f MB", offered.bytes / 1048576.0) else "")).setNegativeButton("Later", null)
                if (!offered.apkStatusKnown || offered.hasApk) prompt.setPositiveButton("Download Update") { _, _ -> startActivity(UpdateActivity.intent(this, offered)) } else prompt.setPositiveButton("Open Releases") { _, _ -> openReleases() }
                prompt.show()
            } else {
                val current = comparison == 0
                updateStatus = if (current) "You’re up to date · v${BuildConfig.VERSION_NAME}" else "No newer release · installed v${BuildConfig.VERSION_NAME}, published v${offered.version}"; render()
                AlertDialog.Builder(this).setTitle(if (current) "You’re Up to Date" else "No Update Available").setMessage(if (current) "CoC Base Tracker v${BuildConfig.VERSION_NAME} is the latest published version." else "You have v${BuildConfig.VERSION_NAME} installed. GitHub currently offers v${offered.version}. Your installed build is newer.").setPositiveButton("Close", null).show()
            }
        } catch (failure: UpdateChecker.CheckException) { pendingUpdateError = failure.message; updateResultPending = true; deliverUpdateResult() }
    }
    private fun openReleases() {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.RELEASES_URL)).addCategory(Intent.CATEGORY_BROWSABLE)) }
        catch (_: ActivityNotFoundException) { AlertDialog.Builder(this).setTitle("GitHub Releases").setMessage("Open this address in a browser:\n${UpdateChecker.RELEASES_URL}").setPositiveButton("Close", null).show() }
    }

    private fun villageState(village: Village, now: Long) {
        val panel = vertical().apply { setBackgroundResource(R.drawable.widget_bg); setPadding(dp(10), dp(8), dp(10), dp(8)) }
        val header = horizontal().apply { setBackgroundResource(R.drawable.build_header); setPadding(dp(6), dp(5), dp(6), dp(5)) }
        val title = SpannableStringBuilder(Store.title(this, village)).append(" TH${village.townHall}", ForegroundColorSpan(GOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        header.addView(text("", 21, Color.WHITE, true).apply { text = title; maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setShadowLayer(dp(1.5f).toFloat(), 0f, dp(1.5f).toFloat(), 0xC0000000.toInt()) }, LinearLayout.LayoutParams(0, -2, 1f))
        button(header, "↻ Sync", true) { syncTarget = ""; importClipboard() }.apply { textSize = 16f; layoutParams = LinearLayout.LayoutParams(dp(78), dp(36)) }
        panel.addView(header); gap(panel, 8); panel.addView(text(Store.identity(this, village), 12, TEXT, false).apply { maxLines = 2 }); gap(panel, 6)
        if (demo == null && Store.inGameName(this, village).isEmpty()) { button(panel, "Set In-game Name", false) { editInGameName(village) {} }.apply { textSize = 15f; layoutParams = LinearLayout.LayoutParams(-1, dp(42)) }; gap(panel, 8) }
        if (village.upgrades.isEmpty()) panel.addView(text("No upgrades in this export. Start an upgrade in Clash, then sync again.", 14, TEXT, false))
        val effects = ActiveEffects.active(village, now)
        if (effects.isNotEmpty()) { panel.addView(text("ACTIVE BOOSTS", 16, GOLD, true)); gap(panel, 4); for (effect in effects) { panel.addView(TimerPanels.effect(this, effect)); gap(panel, 3) }; gap(panel, 7) }
        for (queue in Village.Queue.entries) {
            val jobs = village.upgrades.filter { it.queue == queue }
            if (jobs.isEmpty()) continue
            val heading = text(if (queue == Village.Queue.STAR_LAB) "STAR LAB" else VillageWidget.sectionTitle(village, queue, now), 16, GOLD, true)
            if (HiredWorkers.active(village, queue, now)) HiredWorkers.mark(heading)
            panel.addView(heading, LinearLayout.LayoutParams(-2, -2)); gap(panel, 4)
            if (queue == Village.Queue.BUILDERS && village.freeBuilders(now) > 0) { panel.addView(text("${village.freeBuilders(now)} builders idle", 14, GREEN, true).apply { setBackgroundResource(R.drawable.row_idle); setPadding(dp(9), dp(5), dp(9), dp(5)) }); gap(panel, 3) }
            TimerPanels.nextFree(this, village, queue, now)?.let { panel.addView(it); gap(panel, 3) }
            for (upgrade in jobs) { panel.addView(upgradeCard(upgrade, now, village.exportedAt)); gap(panel, 3) }; gap(panel, 8)
        }
        panel.addView(text("Synced " + Store.age(village.exportedAt, now), 12, 0xFFEFE3CF.toInt(), true).apply { maxLines = 1 }); content.addView(panel)
    }
    private fun upgradeCard(upgrade: Village.Upgrade, now: Long, snapshotAt: Long): LinearLayout {
        val card = VillageWidget.row(this, upgrade, now, snapshotAt, true, true).apply(this, FrameLayout(this)) as LinearLayout
        if (!upgrade.done(now)) {
            if (upgrade.hasBoosts()) { CountdownText.bindWork(card.findViewById(R.id.readable_timer), upgrade, "Done ✓"); CountdownText.bind(card.findViewById(R.id.row_eta), upgrade.finishAt, "Sync to confirm completion", LongSupplier(System::currentTimeMillis), LongFunction(upgrade::etaText)) }
            else CountdownText.bind(card.findViewById(R.id.readable_timer), upgrade.finishAt, "Done ✓")
            CountdownText.bind(card.findViewById(R.id.row_finish_at), upgrade.finishAt, "Ended at\n" + ClockText.at(this, upgrade.finishAt, now), LongSupplier(System::currentTimeMillis), LongFunction { time -> "Finishes at\n" + ClockText.at(this, upgrade.finishAt, time) })
        }
        card.setOnClickListener { openGame() }; return card
    }
    private fun settings(village: Village?) {
        gap(content, 12); button(content, if (settingsExpanded) "Settings ▴" else "Settings ▾", false) { settingsExpanded = !settingsExpanded; render() }.id = R.id.settings_toggle
        if (!settingsExpanded) return
        gap(content, 8); val panel = card().apply { id = R.id.settings_panel }
        settingToggle(panel, "Auto-sync on return", ClipboardSync.enabled(this), R.id.auto_sync) { checked -> Store.prefs(this).edit().putBoolean(ClipboardSync.PREF, checked).apply(); updateClipboardListener(); if (checked) queueAutoClipboard() }
        panel.addView(text("Copy in Clash, then return here to import automatically.", 11, MUTED, false)); gap(panel, 12)
        settingToggle(panel, "Live readable widget timers", LiveTimerService.enabled(this)) { checked -> Store.prefs(this).edit().putBoolean(LiveTimerService.PREF, checked).apply(); VillageWidget.refresh(this); if (checked && !Reminders.canNotify(this)) requestNotifications() }
        panel.addView(text("Ongoing notification · pauses while locked or screen off", 11, MUTED, false)); gap(panel, 12)
        button(panel, "Time Format: " + ClockText.settingLabel(this), false) {
            val labels = arrayOf("Device Default", "12-Hour (1:15 PM)", "24-Hour (13:15)"); val modes = listOf("device", "12", "24")
            AlertDialog.Builder(this).setTitle("Finish Time Format").setSingleChoiceItems(labels, modes.indexOf(ClockText.mode(this))) { dialog, index -> Store.prefs(this).edit().putString(ClockText.PREF, modes[index]).apply(); VillageWidget.refresh(this); dialog.dismiss(); render() }.setNegativeButton("Cancel", null).show()
        }.id = R.id.time_format_setting; gap(panel, 10)
        if (village != null) {
            button(panel, "In-game Name: " + Store.inGameName(this, village).ifEmpty { "Not Set" }, false) { editInGameName(village) {} }; gap(panel, 10)
            button(panel, "Account Nickname: " + Store.nickname(this, village.tag).ifEmpty { "Not Set" }, false) { rename(village) }; gap(panel, 10)
        }
        settingToggle(panel, "Notifications for all saved villages", Store.prefs(this).getBoolean("notify", true)) { checked -> Store.prefs(this).edit().putBoolean("notify", checked).apply(); if (checked) requestNotifications(); Reminders.schedule(this) }
        settingToggle(panel, "Helper & Clock Tower alerts", Store.prefs(this).getBoolean(Availability.PREF, true), R.id.availability_alerts) { on -> Store.prefs(this).edit().putBoolean(Availability.PREF, on).apply(); if (on) requestNotifications(); Reminders.deliver(this); Reminders.schedule(this) }
        if (!Reminders.canNotify(this) && Store.prefs(this).getBoolean("notify", true)) { button(panel, "Allow notifications", false) { requestNotifications() }; gap(panel, 8) }
        panel.addView(text(if (Reminders.exact(this)) "Precise alerts enabled" else "Precise alerts disabled", 11, MUTED, false))
        if (Build.VERSION.SDK_INT >= 31 && !Reminders.exact(this)) { gap(panel, 8); button(panel, "Allow precise timers", false) { try { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))) } catch (_: ActivityNotFoundException) { show("Precise alarm settings are unavailable on this device.") } } }
        if (village != null) { gap(panel, 12); button(panel, "Clear village data", false) {
            AlertDialog.Builder(this).setTitle("Clear this village?").setMessage("This removes only ${village.tag}. Other villages and their widgets stay saved. Widgets showing this village will ask you to choose a village.").setNegativeButton("Keep village", null).setPositiveButton("Clear") { _, _ ->
                try { Store.clear(this); Reminders.schedule(this); VillageWidget.refresh(this); message = "Village removed. Other accounts were kept."; render() } catch (_: IOException) { show("Could not remove the saved village.") }
            }.show()
        } }
        content.addView(panel)
    }
    private fun settingToggle(parent: LinearLayout, value: String, initial: Boolean, viewId: Int = View.NO_ID, changed: (Boolean) -> Unit) {
        parent.addView(Switch(this).apply { id = viewId; text = value; setTextColor(TEXT); textSize = 13f; minHeight = dp(48); isChecked = initial; setOnCheckedChangeListener { _, checked -> changed(checked) } })
    }

    private fun importClipboard() {
        if (importing) return
        val clip = try { getSystemService(ClipboardManager::class.java).primaryClip } catch (_: SecurityException) { show("Clipboard unavailable. Return to the app and try Sync again."); return }
        if (clip == null || clip.itemCount == 0) { show("Copy your village export from Clash first."); return }
        val value = clip.getItemAt(0).text
        if (value == null) { show("The clipboard does not contain a text export."); return }
        if (value.length > ExportParser.MAX_CHARS) { show("That export is too large (maximum 1 MB)."); return }
        importText(value.toString())
    }
    private fun updateClipboardListener() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        if (clipboardListening) { clipboard.removePrimaryClipChangedListener(clipboardChanged); clipboardListening = false }
        if (resumed && ClipboardSync.enabled(this)) { clipboard.addPrimaryClipChangedListener(clipboardChanged); clipboardListening = true }
    }
    private fun queueAutoClipboard() {
        handler.removeCallbacks(autoClipboard)
        if (resumed && hasWindowFocus() && ClipboardSync.enabled(this) && !pendingClipboard) handler.postDelayed(autoClipboard, 180)
    }
    private fun tryAutoClipboard() {
        if (!resumed || !hasWindowFocus() || !ClipboardSync.enabled(this) || demo != null || importing || autoChecking || pendingClipboard || isDestroyed) return
        val text = try { ClipboardSync.candidate(getSystemService(ClipboardManager::class.java).primaryClip) } catch (_: SecurityException) { null } ?: return
        autoChecking = true
        worker.execute {
            val fingerprint = ClipboardSync.fingerprint(text)
            var parsed: Village? = null; var previous: Village? = null
            if (fingerprint != Store.prefs(this).getString(ClipboardSync.LAST, "")) try { parsed = ExportParser.parse(text, Store.catalog(this), System.currentTimeMillis()); previous = Store.load(this, parsed.tag) } catch (_: ExportParser.InvalidExport) { }
            val incoming = parsed; val saved = previous
            handler.post {
                autoChecking = false
                if (isDestroyed || !resumed || !hasWindowFocus() || !ClipboardSync.enabled(this) || importing || demo != null) return@post
                Store.prefs(this).edit().putString(ClipboardSync.LAST, fingerprint).apply()
                if (incoming == null || !ClipboardSync.newer(incoming, saved)) return@post
                if (syncTarget.isNotEmpty() && !syncTarget.equals(incoming.tag, true)) return@post
                syncTarget = ""; importing = true; message = ""; render(); saveImport(text, incoming, true)
            }
        }
    }
    private fun importText(text: String) {
        if (importing) return
        importing = true; message = "Checking your village export…"; render()
        worker.execute {
            try {
                val parsed = ExportParser.parse(text, Store.catalog(this), System.currentTimeMillis()); val previous = Store.load(this, parsed.tag)
                handler.post {
                    if (isDestroyed) return@post
                    if (syncTarget.isNotEmpty() && !syncTarget.equals(parsed.tag, true)) { importing = false; show("This widget is for $syncTarget, but the clipboard contains ${parsed.tag}. Copy the correct village, or use Paste / add account to save another account."); return@post }
                    if (previous != null && previous.tag.equals(parsed.tag, true) && parsed.exportedAt < previous.exportedAt) { importing = false; show("That export is older than your saved village. Copy a fresh export from Clash."); return@post }
                    saveImport(text, parsed, false)
                }
            } catch (failure: ExportParser.InvalidExport) { handler.post { importing = false; show(failure.message) } }
        }
    }
    private fun saveImport(text: String, village: Village, automatic: Boolean) {
        worker.execute {
            try {
                val firstImport = Store.load(this, village.tag) == null
                Store.save(this, text, village); Store.prefs(this).edit().putString(ClipboardSync.LAST, ClipboardSync.fingerprint(text.trim())).apply()
                getSystemService(NotificationManager::class.java).cancel(VillageArchive.canonical(village.tag), 7)
                VillageWidget.refresh(this); Reminders.schedule(this)
                handler.post {
                    if (isDestroyed) return@post
                    importing = false; demo = null; message = ""
                    Toast.makeText(this, (if (automatic) "Auto-synced " else "Synced ") + Store.title(this, village), Toast.LENGTH_SHORT).show(); render()
                    val next = { if (!Store.prefs(this).getBoolean("asked-notify", false)) requestNotifications() }
                    if (firstImport && Store.inGameName(this, village).isEmpty()) editInGameName(village, next) else next()
                }
            } catch (_: IOException) { handler.post { importing = false; show("Could not save the export. Your previous village is unchanged.") } }
        }
    }
    private fun requestNotifications() {
        Store.prefs(this).edit().putBoolean("asked-notify", true).apply()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            if (!Store.prefs(this).getBoolean("requested-notify", false) || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) { Store.prefs(this).edit().putBoolean("requested-notify", true).apply(); requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9); return }
        }
        if (!Reminders.canNotify(this)) try { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) } catch (_: ActivityNotFoundException) { show("Notification settings are unavailable.") }
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) { super.onRequestPermissionsResult(code, permissions, results); if (code == 9) { Reminders.schedule(this); render() } }
    private fun rename(village: Village) {
        val input = EditText(this).apply { setSingleLine(true); setText(Store.nickname(this@MainActivity, village.tag)); setSelectAllOnFocus(true); filters = arrayOf(InputFilter.LengthFilter(32)) }
        val box = vertical().apply { setPadding(dp(20), dp(8), dp(20), 0); addView(input) }
        AlertDialog.Builder(this).setTitle("Account Nickname").setMessage("Optional label such as Main or Mini. Your in-game name stays separate.").setView(box).setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ -> Store.rename(this, village.tag, input.text.toString()); VillageWidget.refresh(this); render() }.show()
    }
    private fun showBoosts(village: Village) {
        val now = System.currentTimeMillis(); val detail = StringBuilder("From your latest village export. Use items in Clash, then sync here.\n\n"); var any = false
        for (upgrade in village.upgrades) if (!upgrade.done(now) && upgrade.hasBoosts()) {
            any = true; detail.append(upgrade.label()).append('\n').append(upgrade.boostText(now)).append('\n').append(upgrade.workText(now)).append('\n').append(upgrade.etaText(now)).append('\n')
            val plan = requireNotNull(upgrade.timing); val change = plan.nextChange(upgrade.elapsed(now))
            if (change != Long.MAX_VALUE) detail.append("Next speed change in: ").append(DurationText.remaining(upgrade.snapshotAt + change * 1000, now)).append('\n')
            if (plan.helper != null && plan.helper.nextStartSeconds >= 0) detail.append("Repeat-helper forecast: 1-hour sessions on the exported 23-hour work-day schedule.\n")
            detail.append('\n')
        }
        if (!any) detail.append("No timed upgrade boosts are recorded for your running upgrades.\n\n")
        detail.append(FastEvents.noteAt(village.exportedAt)).append("\n\nPotions and Magic Snacks in storage do not change timers until used. Books, Hammers and gem finishes are reflected after a fresh export. Resource and combat boosts, Alchemist and Prospector do not speed up these upgrade queues. Repeating helpers are forecast only when the export marks that assignment and includes its cooldown.")
        AlertDialog.Builder(this).setTitle("Boosts & Helpers").setMessage(detail.toString()).setPositiveButton("Close", null).setNeutralButton("Open Clash") { _, _ -> openGame() }.show()
    }
    private fun editInGameName(village: Village, after: () -> Unit) {
        val input = EditText(this).apply { setSingleLine(true); hint = "Name shown on your Clash profile"; setText(Store.inGameName(this@MainActivity, village)); setSelectAllOnFocus(true); filters = arrayOf(InputFilter.LengthFilter(64)) }
        val box = vertical().apply { setPadding(dp(20), dp(8), dp(20), 0); addView(input) }
        AlertDialog.Builder(this).setTitle("In-game Name · ${village.tag}").setMessage("Exports may omit the player name. Enter it once to identify this account in the app and widgets. Saved only on this phone; this does not rename your Clash account. Leave blank to use any exported name or saved nickname.").setView(box).setNegativeButton("Later") { _, _ -> after() }.setOnCancelListener { after() }.setPositiveButton("Save") { _, _ -> Store.setInGameName(this, village.tag, input.text.toString()); VillageWidget.refresh(this); render(); after() }.show()
    }
    private fun chooseVillage() {
        val villages = Store.all(this)
        AlertDialog.Builder(this).setTitle("Saved Accounts").setItems(villages.map { Store.accountLabel(this, it) }.toTypedArray()) { _, which -> Store.select(this, villages[which].tag); syncTarget = ""; message = ""; render() }.setNegativeButton("Close", null).show()
    }
    private fun pinWidget() {
        val manager = AppWidgetManager.getInstance(this); val villages = Store.all(this)
        if (villages.isEmpty()) { show("Import a village first, then add a widget for it."); return }
        AlertDialog.Builder(this).setTitle("Village for the new widget").setItems(villages.map { Store.accountLabel(this, it) }.toTypedArray()) { _, which ->
            if (!manager.isRequestPinAppWidgetSupported) { show("Long-press your home screen, choose Widgets, then find ${getString(R.string.app_name)}. Choose this widget’s village when prompted."); return@setItems }
            val tag = villages[which].tag
            val callback = Intent(this, VillageWidget::class.java).setAction(VillageWidget.PINNED).putExtra("tag", tag).setData(Uri.parse("villageclock://pin/${Uri.encode(tag)}"))
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            manager.requestPinAppWidget(ComponentName(this, VillageWidget::class.java), null, PendingIntent.getBroadcast(this, tag.hashCode(), callback, flags))
        }.setNegativeButton("Cancel", null).show()
    }
    private fun openGame() {
        val game = packageManager.getLaunchIntentForPackage(VillageWidget.COC)
        if (game == null) { show("Clash of Clans is not installed in this Android profile."); return }
        try { startActivity(game) } catch (_: ActivityNotFoundException) { show("Could not open Clash of Clans.") }
    }
    private fun showHelp() { AlertDialog.Builder(this).setTitle("Help & About").setMessage(HELP_TEXT.replace("%VERSION%", BuildConfig.VERSION_NAME)).setPositiveButton("Close", null).show() }
    private fun equalButtons(left: Button, right: Button) { left.layoutParams = LinearLayout.LayoutParams(0, dp(50), 1f).apply { rightMargin = dp(4) }; right.layoutParams = LinearLayout.LayoutParams(0, dp(50), 1f).apply { leftMargin = dp(4) }; left.textSize = 15f; right.textSize = 15f }
    private fun show(value: String?) { if (isDestroyed) return; message = value.orEmpty(); Toast.makeText(this, value, Toast.LENGTH_LONG).show(); render() }
    private fun sample(): Village {
        val now = System.currentTimeMillis()
        val jobs = listOf(Village.Upgrade(1000009, 19, "Archer Tower", Village.Queue.BUILDERS, now + 27 * 3600000L + 42 * 60000L, false, 0), Village.Upgrade(1000004, 16, "Gold Mine", Village.Queue.BUILDERS, now + 3 * 3600000L + 18 * 60000L, false, 1), Village.Upgrade(4000059, 6, "Electro Dragon", Village.Queue.LAB, now + 2 * 86400000L, false, 2), Village.Upgrade(73000000, 8, "L.A.S.S.I", Village.Queue.PETS, now + 7 * 3600000L, false, 3), Village.Upgrade(1000034, 10, "Builder Hall", Village.Queue.BASE, now + 19 * 3600000L, false, 4))
        return Village("#SAMPLE", "Anonymous", now - 12 * 3600000L, 15, 6, 2, jobs)
    }
    private fun dp(value: Int) = dp(value.toFloat())
    private fun dp(value: Float) = (resources.displayMetrics.density * value + .5f).toInt()
    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply { text = value; textSize = (if (size >= 26) size - 3 else if (size >= 18) size - 2 else size).toFloat(); setTextColor(color); typeface = if (bold) resources.getFont(R.font.lilita_one) else Typeface.create("sans-serif", Typeface.NORMAL); setLineSpacing(dp(2).toFloat(), 1f) }
    private fun vertical() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun horizontal() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun card() = vertical().apply { setPadding(dp(12), dp(10), dp(12), dp(10)); setBackgroundResource(R.drawable.card_bg) }
    private fun shape(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun gap(parent: LinearLayout, size: Int) { parent.addView(View(this), LinearLayout.LayoutParams(1, dp(if (size >= 8) size - 3 else maxOf(2, size - 1)))) }
    private fun button(parent: LinearLayout, value: String, primary: Boolean, click: View.OnClickListener): Button = Button(this).apply { text = value; isAllCaps = false; textSize = if (primary) 16f else 14f; typeface = resources.getFont(R.font.lilita_one); setTextColor(if (primary) 0xFF3B2508.toInt() else GOLD); setBackgroundResource(if (primary) R.drawable.btn_primary else R.drawable.btn_secondary); minHeight = dp(44); setPadding(dp(10), dp(3), dp(10), dp(3)); setOnClickListener(click); parent.addView(this, LinearLayout.LayoutParams(-1, dp(44))) }
    companion object {
        private val BG = 0xFF24180F.toInt(); private val CARD = 0xFF35251A.toInt(); private val TEXT = 0xFFFFF6E6.toInt(); private val MUTED = 0xFFCDBDA5.toInt(); private val GOLD = 0xFFFFC531.toInt(); private val GREEN = 0xFF8EE04A.toInt()
        private const val HELP_TEXT = "Sync Your Village\nIn Clash: Settings → More Settings → Export village data → Copy. Return here; Auto-sync on return imports fresh exports automatically while this app has focus. Turn it off in Settings for manual Sync / Add Account. Android does not let the tracker watch Clash’s Copy button in the background or redirect from it. Sync after upgrades, items or helper changes.\n\nTimers & Boosts\nWork left is upgrade work; a 24× Research Potion makes it count down faster. Finishes in is the estimated real time to completion, including boost expiry. Widgets show real time remaining in steady seconds. Potions stack by extending duration. Active Boosts groups their names, rates, time left and local end times above the upgrades. Finish clocks appear beside each upgrade. Settings → Time Format selects device default, 12-hour or 24-hour time. Next free estimates regular slots and excludes Goblin jobs. Boosts & Helpers keeps the per-job calculation details. Green bars show progress since export.\n\nWidgets & Alerts\nEach widget keeps its own account. Live readable widget timers use an ongoing notification, pause while locked or screen off, and recalculate on unlock. Pause live updates to use Android’s native timer. Battery restrictions can delay widgets or alerts. Reopen the app after force-stop.\n\nPrivacy & Updates\nVillage data stays on this phone. No Supercell login, ads or analytics. Clipboard access happens when you tap Sync or while this app is focused with Auto-sync on return enabled. Unrelated text is ignored; clipboard URIs are never opened. Check for Updates contacts GitHub only when tapped, with Android Downloads as another network path if app connections fail. APK downloads start after you choose Download Update. Install Update opens Android’s installer after package, version and signing-key checks. No village or account data is sent.\n\nOverview\nProgress compares exported levels and counts with Current Hall or Catalog Max targets. Crafting shows all exported modules. Helpers shows cooldown estimates and availability alerts; sync after use. Collection contains equipment and cosmetic records, using stable IDs when names are unavailable.\n\nAbout\nCoC Base Tracker v%VERSION% supports Android 8.0 and later. Independent fan companion; not affiliated with or endorsed by Supercell. Item art © Supercell, with additional offline artwork and identity data via ClashKing, with two decoration portraits via ClashPerk. Lilita One font: SIL OFL."
    }
}
