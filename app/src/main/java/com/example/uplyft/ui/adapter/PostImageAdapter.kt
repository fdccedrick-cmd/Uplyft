package com.example.uplyft.ui.adapter

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.uplyft.R
// ui/adapter/PostImageAdapter.kt
class PostImageAdapter(
    private val imageUrls: List<String>
) : RecyclerView.Adapter<PostImageAdapter.PostImageVH>() {

    override fun getItemCount() = imageUrls.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostImageVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post_image, parent, false)
        return PostImageVH(view)
    }

    override fun onBindViewHolder(holder: PostImageVH, position: Int) {
        Glide.with(holder.itemView)
            .load(imageUrls[position])
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(holder.itemView.findViewById(R.id.ivPostImage))
    }

    class PostImageVH(view: View) : RecyclerView.ViewHolder(view)
}