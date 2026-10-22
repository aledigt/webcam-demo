package io.github.aledigt.webcamdemo

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

data class Shot(val uri: Uri, val thumb: Bitmap)

class GalleryAdapter(private val items: MutableList<Shot>) : RecyclerView.Adapter<GalleryAdapter.VH>() {
  class VH(val img: ImageView) : RecyclerView.ViewHolder(img)

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
    val d = parent.resources.displayMetrics.density
    val iv = ImageView(parent.context).apply {
      layoutParams = ViewGroup.MarginLayoutParams((84 * d).toInt(), (84 * d).toInt()).apply { marginEnd = (6 * d).toInt() }
      scaleType = ImageView.ScaleType.CENTER_CROP
    }
    return VH(iv)
  }

  override fun onBindViewHolder(h: VH, pos: Int) {
    val s = items[pos]
    h.img.setImageBitmap(s.thumb)
    h.img.setOnClickListener {
      val i = Intent(Intent.ACTION_VIEW).setDataAndType(s.uri, "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      runCatching { it.context.startActivity(i) }
    }
  }

  override fun getItemCount() = items.size
}
