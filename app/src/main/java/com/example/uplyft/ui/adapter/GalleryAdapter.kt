package com.example.uplyft.ui.adapter

import androidx.recyclerview.widget.RecyclerView
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.uplyft.R
import android.widget.ImageView
import com.bumptech.glide.Glide

// ui/adapter/GalleryAdapter.kt
class GalleryAdapter(
    private val onImageSelected: (Uri) -> Unit,
    private val onCameraClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_CAMERA = 0
        private const val TYPE_IMAGE  = 1
    }

    private val images = mutableListOf<Uri>()
    private var selectedPosition = 1

    fun submitList(uris: List<Uri>) {
        images.clear()
        images.addAll(uris)
        notifyDataSetChanged()
    }

    // Total = camera tile + images
    override fun getItemCount() = images.size + 1

    override fun getItemViewType(position: Int) =
        if (position == 0) TYPE_CAMERA else TYPE_IMAGE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_CAMERA) {
            val view = inflater.inflate(R.layout.item_gallery_camera, parent, false)
            CameraViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_gallery_image, parent, false)
            ImageViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CameraViewHolder -> {
                holder.itemView.setOnClickListener { onCameraClick() }
            }
            is ImageViewHolder -> {
                // position - 1 because position 0 is camera
                val uri = images[position - 1]

                Glide.with(holder.itemView)
                    .load(uri)
                    .centerCrop()
                    .into(holder.imageView)

                // Dim unselected
                holder.itemView.alpha = if (position == selectedPosition) 1f else 0.6f

                holder.itemView.setOnClickListener {
                    val prev = selectedPosition
                    selectedPosition = holder.bindingAdapterPosition
                    notifyItemChanged(prev)
                    notifyItemChanged(selectedPosition)
                    onImageSelected(uri)
                }
            }
        }
    }

    // ViewHolders
    class CameraViewHolder(view: View) : RecyclerView.ViewHolder(view)

    class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.ivGalleryImage)
    }
}