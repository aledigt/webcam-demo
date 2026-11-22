package io.github.aledigt.evisionshop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ManifestPolicyTest {
  @Test fun manifestKeepsCleartextOffAndPermissionsNarrow() {
    val xml = readProjectFile("src/main/AndroidManifest.xml")
    assertTrue(xml.contains("android.permission.INTERNET"))
    assertTrue(xml.contains("android.permission.CAMERA"))
    assertTrue(xml.contains("android:usesCleartextTraffic=\"false\""))
    assertFalse(xml.contains("usesCleartextTraffic=\"true\""))
    assertFalse(xml.contains("RECORD_AUDIO"))
    assertFalse(xml.contains("ACCESS_FINE_LOCATION"))
    assertFalse(xml.contains("ACCESS_COARSE_LOCATION"))
    assertFalse(xml.contains("READ_EXTERNAL_STORAGE"))
    assertFalse(xml.contains("WRITE_EXTERNAL_STORAGE"))
    assertFalse(xml.contains("QUERY_ALL_PACKAGES"))
    assertFalse(xml.contains("SYSTEM_ALERT_WINDOW"))
    assertFalse(xml.contains("REQUEST_INSTALL_PACKAGES"))
  }

  @Test fun networkConfigTrustsSystemCertificatesOnly() {
    val xml = readProjectFile("src/main/res/xml/network_security_config.xml")
    assertTrue(xml.contains("cleartextTrafficPermitted=\"false\""))
    assertTrue(xml.contains("src=\"system\""))
    assertFalse(xml.contains("src=\"user\""))
    assertFalse(xml.contains("trust-anchors") && xml.contains("src=\"user\""))
  }

  @Test fun activityDoesNotBypassTlsOrAddAJavascriptBridge() {
    val src = readProjectFile("src/main/java/io/github/aledigt/evisionshop/MainActivity.kt")
    assertFalse(src.contains("onReceivedSslError"))
    assertFalse(src.contains("MIXED_CONTENT_ALWAYS_ALLOW"))
    assertFalse(src.contains("addJavascriptInterface"))
    assertFalse(src.contains("setWebContentsDebuggingEnabled(true)"))
    assertFalse(Regex("allowUniversalAccessFromFileURLs\\s*=\\s*true").containsMatchIn(src))
    assertFalse(Regex("allowFileAccessFromFileURLs\\s*=\\s*true").containsMatchIn(src))
    assertFalse(src.contains("sslError"))
    assertTrue(src.contains("MIXED_CONTENT_NEVER_ALLOW"))
    assertTrue(src.contains("setAcceptThirdPartyCookies(view, false)"))
  }

  private fun readProjectFile(relative: String): String {
    val candidates = listOf(
      File(relative),
      File("app/$relative"),
      File("shop-android/app/$relative")
    )
    val file = candidates.firstOrNull { it.isFile }
      ?: error("Не найден $relative из ${File(".").absolutePath}")
    return file.readText()
  }
}
