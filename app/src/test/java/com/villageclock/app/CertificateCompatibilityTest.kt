package com.villageclock.app

import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.os.Build
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 29, 35])
class CertificateCompatibilityTest {
    private val first = Signature("certificate-a".toByteArray())
    private val second = Signature("certificate-b".toByteArray())
    private fun info(code: Int, legacy: Array<Signature>?): PackageInfo = PackageInfo().apply { packageName = "com.villageclock.app"; versionCode = code; versionName = if (code == 99) "99.0" else "1.18"; signatures = legacy }
    private fun modern(info: PackageInfo, certificates: Array<Signature>) {
        if (Build.VERSION.SDK_INT < 28) return
        val signing = ReflectionHelpers.newInstance(SigningInfo::class.java)
        shadowOf(signing).setSignatures(certificates)
        info.signingInfo = signing
    }
    @Test fun emptyModernArchiveRecordUsesRealLegacyCertificate() {
        val installed = info(19, arrayOf(first)); modern(installed, arrayOf(first))
        val archive = info(99, arrayOf(first)); modern(archive, emptyArray())
        UpdateDownloads.checkPackage(archive, installed, "99.0")
        assertTrue(UpdateDownloads.packageInfoFlags() and PackageManager.GET_SIGNATURES != 0)
        if (Build.VERSION.SDK_INT >= 28) assertTrue(UpdateDownloads.packageInfoFlags() and PackageManager.GET_SIGNING_CERTIFICATES != 0)
    }
    @Test fun nonemptyModernMismatchNeverFallsBackToConflictingLegacyData() {
        val installed = info(19, arrayOf(first)); modern(installed, arrayOf(first))
        val archive = info(99, arrayOf(first)); modern(archive, arrayOf(second))
        if (Build.VERSION.SDK_INT < 28) archive.signatures = arrayOf(second)
        try { UpdateDownloads.checkPackage(archive, installed, "99.0"); fail("Accepted a different current signer") }
        catch (failure: UpdateDownloads.SigningFailure) { assertTrue(failure.message!!.contains("different signing key")); assertEquals(setOf(hash(first)), failure.installed); assertEquals(setOf(hash(second)), failure.downloaded) }
    }
    @Test fun unavailableMetadataIsBlockedAndNotMisreportedAsConfirmedMismatch() {
        val installed = info(19, arrayOf(first)); val archive = info(99, null); modern(archive, emptyArray())
        try { UpdateDownloads.checkPackage(archive, installed, "99.0"); fail("Accepted an unreadable signer") }
        catch (failure: UpdateDownloads.SigningFailure) { assertTrue(failure.message!!.contains("not a confirmed key mismatch")); assertTrue(failure.downloaded.isEmpty()); assertTrue(failure.diagnostics().contains("Unavailable")) }
    }
    @Test fun everySignerIsRequiredAndSignerOrderDoesNotMatter() {
        val installed = info(19, arrayOf(first, second)); modern(installed, arrayOf(first, second))
        val archive = info(99, arrayOf(second, first)); modern(archive, arrayOf(second, first))
        UpdateDownloads.checkPackage(archive, installed, "99.0")
        archive.signatures = arrayOf(first); modern(archive, arrayOf(first))
        try { UpdateDownloads.checkPackage(archive, installed, "99.0"); fail("Accepted a missing signer") } catch (_: UpdateDownloads.SigningFailure) { }
    }
    @Test fun diagnosticPanelCannotInstallAndCopiesOnlyAfterUserAction() {
        val activity = Robolectric.buildActivity(UpdateActivity::class.java).setup().get()
        val failure = UpdateDownloads.SigningFailure("Certificate unavailable", setOf(hash(first)), emptySet())
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "showError", ReflectionHelpers.ClassParameter.from(Exception::class.java, failure))
        assertEquals(View.GONE, activity.findViewById<View>(R.id.install_update).visibility)
        activity.findViewById<View>(R.id.certificate_details).performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        assertNull(shadowOf(activity).nextStartedActivity)
        assertNull(activity.getSystemService(ClipboardManager::class.java).primaryClip)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(failure.diagnostics(), activity.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString())
    }
    private fun hash(signature: Signature): String = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
}
