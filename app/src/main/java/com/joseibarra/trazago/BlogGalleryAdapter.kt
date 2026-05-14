package com.joseibarra.trazago

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class BlogGalleryAdapter(
    private val urls: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<BlogGalleryAdapter.VH>() {

    inner class VH(val image: ImageView) : RecyclerView.ViewHolder(image)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_blog_gallery_thumb, parent, false) as ImageView
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val url = urls[position]
        Glide.with(holder.image)
            .load(url)
            .centerCrop()
            .into(holder.image)
        holder.image.setOnClickListener { onClick(url) }
    }

    override fun getItemCount() = urls.size
}
