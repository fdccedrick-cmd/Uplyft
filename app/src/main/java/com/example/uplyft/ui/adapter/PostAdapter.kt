package com.example.uplyft.ui.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.uplyft.R
import com.example.uplyft.databinding.ItemPostBinding
import com.example.uplyft.domain.model.Post


class PostAdapter(
    private val onLikeClick    : (Post) -> Unit,
    private val onCommentClick : (Post) -> Unit,
    private val onShareClick   : (Post) -> Unit,
    private val onProfileClick : (Post) -> Unit
) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    private val posts = mutableListOf<Post>()

    // DiffUtil — only redraws what actually changed
    fun submitList(newPosts: List<Post>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = posts.size
            override fun getNewListSize() = newPosts.size

            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                posts[oldPos].postId == newPosts[newPos].postId

            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                posts[oldPos] == newPosts[newPos]
        })
        posts.clear()
        posts.addAll(newPosts)
        diff.dispatchUpdatesTo(this)   // only animates changed rows
    }

    // Optimistic like update — instant UI, no waiting for server
    fun updateLike(postId: String, liked: Boolean, newCount: Int) {
        val index = posts.indexOfFirst { it.postId == postId }
        if (index != -1) {
            posts[index] = posts[index].copy(likesCount = newCount)
            notifyItemChanged(index, "like_payload")   // partial update
        }
    }

    override fun getItemCount() = posts.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemPostBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(posts[position])
    }

    // Partial bind — only re-draws like button, not entire row
    override fun onBindViewHolder(
        holder: PostViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains("like_payload")) {
            holder.bindLike(posts[position])
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class PostViewHolder(
        private val binding: ItemPostBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var isLiked = false

        fun bind(post: Post) {
            // Profile image
            Glide.with(binding.root)
                .load(post.userImageUrl)
                .placeholder(R.drawable.ic_profile)
                .circleCrop()
                .into(binding.ivUserAvatar)

            // Post image — with caching
            Glide.with(binding.root)
                .load(post.imageUrl)
                .placeholder(R.color.surface)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)   // cache full + thumbnail
                .into(binding.ivPostImage)

            binding.tvUsername.text   = post.username
            binding.tvLikesCount.text = formatLikes(post.likesCount)
            binding.tvTimestamp.text  = getRelativeTime(post.createdAt)

            // Caption with bold username prefix
            val caption = SpannableString("${post.username} ${post.caption}")
            caption.setSpan(
                StyleSpan(Typeface.BOLD), 0, post.username.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            binding.tvCaption.text = caption

            bindLike(post)

            // Click listeners
            binding.ivLike.setOnClickListener {
                isLiked = !isLiked
                animateLike(binding.ivLike, isLiked)
                onLikeClick(post)
            }

            binding.ivComment.setOnClickListener  { onCommentClick(post) }
            binding.ivShare.setOnClickListener    { onShareClick(post) }
            binding.ivUserAvatar.setOnClickListener { onProfileClick(post) }
            binding.tvUsername.setOnClickListener   { onProfileClick(post) }

            // Double-tap to like
            binding.ivPostImage.setOnClickListener(object : View.OnClickListener {
                private var lastClick = 0L
                override fun onClick(v: View) {
                    val now = System.currentTimeMillis()
                    if (now - lastClick < 300) {
                        if (!isLiked) {
                            isLiked = true
                            animateLike(binding.ivLike, true)
                            onLikeClick(post)
                        }
                    }
                    lastClick = now
                }
            })
        }

        fun bindLike(post: Post) {
            binding.ivLike.setImageResource(
                if (isLiked) R.drawable.ic_heart_filled
                else R.drawable.ic_heart_outline
            )
            binding.ivLike.imageTintList = if (isLiked)
                ColorStateList.valueOf(Color.RED)
            else
                ColorStateList.valueOf(
                    ContextCompat.getColor(binding.root.context,
                        R.color.on_background)
                )
            binding.tvLikesCount.text = formatLikes(post.likesCount)
        }

        // Bounce animation on like
        private fun animateLike(view: View, liked: Boolean) {
            if (liked) {
                view.animate()
                    .scaleX(1.3f).scaleY(1.3f).setDuration(100)
                    .withEndAction {
                        view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    }.start()
            }
        }

        private fun formatLikes(count: Int): String {
            return when {
                count >= 1_000_000 -> "${count / 1_000_000}M likes"
                count >= 1_000     -> "${count / 1_000}K likes"
                count == 1         -> "1 like"
                else               -> "$count likes"
            }
        }

        private fun getRelativeTime(timestamp: Long): String {
            val diff = System.currentTimeMillis() - timestamp
            return when {
                diff < 60_000              -> "Just now"
                diff < 3_600_000           -> "${diff / 60_000}m ago"
                diff < 86_400_000          -> "${diff / 3_600_000}h ago"
                diff < 604_800_000         -> "${diff / 86_400_000}d ago"
                else                       -> "${diff / 604_800_000}w ago"
            }
        }
    }
}