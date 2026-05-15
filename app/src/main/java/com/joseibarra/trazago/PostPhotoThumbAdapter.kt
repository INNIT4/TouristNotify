package com.joseibarra.trazago

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.joseibarra.trazago.databinding.ItemPostPhotoThumbnailBinding

class PostPhotoThumbAdapter(
    private val onRemove: (Uri) -> Unit,
) : RecyclerView.Adapter<PostPhotoThumbAdapter.ThumbViewHolder>() {

    private val items = mutableListOf<Uri>()

    fun submitList(uris: List<Uri>) {
        items.clear()
        items.addAll(uris)
        notifyDataSetChanged()
    }

    fun getItems(): List<Uri> = items.toList()

    inner class ThumbViewHolder(private val b: ItemPostPhotoThumbnailBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(uri: Uri) {
            Glide.with(b.root.context).load(uri).centerCrop().into(b.photoPreview)
            b.removePhotoButton.setOnClickListener { onRemove(uri) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbViewHolder {
        val b = ItemPostPhotoThumbnailBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ThumbViewHolder(b)
    }

    override fun onBindViewHolder(holder: ThumbViewHolder, position: Int) =
        holder.bind(items[position])

    override fun getItemCount() = items.size
}
