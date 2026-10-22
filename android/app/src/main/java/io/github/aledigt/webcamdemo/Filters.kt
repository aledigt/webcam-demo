package io.github.aledigt.webcamdemo

import android.graphics.ColorMatrix
import kotlin.math.cos
import kotlin.math.sin

enum class Filter(val label: Int) {
  NONE(R.string.f_none), GRAY(R.string.f_gray), SEPIA(R.string.f_sepia),
  INVERT(R.string.f_invert), VIVID(R.string.f_vivid), HUE(R.string.f_hue);

  fun matrix(): ColorMatrix? = when (this) {
    NONE -> null
    GRAY -> ColorMatrix().apply { setSaturation(0f) }
    SEPIA -> ColorMatrix(floatArrayOf(
      0.393f, 0.769f, 0.189f, 0f, 0f,
      0.349f, 0.686f, 0.168f, 0f, 0f,
      0.272f, 0.534f, 0.131f, 0f, 0f,
      0f, 0f, 0f, 1f, 0f))
    INVERT -> ColorMatrix(floatArrayOf(
      -1f, 0f, 0f, 0f, 255f,
      0f, -1f, 0f, 0f, 255f,
      0f, 0f, -1f, 0f, 255f,
      0f, 0f, 0f, 1f, 0f))
    VIVID -> ColorMatrix().apply {
      setSaturation(1.8f)
      val c = 1.2f; val t = (1 - c) * 128f
      postConcat(ColorMatrix(floatArrayOf(c,0f,0f,0f,t, 0f,c,0f,0f,t, 0f,0f,c,0f,t, 0f,0f,0f,1f,0f)))
    }
    HUE -> hueRotate(180f)
  }

  private fun hueRotate(deg: Float): ColorMatrix {
    val r = Math.toRadians(deg.toDouble()); val c = cos(r).toFloat(); val s = sin(r).toFloat()
    // Same as CSS hue-rotate()
    return ColorMatrix(floatArrayOf(
      0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f, 0f,
      0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f, 0f,
      0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f, 0f,
      0f, 0f, 0f, 1f, 0f))
  }
}
