package com.villageclock.app

import android.app.DownloadManager
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import java.io.*
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/** Retains only this app's explicitly requested system-managed download. */
object UpdateDownloads {
    const val MAX_APK = 100L * 1024 * 1024
    @JvmStatic fun prefs(c: Context): SharedPreferences = c.getSharedPreferences("app-update", Context.MODE_PRIVATE)
    @JvmStatic @Throws(IOException::class) fun manager(c: Context): DownloadManager = c.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: throw IOException("Android Download Manager is unavailable. Enable Downloads in Android Settings.")
    @JvmStatic fun id(c: Context): Long = prefs(c).getLong("id", -1)
    @JvmStatic fun version(c: Context): String = prefs(c).getString("version", "").orEmpty()
    fun interface ReleaseCheck { @Throws(Exception::class) fun fetch(): UpdateChecker.Release }
    fun interface MetadataSource { @Throws(IOException::class) fun fetch(publicPage: Boolean): UpdateChecker.Release }
    class TransferFailure(@JvmField val code: Int) : IOException(downloadFailure(code))
    @JvmStatic @Throws(Exception::class) fun fetchLatest(c: Context): UpdateChecker.Release = fetchLatest(ReleaseCheck { UpdateChecker.fetchLatest(BuildConfig.VERSION_NAME) }, ReleaseCheck { fetchMetadata(c) })
    @JvmStatic @Throws(Exception::class) fun fetchLatest(app: ReleaseCheck, system: ReleaseCheck): UpdateChecker.Release {
        try { return app.fetch() }
        catch (failure: IOException) {
            try { return system.fetch() }
            catch (fallback: Exception) { throw UpdateChecker.CheckException(UpdateChecker.failureDetail(failure) + "\n\nAndroid Downloads: " + (fallback.message ?: fallback.javaClass.simpleName)) }
        }
    }
    @JvmStatic @Throws(IOException::class) fun directory(c: Context): File = c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: throw IOException("Download storage is unavailable. Free some storage and retry.")
    class Status(@JvmField val state: Int, @JvmField val reason: Int, @JvmField val downloaded: Long, @JvmField val total: Long)
    @JvmStatic @Throws(IOException::class) fun status(c: Context, id: Long): Status {
        try {
            manager(c).query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                if (cursor == null || !cursor.moveToFirst()) throw IOException("This download is no longer available. Start it again.")
                return Status(cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)), cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)), cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)), cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)))
            }
        } catch (failure: RuntimeException) { throw IOException("Android Downloads could not be read: ${failure.javaClass.simpleName}", failure) }
    }
    @JvmStatic fun request(url: String, title: String): DownloadManager.Request = DownloadManager.Request(Uri.parse(url)).setTitle(title).setAllowedOverMetered(true).setAllowedOverRoaming(false).addRequestHeader("User-Agent", "CoC-Base-Tracker/${BuildConfig.VERSION_NAME}")
    @JvmStatic @Throws(IOException::class) fun fetchMetadata(c: Context): UpdateChecker.Release = fetchMetadata(MetadataSource { fetchMetadataFile(c, it) })
    @JvmStatic @Throws(IOException::class) fun fetchMetadata(source: MetadataSource): UpdateChecker.Release {
        var pageFailure: IOException
        try { return source.fetch(true) } catch (failure: IOException) { pageFailure = failure }
        if (pageFailure is TransferFailure && pageFailure.code in setOf(500, 502, 503, 504)) {
            try { Thread.sleep(1000) } catch (_: InterruptedException) { Thread.currentThread().interrupt(); throw IOException("The update check was cancelled.") }
            try { return source.fetch(true) } catch (failure: IOException) { pageFailure = failure }
        }
        try { return source.fetch(false) }
        catch (failure: IOException) { throw IOException("Public Releases: ${pageFailure.message}\nAPI fallback: ${failure.message}", failure) }
    }
    private fun fetchMetadataFile(context: Context, publicPage: Boolean): UpdateChecker.Release {
        val c = context.applicationContext
        val dm = manager(c)
        directory(c)
        val name = "release-check-${UUID.randomUUID()}" + if (publicPage) ".html" else ".json"
        val limit = if (publicPage) 2 * 1024 * 1024 else 512 * 1024
        val request = request(if (publicPage) UpdateChecker.RELEASES_URL else UpdateChecker.API_URL, "Checking CoC Base Tracker updates")
            .addRequestHeader("Cache-Control", "no-cache").addRequestHeader("Pragma", "no-cache")
            .addRequestHeader("Accept", if (publicPage) "text/html" else "application/vnd.github+json").setMimeType(if (publicPage) "text/html" else "application/json")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE).setDestinationInExternalFilesDir(c, Environment.DIRECTORY_DOWNLOADS, name)
        var download = -1L
        try {
            download = dm.enqueue(request)
            val deadline = SystemClock.elapsedRealtime() + 20000
            while (SystemClock.elapsedRealtime() < deadline) {
                val status = status(c, download)
                if (status.total > limit || status.downloaded > limit) throw IOException("The release response is too large.")
                if (status.state == DownloadManager.STATUS_FAILED) throw TransferFailure(status.reason)
                if (status.state == DownloadManager.STATUS_SUCCESSFUL) c.contentResolver.openInputStream(dm.getUriForDownloadedFile(download)).use { input ->
                    val body = String(readBounded(input, limit), Charsets.UTF_8)
                    return if (publicPage) UpdateChecker.parseReleasePage(body) else UpdateChecker.parse(body)
                }
                try { Thread.sleep(300) } catch (_: InterruptedException) { Thread.currentThread().interrupt(); throw IOException("The update check was cancelled.") }
            }
            throw IOException("Android Downloads did not finish this check within 20 seconds. Check that Downloads is enabled and can access the network.")
        } catch (failure: RuntimeException) { throw IOException("Android Downloads: ${failure.javaClass.simpleName}. Check that Downloads is enabled in Android Settings.", failure) }
        finally { if (download != -1L) dm.remove(download); File(directory(c), name).delete() }
    }
    @JvmStatic @Throws(IOException::class) fun readBounded(input: InputStream?, max: Int): ByteArray {
        if (input == null) throw IOException("The downloaded file could not be opened.")
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) { val count = input.read(buffer); if (count == -1) break; if (bytes.size() + count > max) throw IOException("The downloaded response is too large."); bytes.write(buffer, 0, count) }
        return bytes.toByteArray()
    }
    @JvmStatic @Synchronized @Throws(IOException::class) fun start(c: Context, release: UpdateChecker.Release): Long {
        if (release.apkStatusKnown && !release.hasApk) throw IOException("The published release has no downloadable APK yet.")
        if (UpdateChecker.compare(release.version, BuildConfig.VERSION_NAME) <= 0) throw IOException("This release is not newer than the installed app.")
        if (release.bytes > MAX_APK) throw IOException("This APK exceeds the 100 MB download limit.")
        val url = release.downloadUrl()
        if (url.isEmpty()) throw IOException("The APK download address could not be verified.")
        if (id(c) != -1L && url == prefs(c).getString("url", "")) return id(c)
        cancel(c)
        val file = File(directory(c), "tracker-update.apk")
        if (file.exists() && !file.delete()) throw IOException("The previous update file could not be cleared.")
        val request = request(url, "CoC Base Tracker v${release.version}").setDescription("Downloading update · return to the tracker to install").setMimeType("application/vnd.android.package-archive").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE).setDestinationInExternalFilesDir(c, Environment.DIRECTORY_DOWNLOADS, "tracker-update.apk")
        val download = try { manager(c).enqueue(request) } catch (failure: RuntimeException) { throw IOException("The download could not be started. Enable Downloads in Android Settings.", failure) }
        prefs(c).edit().putLong("id", download).putString("version", release.version).putString("url", url).putString("sha256", release.sha256).putLong("size", release.bytes).apply()
        return download
    }
    @JvmStatic @Synchronized @Throws(IOException::class) fun cancel(c: Context) {
        val download = id(c)
        if (download != -1L) manager(c).remove(download)
        prefs(c).edit().clear().apply()
        c.getSystemService(NotificationManager::class.java)?.cancel(90)
    }
    @JvmStatic fun downloadFailure(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough storage. Free some space and retry."
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Download storage is unavailable."
        404 -> "The APK is not attached to this release yet (HTTP 404). Try again later."
        403, 429 -> "GitHub denied or limited the download (HTTP $reason). Wait before retrying, or open Releases."
        500, 502, 503, 504 -> "GitHub’s download server is temporarily unavailable (HTTP $reason). A new release may still be processing. Retry shortly."
        else -> "Android Downloads failed (code $reason). Retry or open Releases."
    }
    /** Read-only APK inspection. Android checks the signature again during installation. */
    @JvmStatic @Synchronized @Throws(Exception::class) fun verify(c: Context): Uri {
        val download = id(c)
        if (download == -1L || status(c, download).state != DownloadManager.STATUS_SUCCESSFUL) throw IOException("The update download has not completed.")
        val file = File(directory(c), "tracker-update.apk")
        val size = file.length()
        val expected = prefs(c).getLong("size", 0)
        if (size <= 0 || size > MAX_APK || expected > 0 && expected != size) throw IOException("The APK size does not match. Cancel this download and retry.")
        FileInputStream(file).use { checkDigest(it, prefs(c).getString("sha256", "").orEmpty()) }
        val pm = c.packageManager
        val flags = packageInfoFlags()
        var archive = pm.getPackageArchiveInfo(file.absolutePath, flags)
        var installed = pm.getPackageInfo(c.packageName, flags)
        // Some Android package parsers leave SigningInfo empty for an archive.
        // Ask for both records and retry the legacy query only when no signer was read.
        if (archive != null && signatures(archive).isEmpty()) {
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES)?.takeIf { signatures(it).isNotEmpty() }?.let { archive = it }
        }
        if (signatures(installed).isEmpty()) installed = pm.getPackageInfo(c.packageName, PackageManager.GET_SIGNATURES)
        checkPackage(archive, installed, version(c))
        val uri = manager(c).getUriForDownloadedFile(download)
        if (uri == null || uri.scheme != "content") throw IOException("Android could not share the verified APK with its installer.")
        return uri
    }
    @JvmStatic @Throws(Exception::class) fun checkDigest(input: InputStream, expected: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32768)
        var size = 0L
        while (true) { val count = input.read(buffer); if (count == -1) break; size += count; if (size > MAX_APK) throw IOException("The APK is too large."); digest.update(buffer, 0, count) }
        val hash = digest.digest().joinToString("") { String.format(Locale.ROOT, "%02x", it.toInt() and 255) }
        if (expected.isNotEmpty() && expected != hash) throw IOException("The APK checksum does not match GitHub. Cancel this download and retry.")
    }
    @JvmStatic @Throws(IOException::class) fun checkPackage(archive: PackageInfo?, installed: PackageInfo?, version: String) {
        if (archive == null || installed == null || installed.packageName != archive.packageName) throw IOException("The downloaded file is not a CoC Base Tracker APK.")
        val incoming = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        val current = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        if (incoming <= current || UpdateChecker.compare(archive.versionName, version) != 0) throw IOException("The APK version does not match the newer release.")
        val actual = signatures(archive)
        val expected = signatures(installed)
        if (expected.isEmpty() || actual.isEmpty()) throw SigningFailure("Android could not read " + (if (expected.isEmpty()) "the installed app’s" else "the downloaded APK’s") + " signing certificate. Installation is blocked until it can be verified. This is not a confirmed key mismatch.", expected, actual)
        if (actual != expected) throw SigningFailure("The APK uses a different signing key. Installation is blocked. Keep your existing app and use a release signed with the same key.", expected, actual)
    }
    class SigningFailure(message: String, @JvmField val installed: Set<String>, @JvmField val downloaded: Set<String>) : IOException(message) {
        fun diagnostics(): String = "Android API: ${Build.VERSION.SDK_INT}\nInstalled certificate SHA-256:\n${installed.sorted().joinToString("\n").ifEmpty { "Unavailable" }}\n\nDownloaded certificate SHA-256:\n${downloaded.sorted().joinToString("\n").ifEmpty { "Unavailable" }}\n\nMatching certificates are required; missing or different certificates never bypass verification."
    }
    @JvmStatic fun packageInfoFlags(): Int = PackageManager.GET_SIGNATURES or if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else 0
    private fun signatures(info: PackageInfo): Set<String> {
        val modern = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners.orEmpty() else emptyArray()
        val records = modern.takeIf { it.isNotEmpty() } ?: info.signatures.orEmpty()
        return records.map { certificate -> MessageDigest.getInstance("SHA-256").digest(certificate.toByteArray()).joinToString("") { String.format(Locale.ROOT, "%02x", it.toInt() and 255) } }.toSet()
    }
}
