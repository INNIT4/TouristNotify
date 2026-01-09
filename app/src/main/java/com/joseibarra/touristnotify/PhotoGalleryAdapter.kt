package com.joseibarra.touristnotify

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.joseibarra.touristnotify.databinding.ListItemPhotoGalleryBinding
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Adapter para mostrar fotos en la galería
 */
class PhotoGalleryAdapter(
    private val onPhotoClick: (PlacePhoto, Int) -> Unit
) : ListAdapter<PlacePhoto, PhotoGalleryAdapter.PhotoViewHolder>(PhotoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ListItemPhotoGalleryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class PhotoViewHolder(
        private val binding: ListItemPhotoGalleryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(photo: PlacePhoto, position: Int) {
            // Cargar imagen con Glide
            Glide.with(binding.photoImageView.context)
                .load(photo.imageUrl)
                .placeholder(R.drawable.ic_placeholder_image)
                .error(R.drawable.ic_error_image)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .into(binding.photoImageView)

            // Mostrar caption si existe
            if (photo.caption.isNotBlank()) {
                binding.captionTextView.text = photo.caption
            } else {
                binding.captionTextView.text = ""
            }

            // Fecha de subida
            photo.uploadedAt?.let { date ->
                val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale("es", "MX"))
                binding.dateTextView.text = dateFormat.format(date)
            }

            // Likes
            binding.likesTextView.text = "❤️ ${photo.likes}"

            // Click en la foto
            binding.root.setOnClickListener {
                onPhotoClick(photo, position)
            }
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
