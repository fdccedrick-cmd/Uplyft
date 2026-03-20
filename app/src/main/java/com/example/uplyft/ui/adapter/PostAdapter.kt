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


        fun bind(post: Post) {

            binding.tvUsername.text= post.username.ifEmpty { "User" }
            // Profile image
            Glide.with(binding.root)
                .load(post.userImageUrl.ifEmpty { null })   // null triggers placeholder
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .circleCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(binding.ivUserAvatar)

            // Post image — with caching
            Glide.with(binding.root)
                .load(post.imageUrl)
                .placeholder(R.color.surface)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)   // cache full + thumbnail
                .into(binding.ivPostImage)

            binding.tvUsername.text   = post.username
            binding.tvTimestamp.text  = getRelativeTime(post.createdAt)

            // Show like count only if > 0
            if (post.likesCount > 0) {
                binding.tvLikesCount.text = post.likesCount.toString()
                binding.tvLikesCount.visibility = View.VISIBLE
            } else {
                binding.tvLikesCount.visibility = View.GONE
            }

            // Show comment count only if > 0
            if (post.commentsCount > 0) {
                binding.tvCommentsCount.text = post.commentsCount.toString()
                binding.tvCommentsCount.visibility = View.VISIBLE
            } else {
                binding.tvCommentsCount.visibility = View.GONE
            }

            if (post.caption.isNotEmpty()) {
                val displayName = post.username.ifEmpty { "User" }
                val full = SpannableString("$displayName ${post.caption}")
                full.setSpan(
                    StyleSpan(Typeface.BOLD),
                    0, displayName.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                binding.tvCaption.text       = full
                binding.tvCaption.visibility = View.VISIBLE
            } else {
                binding.tvCaption.visibility = View.GONE
            }

            bindLike(post)

            // Clicks
            binding.ivLike.setOnClickListener {
                animateLike(binding.ivLike, !post.isLiked)
                onLikeClick(post)
            }

            binding.ivComment.setOnClickListener    { onCommentClick(post) }
            binding.ivShare.setOnClickListener      { onShareClick(post) }
            binding.ivUserAvatar.setOnClickListener { onProfileClick(post) }
            binding.tvUsername.setOnClickListener   { onProfileClick(post) }

            // Double tap to like with heart pop-up animation (Instagram style)
            binding.ivPostImage.setOnClickListener(object : View.OnClickListener {
                private var lastClick = 0L
                override fun onClick(v: View) {
                    val now = System.currentTimeMillis()
                    if (now - lastClick < 300) {
                        // Double-tap detected!
                        // Always show heart pop-up animation (Instagram style)
                        showHeartPopAnimation()

                        // Only like if not already liked
                        if (!post.isLiked) {
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
                if (post.isLiked) R.drawable.ic_heart_filled
                else R.drawable.ic_heart_outline
            )
            binding.ivLike.imageTintList = if (post.isLiked)
                ColorStateList.valueOf(Color.RED)
            else
                ColorStateList.valueOf(
                    ContextCompat.getColor(binding.root.context,
                        R.color.on_background)
                )

            // Update like count - show only if > 0
            if (post.likesCount > 0) {
                binding.tvLikesCount.text = post.likesCount.toString()
                binding.tvLikesCount.visibility = View.VISIBLE
            } else {
                binding.tvLikesCount.visibility = View.GONE
            }
        }

        // Smooth Instagram-style like animation
        private fun animateLike(view: View, liked: Boolean) {
            if (liked) {
                // Scale up then down with overshoot
                view.animate()
                    .scaleX(1.3f).scaleY(1.3f)
                    .setDuration(150)
                    .withEndAction {
                        view.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(150)
                            .start()
                    }.start()
            }
        }

        // Instagram-style heart pop-up animation over the image
        private fun showHeartPopAnimation() {
            binding.ivHeartOverlay.apply {
                visibility = View.VISIBLE
                alpha = 0f
                scaleX = 0f
                scaleY = 0f

                // Phase 1: Pop in with overshoot (0 -> 1.2)
                animate()
                    .alpha(1f)
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .setDuration(250)
                    .setInterpolator(android.view.animation.OvershootInterpolator(2f))
                    .withEndAction {
                        // Phase 2: Settle to normal size (1.2 -> 1.0)
                        animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(150)
                            .setInterpolator(android.view.animation.DecelerateInterpolator())
                            .withEndAction {
                                // Phase 3: Hold briefly then fade out
                                animate()
                                    .alpha(0f)
                                    .scaleX(1.2f)
                                    .scaleY(1.2f)
                                    .setDuration(250)
                                    .setStartDelay(100)
                                    .setInterpolator(android.view.animation.AccelerateInterpolator())
                                    .withEndAction {
                                        visibility = View.GONE
                                    }
                                    .start()
                            }
                            .start()
                    }
                    .start()
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