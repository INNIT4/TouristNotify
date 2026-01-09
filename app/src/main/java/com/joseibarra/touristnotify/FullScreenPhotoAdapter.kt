package com.joseibarra.touristnotify

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.joseibarra.touristnotify.databinding.ListItemFullscreenPhotoBinding

/**
 * Adapter para mostrar fotos en pantalla completa con ViewPager2
 */
class FullScreenPhotoAdapter : ListAdapter<PlacePhoto, FullScreenPhotoAdapter.FullScreenPhotoViewHolder>(PhotoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FullScreenPhotoViewHolder {
        val binding = ListItemFullscreenPhotoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FullScreenPhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FullScreenPhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FullScreenPhotoViewHolder(
        private val binding: ListItemFullscreenPhotoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(photo: PlacePhoto) {
            // Cargar imagen en pantalla completa con zoom support
            Glide.with(binding.photoImageView.context)
                .load(photo.imageUrl)
                .placeholder(R.drawable.ic_placeholder_image)
                .error(R.drawable.ic_error_image)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .fitCenter()
                .into(binding.photoImageView)
        }
    }

    class PhotoDiffCallback : DiffUtil.ItemCallback<PlacePhoto>() {
        override fun areItemsTheSame(oldItem: PlacePhoto, newItem: PlacePhoto): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PlacePhoto, newItem: PlacePhoto): Boolean {
            return oldItem == newItem
        }
    }
}
