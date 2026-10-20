package io.github.aledigt.webcamdemo;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.*;
import android.widget.Toast;
import androidx.webkit.WebViewAssetLoader;
import java.io.OutputStream;

public class MainActivity extends Activity {
  private static final int REQ = 1;
  private WebView web;
  private PermissionRequest pending;

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
    web = new WebView(this);
    setContentView(web);
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setMediaPlaybackRequiresUserGesture(false);
    s.setAllowFileAccess(false);
    web.setWebViewClient(new WebViewClient() {
      @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
        return loader.shouldInterceptRequest(r.getUrl());
      }
      @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
        return !"appassets.androidplatform.net".equals(r.getUrl().getHost());
      }
    });
    web.setWebChromeClient(new WebChromeClient() {
      @Override public void onPermissionRequest(final PermissionRequest r) {
        runOnUiThread(() -> {
          if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) grant(r);
          else { pending = r; requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ); }
        });
      }
    });
    web.setDownloadListener((url, ua, cd, mime, len) -> saveDataUrl(url));
    if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
      requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ);
    web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
  }

  private void grant(PermissionRequest r) {
    for (String res : r.getResources())
      if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res)) {
        r.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE}); return;
      }
    r.deny();
  }

  @Override public void onRequestPermissionsResult(int c, String[] p, int[] g) {
    super.onRequestPermissionsResult(c, p, g);
    if (pending == null) return;
    if (g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) grant(pending);
    else { pending.deny(); Toast.makeText(this, "Нет доступа к камере", Toast.LENGTH_LONG).show(); }
    pending = null;
  }

  private void saveDataUrl(String url) {
    try {
      if (url == null || !url.startsWith("data:") || Build.VERSION.SDK_INT < 29) {
        Toast.makeText(this, "Сохранение доступно на Android 10+", Toast.LENGTH_SHORT).show(); return;
      }
      byte[] data = Base64.decode(url.substring(url.indexOf(',') + 1), Base64.DEFAULT);
      ContentValues v = new ContentValues();
      v.put(MediaStore.Images.Media.DISPLAY_NAME, "webcam-" + System.currentTimeMillis() + ".png");
      v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
      v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WebcamDemo");
      Uri u = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
      try (OutputStream os = getContentResolver().openOutputStream(u)) { os.write(data); }
      Toast.makeText(this, "Снимок сохранён в Pictures/WebcamDemo", Toast.LENGTH_SHORT).show();
    } catch (Exception e) {
      Toast.makeText(this, "Не удалось сохранить снимок", Toast.LENGTH_SHORT).show();
    }
  }

  @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }
}
