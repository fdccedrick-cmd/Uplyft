package com.example.uplyft.ui.adapter

import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.uplyft.R
import com.example.uplyft.databinding.ItemProfilePostBinding
import com.example.uplyft.domain.model.Post
class ProfilePostAdapter(
    private val onPostClick: (Post) -> Unit,
) : RecyclerView.Adapter<ProfilePostAdapter.ProfilePostViewHolder>() {

    private val posts = mutableListOf<Post>()

    fun submitList(newPosts: List<Post>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = posts.size
            override fun getNewListSize() = newPosts.size
            override fun areItemsTheSame(o: Int, n: Int) =
                posts[o].postId == newPosts[n].postId
            override fun areContentsTheSame(o: Int, n: Int) =
                posts[o] == newPosts[n]
        })
        posts.clear()
        posts.addAll(newPosts)
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemCount() = posts.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfilePostViewHolder {
        val binding = ItemProfilePostBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ProfilePostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProfilePostViewHolder, position: Int) {
        holder.bind(posts[position])
    }

    inner class ProfilePostViewHolder(
        private val binding: ItemProfilePostBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(post: Post) {
            Glide.with(binding.root)
                .load(post.imageUrl)
                .placeholder(ColorDrawable(
                    ContextCompat.getColor(binding.root.context, R.color.surface)
                ))
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .dontAnimate()
                .into(binding.ivProfilePost)

            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_ID.toInt()) return@setOnClickListener
                onPostClick(post)
            }
        }
    }
}