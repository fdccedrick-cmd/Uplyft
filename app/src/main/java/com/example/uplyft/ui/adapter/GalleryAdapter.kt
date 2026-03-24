package com.example.uplyft.ui.adapter

import androidx.recyclerview.widget.RecyclerView
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.uplyft.R
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.example.uplyft.databinding.ItemGalleryImageBinding


// ui/adapter/GalleryAdapter.kt
class GalleryAdapter(
    private val onImageSelected : (Uri) -> Unit,
    private val onCameraClick   : () -> Unit,
    private val onMultiSelected : (List<Uri>) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_CAMERA = 0
        private const val TYPE_IMAGE  = 1
        const val MAX_SELECT          = 10
    }

    private val images          = mutableListOf<Uri>()
    private var selectedPosition = 1
    var isMultiSelectMode        = false
        private set
    private val selectedUris    = mutableListOf<Uri>()

    fun submitList(uris: List<Uri>) {
        images.clear()
        images.addAll(uris)
        notifyDataSetChanged()
    }
    //  Reset multi-select state when returning to fragment
    fun resetMultiSelect() {
        isMultiSelectMode = false
        selectedUris.clear()
        selectedPosition = 1
        notifyDataSetChanged()
    }

    // Explicitly enter multi-select mode
    fun enterMultiSelectMode() {
        isMultiSelectMode = true
        selectedUris.clear()
        notifyDataSetChanged()
    }

    // Explicitly exit multi-select mode
    fun exitMultiSelectMode() {
        isMultiSelectMode = false
        selectedUris.clear()
        notifyDataSetChanged()
    }

    fun getSelectedUris(): List<Uri> = selectedUris.toList()

    override fun getItemCount() = images.size + 1
    override fun getItemViewType(position: Int) =
        if (position == 0) TYPE_CAMERA else TYPE_IMAGE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_CAMERA) {
            CameraViewHolder(
                inflater.inflate(R.layout.item_gallery_camera, parent, false)
            )
        } else {
            ImageViewHolder(
                ItemGalleryImageBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CameraViewHolder -> holder.itemView.setOnClickListener { onCameraClick() }
            is ImageViewHolder  -> holder.bind(images[position - 1], position)
        }
    }

    inner class ImageViewHolder(
        private val binding: ItemGalleryImageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(uri: Uri, position: Int) {
            Glide.with(binding.root)
                .load(uri)
                .centerCrop()
                .into(binding.ivGalleryImage)

            if (isMultiSelectMode) {
                // show circle indicators
                val selectionIndex = selectedUris.indexOf(uri)
                val isSelected     = selectionIndex != -1

                binding.vUnselected.visibility      =
                    if (isSelected) View.GONE else View.VISIBLE
                binding.tvSelectionOrder.visibility =
                    if (isSelected) View.VISIBLE else View.GONE
                binding.vDimOverlay.visibility      =
                    if (isSelected) View.GONE else View.VISIBLE

                if (isSelected) {
                    binding.tvSelectionOrder.text = (selectionIndex + 1).toString()
                }

                binding.root.setOnClickListener {
                    val idx = selectedUris.indexOf(uri)
                    if (idx != -1) {
                        selectedUris.removeAt(idx)
                    } else {
                        if (selectedUris.size >= MAX_SELECT) return@setOnClickListener
                        selectedUris.add(uri)
                        onImageSelected(selectedUris.first())
                    }
                    onMultiSelected(selectedUris.toList())
                    notifyDataSetChanged()
                }
            } else {
                // single select mode
                binding.vUnselected.visibility      = View.GONE
                binding.tvSelectionOrder.visibility = View.GONE
                binding.vDimOverlay.visibility      = View.GONE
                binding.root.alpha = if (position == selectedPosition) 1f else 0.7f

                binding.root.setOnClickListener {
                    val newPos = bindingAdapterPosition
                    if (newPos == RecyclerView.NO_ID.toInt()) return@setOnClickListener
                    val prev         = selectedPosition
                    selectedPosition = newPos
                    notifyItemChanged(prev)
                    notifyItemChanged(selectedPosition)
                    onImageSelected(uri)
                }
            }
        }
    }

    class CameraViewHolder(view: View) : RecyclerView.ViewHolder(view)
}