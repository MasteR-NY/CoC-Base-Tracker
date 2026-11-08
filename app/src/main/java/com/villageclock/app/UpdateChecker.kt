package com.villageclock.app

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

/** Explicit anonymous release checks; saved account data never enters a request. */
object UpdateChecker {
    const val API_URL = "https://api.github.com/repos/MasteR-NY/CoC-Base-Tracker/releases/latest"
    const val RELEASES_URL = "https://github.com/MasteR-NY/CoC-Base-Tracker/releases/latest"
    private const val MAX_BYTES = 512 * 1024
    class CheckException(message: String) : IOException(message)
    class Release internal constructor(@JvmField val version: String, @JvmField val hasApk: Boolean,
        @JvmField val apkStatusKnown: Boolean = true, @JvmField val tag: String = "v$version",
        @JvmField val downloadUrl: String = "", @JvmField val fileName: String = "",
        @JvmField val sha256: String = "", @JvmField val bytes: Long = 0) {
        fun downloadUrl(): String = downloadUrl.ifEmpty { UpdateChecker.downloadUrl(tag, "CoC-Base-Tracker-$version.apk") }
    }
    fun interface ConnectionFactory { @Throws(IOException::class) fun open(url: URL): HttpsURLConnection }
    @JvmStatic @JvmOverloads @Throws(IOException::class) fun fetchLatest(installedVersion: String, factory: ConnectionFactory = ConnectionFactory { it.openConnection() as HttpsURLConnection }): Release {
        try { return fetchApi(installedVersion, factory) }
        catch (api: IOException) {
            try { return fetchReleaseRedirect(installedVersion, factory) }
            catch (page: IOException) { throw CheckException("GitHub API: ${failureDetail(api)}\n\nGitHub Releases: ${failureDetail(page)}\n\nOpen Releases to check in your browser.") }
        }
    }
    private fun configure(connection: HttpsURLConnection, installedVersion: String) {
        connection.connectTimeout = 12000; connection.readTimeout = 12000
        connection.instanceFollowRedirects = false; connection.useCaches = false; connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "CoC-Base-Tracker/$installedVersion")
        connection.setRequestProperty("Connection", "close")
    }
    private fun fetchApi(installed: String, factory: ConnectionFactory): Release {
        val connection = factory.open(URL(API_URL))
        try {
            configure(connection, installed)
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            when (val status = connection.responseCode) {
                200 -> Unit
                404 -> throw CheckException("No public release was found. You can check the Releases page manually.")
                403, 429 -> throw CheckException("GitHub denied or limited the request (HTTP $status). Try again later.")
                else -> throw CheckException("The server returned HTTP $status.")
            }
            val bytes = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) { val count = input.read(buffer); if (count == -1) break; if (bytes.size() + count > MAX_BYTES) throw CheckException("The release response is too large. Open Releases to check manually."); bytes.write(buffer, 0, count) }
            }
            return parse(bytes.toString("UTF-8"))
        } finally { connection.disconnect() }
    }
    private fun fetchReleaseRedirect(installed: String, factory: ConnectionFactory): Release {
        val connection = factory.open(URL(RELEASES_URL))
        try {
            configure(connection, installed); connection.setRequestProperty("Accept", "text/html")
            val status = connection.responseCode
            if (status !in setOf(301, 302, 303, 307, 308)) throw CheckException("No latest-release redirect was returned (HTTP $status).")
            return parseReleaseRedirect(connection.getHeaderField("Location"))
        } finally { connection.disconnect() }
    }
    @JvmStatic @Throws(CheckException::class) fun parseReleaseRedirect(location: String?): Release {
        try {
            require(location != null && location.length <= 1024)
            val uri = URI(RELEASES_URL).resolve(location)
            val prefix = "/MasteR-NY/CoC-Base-Tracker/releases/tag/"
            require(uri.scheme.equals("https", true) && uri.host.equals("github.com", true) && uri.userInfo == null && uri.port in setOf(-1, 443) && uri.rawQuery == null && uri.rawFragment == null && uri.rawPath.startsWith(prefix))
            val tag = URLDecoder.decode(uri.rawPath.substring(prefix.length), "UTF-8")
            parts(tag)
            return Release(tag.replaceFirst(Regex("^[vV]"), ""), false, false, tag)
        } catch (_: Exception) { throw CheckException("The release redirect could not be verified for this repository.") }
    }
    /** Only used for a fresh HTTPS fetch of the fixed /releases/latest endpoint. */
    @JvmStatic @Throws(CheckException::class) fun parseReleasePage(html: String?): Release {
        if (html == null || html.length > 2 * 1024 * 1024) throw CheckException("The public release page is too large or unreadable.")
        val tags = Regex("""<(?:meta|link)\b[^>]{0,4096}>""", RegexOption.IGNORE_CASE)
        val fields = Regex("""([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(["'])(.*?)\2""", RegexOption.DOT_MATCHES_ALL)
        var verified: Release? = null
        for (match in tags.findAll(html)) {
            val attributes = fields.findAll(match.value).associate { it.groupValues[1].lowercase(Locale.ROOT) to it.groupValues[3] }
            val location = when { attributes["property"].equals("og:url", true) -> attributes["content"]; attributes["rel"].equals("canonical", true) -> attributes["href"]; else -> null }
            if (location != null) {
                val candidate = parseReleaseRedirect(location)
                if (verified != null && verified.tag != candidate.tag) throw CheckException("The public release page contains conflicting version metadata.")
                verified = candidate
            }
        }
        return verified ?: throw CheckException("The public release page did not confirm a stable version. The release may still be publishing.")
    }
    @JvmStatic fun userMessage(detail: String?): String {
        val value = detail.orEmpty()
        return when {
            value.contains("EPERM") -> "Android blocked this app’s direct network access (EPERM). The system download fallback also failed" + (if (value.contains("403")) " with HTTP 403" else if (value.contains("504")) " with HTTP 504" else "") + ". Check the app’s network access or retry later. Open Releases remains available."
            value.contains("403") || value.contains("429") -> "GitHub denied or limited the request. Wait before retrying, or open Releases. This does not mean your phone is offline."
            listOf("500", "504", "502", "503").any(value::contains) -> "GitHub did not finish the request. A newly published release or its APK may still be processing. Retry shortly or open Releases."
            else -> "Couldn’t verify the latest release right now. Retry or open Releases. Technical details are available below."
        }
    }
    @JvmStatic fun failureDetail(failure: Exception): String = when (failure) {
        is CheckException -> failure.message.orEmpty()
        is javax.net.ssl.SSLException -> "Secure connection failed (${failure.javaClass.simpleName}). Check the phone’s date/time, Android security updates, or network filtering."
        is java.net.UnknownHostException -> "GitHub’s address could not be resolved. Check Private DNS, VPN or network filtering."
        is java.net.SocketTimeoutException -> "The connection timed out. Retry or try another network."
        is java.net.ConnectException -> "GitHub could not be reached. Check VPN, firewall or network filtering."
        is java.net.SocketException -> "SocketException: ${diagnostic(failure)}. Check this app’s Wi-Fi/mobile-data access in Android Settings and any VPN/firewall rules. Android’s download service is also tried. Include this system detail when reporting a failure."
        else -> "The check failed (${failure.javaClass.simpleName}). Try another network or open Releases."
    }
    private fun diagnostic(error: Throwable): String {
        val messages = mutableListOf<String>()
        var failure: Throwable? = error
        repeat(3) { failure?.message?.takeIf { it.trim().isNotEmpty() }?.let { messages.add(it.replace(Regex("[\\p{Cntrl}]"), " ")) }; failure = failure?.cause }
        return messages.joinToString("; ").ifEmpty { "no further system detail" }.take(300)
    }
    @JvmStatic fun downloadUrl(tag: String?, name: String?): String {
        if (tag == null || !tag.matches(Regex("[vV]?[0-9]+(?:\\.[0-9]+){0,3}(?:\\+[0-9A-Za-z.-]+)?")) || name == null || !name.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,120}\\.apk"))) return ""
        return "https://github.com/MasteR-NY/CoC-Base-Tracker/releases/download/$tag/$name"
    }
    @JvmStatic @Throws(CheckException::class) fun parse(json: String?): Release {
        if (json == null || json.length > MAX_BYTES) throw CheckException("The release response could not be read. Open Releases to check manually.")
        try {
            val root = JSONObject(json)
            if (root.opt("draft") != false || root.opt("prerelease") != false) throw CheckException("No stable published release was returned. Open Releases to check manually.")
            val value = root.opt("tag_name") as? String ?: throw CheckException("The release has no readable version. Open Releases to check manually.")
            val tag = value.trim()
            parts(tag)
            val assets = root.optJSONArray("assets")
            if (assets != null) for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                if (asset.optString("state") != "uploaded" || !asset.optString("name").endsWith(".apk")) continue
                val name = asset.optString("name")
                val expected = downloadUrl(tag, name)
                if (expected.isNotEmpty() && expected == asset.optString("browser_download_url")) {
                    val digest = asset.optString("digest")
                    val hash = if (digest.matches(Regex("sha256:[a-fA-F0-9]{64}"))) digest.substring(7).lowercase(Locale.ROOT) else ""
                    return Release(tag.replaceFirst(Regex("^[vV]"), ""), true, true, tag, expected, name, hash, asset.optLong("size", 0))
                }
            }
            return Release(tag.replaceFirst(Regex("^[vV]"), ""), false, true, tag)
        } catch (error: CheckException) { throw error }
        catch (_: Exception) { throw CheckException("The release response could not be read. Open Releases to check manually.") }
    }
    @JvmStatic @Throws(CheckException::class) fun compare(a: String?, b: String?): Int {
        val left = parts(a); val right = parts(b)
        for (i in 0 until maxOf(left.size, right.size)) { val result = (left.getOrNull(i) ?: BigInteger.ZERO).compareTo(right.getOrNull(i) ?: BigInteger.ZERO); if (result != 0) return result }
        return 0
    }
    private fun parts(value: String?): List<BigInteger> {
        if (value == null || value.length > 80 || !value.trim().matches(Regex("[vV]?[0-9]+(?:\\.[0-9]+){0,3}(?:\\+[0-9A-Za-z.-]+)?"))) throw CheckException("This release uses an unrecognized version format. Open Releases to check manually.")
        return value.trim().replaceFirst(Regex("^[vV]"), "").substringBefore('+').split('.').map(::BigInteger)
    }
}
