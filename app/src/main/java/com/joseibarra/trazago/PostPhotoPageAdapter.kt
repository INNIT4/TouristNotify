package com.joseibarra.trazago

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.joseibarra.trazago.databinding.ItemPostPhotoPageBinding

class PostPhotoPageAdapter(private val urls: List<String>) :
    RecyclerView.Adapter<PostPhotoPageAdapter.PhotoVH>() {

    inner class PhotoVH(private val b: ItemPostPhotoPageBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(url: String) {
            Glide.with(b.root.context).load(SafeImageUrl.sanitize(url))
                .centerCrop()
                .placeholder(R.drawable.bg_hero_placeholder)
                .into(b.photo)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoVH {
        val b = ItemPostPhotoPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoVH(b)
    }

    override fun onBindViewHolder(holder: PhotoVH, position: Int) = holder.bind(urls[position])
    override fun getItemCount() = urls.size
}
