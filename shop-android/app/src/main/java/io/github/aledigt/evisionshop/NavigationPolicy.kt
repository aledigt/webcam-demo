package io.github.aledigt.evisionshop

import java.net.URI
import java.net.URLDecoder

/**
 * Правила навигации обёртки.
 * Внутри приложения остаются только HTTPS-страницы магазина и облака,
 * куда PWA переходит сама. Остальной HTTPS открывается снаружи.
 * HTTP, intent, file и javascript в WebView не загружаются.
 */
object NavigationPolicy {
  const val START_URL = "https://shop.evision.tech/"
  const val VIDEO_CAPTURE = "android.webkit.resource.VIDEO_CAPTURE"

  private val inAppHosts = setOf("shop.evision.tech", "cloud.evision.tech")
  private val externalSchemes = setOf("mailto", "tel")

  enum class Decision { LOAD_IN_APP, OPEN_EXTERNAL, BLOCK }
  enum class BackAction { WEBVIEW_BACK, FINISH }

  fun decide(url: String?, mainFrame: Boolean): Decision {
    if (url.isNullOrBlank() || url.any { it.isISOControl() || it == ' ' || it == '\\' }) {
      return Decision.BLOCK
    }
    val uri = parse(url) ?: return Decision.BLOCK
    val scheme = uri.scheme?.lowercase() ?: return Decision.BLOCK
    if (scheme in externalSchemes) {
      return if (mainFrame) Decision.OPEN_EXTERNAL else Decision.BLOCK
    }
    if (scheme != "https" || uri.isOpaque || uri.userInfo != null) return Decision.BLOCK
    val host = normalizeHost(uri.host) ?: return Decision.BLOCK
    if (host in inAppHosts) return Decision.LOAD_IN_APP
    return if (mainFrame) Decision.OPEN_EXTERNAL else Decision.BLOCK
  }

  fun backAction(canGoBack: Boolean): BackAction =
    if (canGoBack) BackAction.WEBVIEW_BACK else BackAction.FINISH

  fun grantableWebResources(requested: Collection<String>): Set<String> =
    requested.filter { it == VIDEO_CAPTURE }.toSet()

  fun isShopDocument(url: String?): Boolean {
    if (url == null || decide(url, true) != Decision.LOAD_IN_APP) return false
    val uri = parse(url) ?: return false
    if (normalizeHost(uri.host) != "shop.evision.tech") return false
    val path = uri.path ?: return true
    return path.isEmpty() || path == "/" || path == "/index.html"
  }

  fun suggestedDownloadName(contentDisposition: String?, url: String): String {
    val header = contentDisposition ?: ""
    val starred = Regex("(?i)filename\\*=(?:UTF-8'')?([^;]+)").find(header)
    val plain = Regex("(?i)filename=\"?([^\";]+)\"?").find(header)
    val raw = (starred?.groupValues?.get(1) ?: plain?.groupValues?.get(1))?.trim()?.trim('"')
    val fromUrl = parse(url)?.path?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    val decoded = urlDecode(raw ?: fromUrl ?: "download")
    val cleaned = decoded.replace(Regex("[^\\p{L}\\p{N}._-]+"), "_").trim('_', '.', ' ').take(80)
    return if (cleaned.isBlank() || cleaned == "." || cleaned == "..") "download" else cleaned
  }

  private fun parse(url: String): URI? = try {
    URI(url.trim())
  } catch (_: Exception) {
    null
  }

  private fun normalizeHost(host: String?): String? {
    val value = host?.trim()?.lowercase()?.removeSuffix(".") ?: return null
    if (value.isEmpty() || value.contains('\\') || value.contains(' ')) return null
    return value
  }

  private fun urlDecode(raw: String): String = try {
    URLDecoder.decode(raw, Charsets.UTF_8.name())
  } catch (_: Exception) {
    raw
  }
}
