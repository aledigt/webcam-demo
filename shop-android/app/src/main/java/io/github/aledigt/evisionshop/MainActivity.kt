package io.github.aledigt.evisionshop

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.Charset

class MainActivity : ComponentActivity() {
  private lateinit var webContainer: FrameLayout
  private lateinit var errorPanel: View
  private lateinit var errorText: TextView
  private lateinit var web: WebView
  private var mainFrameFailed = false
  private var webViewDead = false
  private var sawCameraGranted = false
  private var documentStartSupported = false
  private var fileCallback: ValueCallback<Array<Uri>>? = null

  private val cameraPermission = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    sawCameraGranted = granted
    if (!granted) explainCameraDenied()
    if (!::web.isInitialized || webViewDead) return@registerForActivityResult
    if (web.url == null) web.loadUrl(NavigationPolicy.START_URL)
    else if (granted) web.reload()
  }

  private val fileChooser = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) { result ->
    val callback = fileCallback
    fileCallback = null
    val uris = if (result.resultCode == RESULT_OK) {
      WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
    } else {
      null
    }
    callback?.onReceiveValue(uris)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    webContainer = findViewById(R.id.webContainer)
    errorPanel = findViewById(R.id.errorPanel)
    errorText = findViewById(R.id.errorText)
    findViewById<Button>(R.id.retryButton).setOnClickListener { retry() }
    web = createWebView()
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        if (webViewDead || !::web.isInitialized) {
          finish()
          return
        }
        if (NavigationPolicy.backAction(web.canGoBack()) == NavigationPolicy.BackAction.WEBVIEW_BACK) {
          errorPanel.visibility = View.GONE
          web.goBack()
        } else {
          finish()
        }
      }
    })
    sawCameraGranted = hasCamera()
    if (savedInstanceState != null) {
      web.restoreState(savedInstanceState)
    } else if (sawCameraGranted) {
      web.loadUrl(NavigationPolicy.START_URL)
    }
    if (!sawCameraGranted) cameraPermission.launch(android.Manifest.permission.CAMERA)
  }

  override fun onResume() {
    super.onResume()
    val granted = hasCamera()
    if (granted && !sawCameraGranted && ::web.isInitialized && !webViewDead && web.url != null) {
      web.reload()
    }
    sawCameraGranted = granted
    if (::web.isInitialized && !webViewDead) CookieManager.getInstance().flush()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    if (::web.isInitialized && !webViewDead) web.saveState(outState)
  }

  override fun onDestroy() {
    fileCallback?.onReceiveValue(null)
    fileCallback = null
    if (::web.isInitialized) {
      (web.parent as? ViewGroup)?.removeView(web)
      web.destroy()
    }
    super.onDestroy()
  }

  private fun retry() {
    if (webViewDead || !::web.isInitialized) {
      recreate()
      return
    }
    errorPanel.visibility = View.GONE
    mainFrameFailed = false
    if (web.url == null) web.loadUrl(NavigationPolicy.START_URL) else web.reload()
  }

  private fun hasCamera(): Boolean =
    ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) ==
      android.content.pm.PackageManager.PERMISSION_GRANTED

  private fun explainCameraDenied() {
    val permanent = !shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)
    if (!permanent) {
      Toast.makeText(this, R.string.camera_denied, Toast.LENGTH_LONG).show()
      return
    }
    AlertDialog.Builder(this)
      .setMessage(R.string.camera_denied)
      .setPositiveButton(R.string.open_settings) { _, _ ->
        startActivity(
          Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        )
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun showError(message: CharSequence) {
    errorText.text = message
    errorPanel.visibility = View.VISIBLE
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun createWebView(): WebView {
    val view = WebView(this)
    webContainer.addView(
      view,
      FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
    )
    val settings = view.settings
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.mediaPlaybackRequiresUserGesture = false
    settings.allowFileAccess = false
    settings.allowContentAccess = true
    @Suppress("DEPRECATION")
    settings.allowFileAccessFromFileURLs = false
    @Suppress("DEPRECATION")
    settings.allowUniversalAccessFromFileURLs = false
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    settings.setGeolocationEnabled(false)
    settings.javaScriptCanOpenWindowsAutomatically = false
    settings.setSupportMultipleWindows(false)
    settings.cacheMode = WebSettings.LOAD_DEFAULT
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    settings.userAgentString = settings.userAgentString + " EvisionShopWebView/1.0"
    view.isFocusable = true
    view.isFocusableInTouchMode = true
    val cookies = CookieManager.getInstance()
    cookies.setAcceptCookie(true)
    cookies.setAcceptThirdPartyCookies(view, false)
    documentStartSupported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    if (documentStartSupported) {
      WebViewCompat.addDocumentStartJavaScript(
        view,
        DocumentStart.SCRIPT,
        setOf("https://shop.evision.tech")
      )
    }
    view.webViewClient = client
    view.webChromeClient = chrome
    view.setDownloadListener { url, userAgent, contentDisposition, mime, _ ->
      when (NavigationPolicy.decide(url, true)) {
        NavigationPolicy.Decision.LOAD_IN_APP ->
          enqueueDownload(url, userAgent, contentDisposition, mime)
        NavigationPolicy.Decision.OPEN_EXTERNAL -> openExternal(Uri.parse(url))
        NavigationPolicy.Decision.BLOCK ->
          Toast.makeText(this, R.string.blocked_url, Toast.LENGTH_SHORT).show()
      }
    }
    return view
  }

  private val client = object : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
      val decision = NavigationPolicy.decide(request.url.toString(), request.isForMainFrame)
      return when (decision) {
        NavigationPolicy.Decision.LOAD_IN_APP -> false
        NavigationPolicy.Decision.OPEN_EXTERNAL -> {
          if (request.isForMainFrame) openExternal(request.url)
          true
        }
        NavigationPolicy.Decision.BLOCK -> {
          if (request.isForMainFrame) {
            Toast.makeText(this@MainActivity, R.string.blocked_url, Toast.LENGTH_SHORT).show()
          }
          true
        }
      }
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
      if (documentStartSupported) return null
      if (!request.isForMainFrame || !request.method.equals("GET", true)) return null
      if (!NavigationPolicy.isShopDocument(request.url.toString())) return null
      return fetchShopDocument(request)
    }

    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
      mainFrameFailed = false
    }

    override fun onPageFinished(view: WebView, url: String) {
      if (!mainFrameFailed) errorPanel.visibility = View.GONE
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
      if (!request.isForMainFrame) return
      mainFrameFailed = true
      showError(getString(R.string.load_error))
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
      webViewDead = true
      (view.parent as? ViewGroup)?.removeView(view)
      view.destroy()
      showError(getString(R.string.webview_crashed))
      return true
    }
  }

  private val chrome = object : WebChromeClient() {
    override fun onPermissionRequest(request: PermissionRequest) {
      runOnUiThread {
        if (isDestroyed) {
          request.deny()
          return@runOnUiThread
        }
        val grant = NavigationPolicy.grantableWebResources(request.resources.toList())
        if (grant.isEmpty() || !hasCamera()) request.deny()
        else request.grant(grant.toTypedArray())
      }
    }

    override fun onShowFileChooser(
      webView: WebView,
      callback: ValueCallback<Array<Uri>>,
      params: FileChooserParams
    ): Boolean {
      fileCallback?.onReceiveValue(null)
      fileCallback = callback
      return try {
        fileChooser.launch(params.createIntent())
        true
      } catch (_: ActivityNotFoundException) {
        fileCallback = null
        callback.onReceiveValue(null)
        false
      }
    }

    override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
      if (!trusted(url)) {
        result.cancel()
        return true
      }
      AlertDialog.Builder(this@MainActivity)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
        .setOnCancelListener { result.cancel() }
        .show()
      return true
    }

    override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
      if (!trusted(url)) {
        result.cancel()
        return true
      }
      AlertDialog.Builder(this@MainActivity)
        .setMessage(message)
        .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
        .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
        .setOnCancelListener { result.cancel() }
        .show()
      return true
    }

    override fun onJsPrompt(
      view: WebView,
      url: String,
      message: String,
      defaultValue: String,
      result: JsPromptResult
    ): Boolean {
      result.cancel()
      return true
    }
  }

  private fun trusted(url: String): Boolean =
    NavigationPolicy.decide(url, true) == NavigationPolicy.Decision.LOAD_IN_APP

  private fun openExternal(uri: Uri) {
    try {
      startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
      Toast.makeText(this, R.string.no_handler, Toast.LENGTH_SHORT).show()
    }
  }

  private fun enqueueDownload(url: String, userAgent: String?, contentDisposition: String?, mime: String?) {
    try {
      val name = NavigationPolicy.suggestedDownloadName(contentDisposition, url)
      val request = DownloadManager.Request(Uri.parse(url))
      if (!userAgent.isNullOrBlank()) request.addRequestHeader("User-Agent", userAgent)
      CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
      if (!mime.isNullOrBlank()) request.setMimeType(mime)
      request.setTitle(name)
      request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
      request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
      getSystemService(DownloadManager::class.java).enqueue(request)
      Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
      Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show()
    }
  }

  private fun fetchShopDocument(request: WebResourceRequest): WebResourceResponse? {
    val url = request.url.toString()
    if (!NavigationPolicy.isShopDocument(url)) return null
    val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
      instanceFollowRedirects = false
      connectTimeout = 15_000
      readTimeout = 20_000
      requestMethod = "GET"
      setRequestProperty("Accept", header(request, "Accept") ?: "text/html")
      header(request, "Accept-Language")?.let { setRequestProperty("Accept-Language", it) }
      header(request, "User-Agent")?.let { setRequestProperty("User-Agent", it) }
      CookieManager.getInstance().getCookie(url)?.let { setRequestProperty("Cookie", it) }
    }
    return try {
      connection.connect()
      val code = connection.responseCode
      val reason = (connection.responseMessage ?: "")
        .filter { it.code in 32..126 }
        .ifBlank { "OK" }
      connection.headerFields["Set-Cookie"]?.forEach { CookieManager.getInstance().setCookie(url, it) }
      if (code in 300..399) {
        val location = connection.getHeaderField("Location") ?: return null
        WebResourceResponse(
          "text/plain",
          "utf-8",
          code,
          reason,
          mapOf("Location" to location),
          ByteArrayInputStream(ByteArray(0))
        )
      } else {
        val stream = if (code >= 400) connection.errorStream else connection.inputStream
        val bytes = stream?.use { it.readNBytes(3_000_000) } ?: ByteArray(0)
        val mime = connection.contentType?.substringBefore(';')?.trim()?.ifBlank { null } ?: "text/html"
        val charset = connection.contentEncodingHeader()
        val body = if (code == 200 && mime.contains("html", true)) {
          val html = bytes.toString(Charset.forName(charset))
          DocumentStart.inject(html).toByteArray(Charset.forName(charset))
        } else {
          bytes
        }
        WebResourceResponse(mime, charset, code, reason, mapOf("Content-Type" to "$mime; charset=$charset"), ByteArrayInputStream(body))
      }
    } catch (_: Exception) {
      null
    } finally {
      connection.disconnect()
    }
  }

  private fun header(request: WebResourceRequest, name: String): String? =
    request.requestHeaders.entries.firstOrNull { it.key.equals(name, true) }?.value

  private fun HttpURLConnection.contentEncodingHeader(): String {
    val type = contentType ?: return "utf-8"
    val mark = "charset="
    val index = type.lowercase().indexOf(mark)
    if (index < 0) return "utf-8"
    return type.substring(index + mark.length).substringBefore(';').trim().trim('"').ifBlank { "utf-8" }
  }
}
