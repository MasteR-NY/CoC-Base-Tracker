package com.villageclock.app

import android.app.*
import android.content.*
import android.graphics.Typeface
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors

/** Explicit download, verification and foreground installation; never installs silently. */
class UpdateActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var detail: TextView
    private lateinit var progress: ProgressBar
    private lateinit var install: Button
    private lateinit var retry: Button
    private lateinit var cancel: Button
    private lateinit var certificateDetails: Button
    private var resumed = false; private var busy = false; private var ready = false
    private var verifiedId = -1L
    private var release: UpdateChecker.Release? = null
    private val poll = Runnable { refresh() }
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved); createViews()
        try {
            intent.getStringExtra("release")?.let { json ->
                val parsed = UpdateChecker.parse(json)
                release = if (intent.getBooleanExtra("redirect", false)) UpdateChecker.parseReleaseRedirect("/MasteR-NY/CoC-Base-Tracker/releases/tag/${parsed.tag}") else parsed
            }
            if (intent.getBooleanExtra("preview", false)) { showReady("1.22"); return }
            release?.let { if (UpdateDownloads.id(this) == -1L || it.version != UpdateDownloads.version(this)) startDownload() }
        } catch (error: Exception) { showError(error) }
    }
    private fun createViews() {
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(0xFF24180F.toInt()) }
        val content = column().apply { setPadding(dp(20), dp(24), dp(20), dp(24)) }
        scroll.addView(content); setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= 30) { val bars = insets.getInsets(WindowInsets.Type.systemBars()); content.setPadding(dp(20) + bars.left, dp(24) + bars.top, dp(20) + bars.right, dp(24) + bars.bottom) }
            else content.setPadding(dp(20), dp(24) + insets.systemWindowInsetTop, dp(20), dp(24) + insets.systemWindowInsetBottom)
            insets
        }
        button(content, "‹ Back", false) { finish() }; space(content, 20)
        content.addView(text("App Update", 30, 0xFFFFC531.toInt(), true)); content.addView(text("CoC Base Tracker v${BuildConfig.VERSION_NAME}", 15, 0xFFCDBDA5.toInt(), false)); space(content, 22)
        val card = column().apply { setBackgroundResource(R.drawable.card_bg); setPadding(dp(18), dp(18), dp(18), dp(18)) }; content.addView(card)
        status = text("Preparing Update", 23, 0xFFFFF6E6.toInt(), true).apply { id = R.id.download_status }; card.addView(status); space(card, 12)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { isIndeterminate = true }; card.addView(progress, LinearLayout.LayoutParams(-1, dp(12))); space(card, 12)
        detail = text("The download is managed by Android. You can keep using the tracker while it finishes.", 15, 0xFFCDBDA5.toInt(), false).apply { id = R.id.download_detail }; card.addView(detail); space(card, 20)
        install = button(card, "Install Update", true) { install() }.apply { id = R.id.install_update; visibility = View.GONE }
        retry = button(card, "Retry Download", true) { startDownload() }.apply { visibility = View.GONE }; space(card, 8)
        cancel = button(card, "Cancel Download", false) { try { UpdateDownloads.cancel(this); finish() } catch (error: Exception) { showError(error) } }
        certificateDetails = button(card, "Certificate Details", false) { }.apply { id = R.id.certificate_details; visibility = View.GONE }
        space(content, 20); content.addView(text("Android asks you to confirm installation. Your saved accounts and widgets stay in place when you update.", 14, 0xFFCDBDA5.toInt(), false))
        space(content, 16); button(content, "Open Releases", false) { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.RELEASES_URL))) } catch (_: ActivityNotFoundException) { Toast.makeText(this, "No browser is available.", Toast.LENGTH_LONG).show() } }
    }
    override fun onResume() { super.onResume(); resumed = true; if (!intent.getBooleanExtra("preview", false)) refresh() }
    override fun onPause() { resumed = false; handler.removeCallbacks(poll); super.onPause() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); worker.shutdownNow(); super.onDestroy() }
    private fun startDownload() {
        if (busy) return
        ready = false; verifiedId = -1; certificateDetails.visibility = View.GONE
        try {
            val release = release ?: throw IOException("Return to Check for Updates to find the latest release.")
            UpdateDownloads.cancel(this); UpdateDownloads.start(this, release); retry.visibility = View.GONE; cancel.text = "Cancel Download"; refresh()
        } catch (error: Exception) { showError(error) }
    }
    private fun refresh() {
        handler.removeCallbacks(poll)
        if (!resumed || busy || isFinishing) return
        val id = UpdateDownloads.id(this)
        if (id == -1L) { showError(IOException("No update download is saved. Return to Check for Updates.")); return }
        if (ready && verifiedId == id) return
        busy = true
        worker.execute {
            val result = runCatching {
                val value = UpdateDownloads.status(this, id)
                if (value.total > UpdateDownloads.MAX_APK || value.downloaded > UpdateDownloads.MAX_APK) { UpdateDownloads.cancel(this); throw IOException("The APK exceeds the 100 MB download limit.") }
                if (value.state == DownloadManager.STATUS_FAILED) throw IOException(UpdateDownloads.downloadFailure(value.reason))
                if (value.state == DownloadManager.STATUS_SUCCESSFUL) UpdateDownloads.verify(this)
                value
            }
            handler.post {
                busy = false
                if (isDestroyed || isFinishing) return@post
                val value = result.getOrElse { showError(it as? Exception ?: IOException(it)); return@post }
                if (value.state == DownloadManager.STATUS_SUCCESSFUL) { verifiedId = id; showReady(UpdateDownloads.version(this)); return@post }
                status.text = if (value.state == DownloadManager.STATUS_PAUSED) "Download Waiting" else "Downloading v${UpdateDownloads.version(this)}"
                progress.visibility = View.VISIBLE; progress.isIndeterminate = value.total <= 0
                if (value.total > 0) progress.progress = (value.downloaded * 100 / value.total).toInt()
                detail.text = size(value.downloaded) + (if (value.total > 0) " / " + size(value.total) else "") + "\nYou can leave this screen. Reopen Update Download from the home screen when it finishes."
                if (resumed) handler.postDelayed(poll, 750)
            }
        }
    }
    fun showReady(version: String) {
        ready = true; status.text = "v$version Ready to Install"; progress.visibility = View.GONE
        detail.text = "Download complete. Package, version and signing key checks passed.\n\nTap Install Update to continue with Android’s installer."
        install.visibility = View.VISIBLE; retry.visibility = View.GONE; cancel.text = "Delete Download"; certificateDetails.visibility = View.GONE
    }
    private fun showError(error: Exception) {
        ready = false; status.text = "Update Unavailable"; progress.visibility = View.GONE; detail.text = error.message ?: error.javaClass.simpleName; install.visibility = View.GONE; retry.visibility = if (release == null) View.GONE else View.VISIBLE
        certificateDetails.visibility = if (error is UpdateDownloads.SigningFailure) View.VISIBLE else View.GONE
        if (error is UpdateDownloads.SigningFailure) certificateDetails.setOnClickListener {
            val diagnostic = error.diagnostics()
            val text = TextView(this).apply { this.text = diagnostic; textSize = 12f; setPadding(dp(20), dp(10), dp(20), dp(10)); setTextIsSelectable(true) }
            val scroll = ScrollView(this).apply { addView(text) }
            AlertDialog.Builder(this).setTitle("Signing Certificates").setView(scroll).setPositiveButton("Close", null).setNeutralButton("Copy Details") { _, _ -> getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("APK signing diagnostics", diagnostic)); Toast.makeText(this, "Certificate details copied", Toast.LENGTH_SHORT).show() }.show()
        }
    }
    private fun install() {
        if (!ready || busy || intent.getBooleanExtra("preview", false)) return
        if (!packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(this).setTitle("Allow This Update").setMessage("In Android Settings, allow CoC Base Tracker to install apps. Return here and tap Install Update again.").setPositiveButton("Open Settings") { _, _ ->
                try { startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) }
                catch (_: ActivityNotFoundException) { showError(IOException("Open Android Settings → Apps → Special access → Install unknown apps and allow CoC Base Tracker.")) }
            }.setNegativeButton("Later", null).show()
            return
        }
        busy = true; install.isEnabled = false
        worker.execute {
            val result = runCatching { UpdateDownloads.verify(this) }
            handler.post {
                busy = false
                if (isDestroyed || isFinishing) return@post
                install.isEnabled = true
                val verified = result.getOrElse { showError(it as? Exception ?: IOException(it)); return@post }
                if (!resumed) return@post
                try { startActivity(installIntent(verified)) } catch (_: ActivityNotFoundException) { showError(IOException("Android’s package installer is unavailable.")) }
            }
        }
    }
    private fun size(bytes: Long): String = String.format(Locale.getDefault(), "%.1f MB", bytes.coerceAtLeast(0) / 1048576.0)
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(color); typeface = if (bold) resources.getFont(R.font.lilita_one) else Typeface.create("sans-serif", Typeface.NORMAL); setLineSpacing(dp(2).toFloat(), 1f) }
    private fun button(parent: LinearLayout, value: String, primary: Boolean, click: View.OnClickListener) = Button(this).apply { text = value; isAllCaps = false; textSize = 16f; typeface = resources.getFont(R.font.lilita_one); setTextColor(if (primary) 0xFF3B2508.toInt() else 0xFFFFC531.toInt()); setBackgroundResource(if (primary) R.drawable.btn_primary else R.drawable.btn_secondary); minHeight = dp(48); setPadding(dp(10), dp(5), dp(10), dp(5)); setOnClickListener(click); parent.addView(this, LinearLayout.LayoutParams(-1, -2)) }
    private fun space(parent: LinearLayout, height: Int) { parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height))) }
    private fun dp(value: Int) = (resources.displayMetrics.density * value + .5f).toInt()
    companion object {
        @JvmStatic fun intent(c: Context, release: UpdateChecker.Release?): Intent = Intent(c, UpdateActivity::class.java).also { intent ->
            if (release != null) {
                val json = JSONObject().put("tag_name", release.tag).put("draft", false).put("prerelease", false)
                val assets = JSONArray()
                if (release.hasApk) assets.put(JSONObject().put("name", release.fileName).put("state", "uploaded").put("browser_download_url", release.downloadUrl).put("size", release.bytes).put("digest", if (release.sha256.isEmpty()) "" else "sha256:${release.sha256}"))
                json.put("assets", assets); intent.putExtra("release", json.toString()).putExtra("redirect", !release.apkStatusKnown)
            }
        }
        @JvmStatic fun installIntent(uri: Uri): Intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri("CoC Base Tracker update", uri) }
    }
}
