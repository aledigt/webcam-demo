package io.github.aledigt.webcamdemo

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import io.github.aledigt.webcamdemo.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
  private lateinit var b: ActivityMainBinding
  private var provider: ProcessCameraProvider? = null
  private var imageCapture: ImageCapture? = null
  private var lensFacing = CameraSelector.LENS_FACING_BACK
  private var filter = Filter.NONE
  private val shots = mutableListOf<Shot>()
  private lateinit var adapter: GalleryAdapter

  private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { updatePermissionState() }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    b = ActivityMainBinding.inflate(layoutInflater)
    setContentView(b.root)
    b.preview.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    b.preview.scaleType = PreviewView.ScaleType.FILL_CENTER

    Filter.values().forEach { f ->
      val chip = Chip(this).apply {
        text = getString(f.label); isCheckable = true; isChecked = f == Filter.NONE
        setOnClickListener { filter = f; applyPreviewFilter() }
      }
      b.filters.addView(chip)
    }
    adapter = GalleryAdapter(shots)
    b.gallery.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    b.gallery.adapter = adapter

    b.switchBtn.setOnClickListener {
      lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
      bindCamera()
    }
    b.captureBtn.setOnClickListener { capture() }
    b.permButton.setOnClickListener {
      if (!hasCamera() && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) && asked) {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
      } else requestPerms()
    }
    if (hasCamera()) updatePermissionState() else requestPerms()
  }

  private var asked = false
  private fun requestPerms() {
    asked = true
    val perms = mutableListOf(Manifest.permission.CAMERA)
    if (Build.VERSION.SDK_INT < 29) perms += Manifest.permission.WRITE_EXTERNAL_STORAGE
    permLauncher.launch(perms.toTypedArray())
  }

  private fun hasCamera() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

  override fun onResume() { super.onResume(); if (asked || hasCamera()) updatePermissionState() }

  private fun updatePermissionState() {
    if (hasCamera()) {
      b.permPanel.visibility = View.GONE
      b.captureBtn.isEnabled = true; b.switchBtn.isEnabled = true
      if (provider == null) startCamera()
    } else {
      b.permPanel.visibility = View.VISIBLE
      b.captureBtn.isEnabled = false; b.switchBtn.isEnabled = false
      val forever = asked && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
      b.permText.setText(if (forever) R.string.perm_denied_forever else R.string.perm_rationale)
      b.permButton.setText(if (forever) R.string.open_settings else R.string.grant)
    }
  }

  private fun startCamera() {
    val f = ProcessCameraProvider.getInstance(this)
    f.addListener({
      provider = f.get()
      val p = provider!!
      if (!p.hasCamera(CameraSelector.Builder().requireLensFacing(lensFacing).build())) {
        lensFacing = if (p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
      }
      b.switchBtn.isEnabled = runCatching { p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) && p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false)
      bindCamera()
    }, ContextCompat.getMainExecutor(this))
  }

  private fun bindCamera() {
    val p = provider ?: return
    val preview = Preview.Builder().build().also { it.setSurfaceProvider(b.preview.surfaceProvider) }
    imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    try {
      p.unbindAll()
      p.bindToLifecycle(this, CameraSelector.Builder().requireLensFacing(lensFacing).build(), preview, imageCapture)
    } catch (e: Exception) {
      Toast.makeText(this, R.string.camera_error, Toast.LENGTH_LONG).show()
    }
    applyPreviewFilter()
  }

  private fun applyPreviewFilter() {
    // PreviewView already mirrors the front camera preview itself.
    val m = filter.matrix()
    if (m == null) b.preview.setLayerType(View.LAYER_TYPE_NONE, null)
    else b.preview.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = ColorMatrixColorFilter(m) })
  }

  private fun capture() {
    val ic = imageCapture ?: return
    b.captureBtn.isEnabled = false
    val front = lensFacing == CameraSelector.LENS_FACING_FRONT
    val f = filter
    ic.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
      override fun onCaptureSuccess(image: ImageProxy) {
        val rot = image.imageInfo.rotationDegrees
        val raw = try { image.toBitmap() } catch (e: Exception) { null } finally { image.close() }
        if (raw == null) { fail(); return }
        lifecycleScope.launch {
          val res = withContext(Dispatchers.Default) {
            runCatching {
              val bmp = process(raw, rot, front, f)
              val uri = save(bmp)
              val thumb = Bitmap.createScaledBitmap(bmp, 240, (240f * bmp.height / bmp.width).toInt().coerceAtLeast(1), true)
              Shot(uri, thumb)
            }.getOrNull()
          }
          b.captureBtn.isEnabled = true
          if (res == null) fail() else {
            shots.add(0, res); if (shots.size > 30) shots.removeAt(shots.size - 1)
            adapter.notifyDataSetChanged(); b.gallery.scrollToPosition(0)
            b.galleryTitle.setText(R.string.gallery)
            Toast.makeText(this@MainActivity, R.string.saved, Toast.LENGTH_SHORT).show()
          }
        }
      }
      override fun onError(e: ImageCaptureException) { b.captureBtn.isEnabled = true; fail() }
    })
  }

  private fun fail() = Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()

  private fun process(src: Bitmap, rot: Int, mirror: Boolean, f: Filter): Bitmap {
    val m = Matrix().apply { postRotate(rot.toFloat()); if (mirror) postScale(-1f, 1f) }
    val oriented = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    val cm = f.matrix() ?: return oriented
    val out = Bitmap.createBitmap(oriented.width, oriented.height, Bitmap.Config.ARGB_8888)
    Canvas(out).drawBitmap(oriented, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
    return out
  }

  private fun save(bmp: Bitmap): Uri {
    val name = "webcam-${System.currentTimeMillis()}.jpg"
    val v = ContentValues().apply {
      put(MediaStore.Images.Media.DISPLAY_NAME, name)
      put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
    }
    if (Build.VERSION.SDK_INT >= 29) {
      v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WebcamDemo")
      v.put(MediaStore.Images.Media.IS_PENDING, 1)
      val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)!!
      contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
      contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
      return uri
    } else {
      @Suppress("DEPRECATION")
      val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "WebcamDemo").apply { mkdirs() }
      val file = File(dir, name)
      FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
      @Suppress("DEPRECATION")
      v.put(MediaStore.Images.Media.DATA, file.absolutePath)
      return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: Uri.fromFile(file)
    }
  }
}
