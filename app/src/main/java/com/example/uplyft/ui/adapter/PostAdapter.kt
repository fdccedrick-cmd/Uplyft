package com.example.uplyft.ui.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
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
import androidx.viewpager2.widget.ViewPager2


class PostAdapter(
    private val onLikeClick    : (Post) -> Unit,
    private val onCommentClick : (Post) -> Unit,
    private val onShareClick   : (Post) -> Unit,
    private val onProfileClick : (Post) -> Unit,
    private val onSaveClick    : (Post) -> Unit
) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

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

    fun updateLike(postId: String, liked: Boolean, newCount: Int) {
        val index = posts.indexOfFirst { it.postId == postId }
        if (index != -1) {
            posts[index] = posts[index].copy(likesCount = newCount)
            notifyItemChanged(index, "like_payload")
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

        // ✅ single clean bind — handles both single and multiple images
        fun bind(post: Post) {

            // ── Avatar ──────────────────────────────────────
            Glide.with(binding.root)
                .load(post.userImageUrl.ifEmpty { null })
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .circleCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(binding.ivUserAvatar)

            // ── Username + Timestamp ─────────────────────────
            binding.tvUsername.text  = post.username.ifEmpty { "User" }
            binding.tvTimestamp.text = getRelativeTime(post.createdAt)

            // ── Images — carousel or single ──────────────────
            val urls = post.imageUrls.ifEmpty { listOf(post.imageUrl) }
                .filter { it.isNotBlank() }

            val imageAdapter = PostImageAdapter(urls)
            binding.vpPostImages.adapter = imageAdapter

            if (urls.size > 1) {
                binding.dotsIndicator.visibility = View.VISIBLE
                binding.tvImageCount.visibility  = View.VISIBLE
                binding.tvImageCount.text        = "1/${urls.size}"
                binding.dotsIndicator.attachTo(binding.vpPostImages)

                binding.vpPostImages.registerOnPageChangeCallback(
                    object : ViewPager2.OnPageChangeCallback() {
                        override fun onPageSelected(pos: Int) {
                            binding.tvImageCount.text = "${pos + 1}/${urls.size}"
                        }
                    }
                )
            } else {
                binding.dotsIndicator.visibility = View.GONE
                binding.tvImageCount.visibility  = View.GONE
            }

            // ── Caption ──────────────────────────────────────
            if (post.caption.isNotEmpty()) {
                val displayName = post.username.ifEmpty { "User" }
                val full = SpannableString("$displayName ${post.caption}")
                full.setSpan(
                    StyleSpan(Typeface.BOLD), 0, displayName.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                binding.tvCaption.text       = full
                binding.tvCaption.visibility = View.VISIBLE
            } else {
                binding.tvCaption.visibility = View.GONE
            }

            // ── Likes count ──────────────────────────────────
            binding.tvLikesCount.text = if (post.likesCount > 0)
                formatLikes(post.likesCount)
            else
                ""
            binding.tvLikesCount.visibility =
                if (post.likesCount > 0) View.VISIBLE else View.GONE

            // ── Comments count ───────────────────────────────
            if (post.commentsCount > 0) {
                binding.tvCommentsCount.text = post.commentsCount.toString()
                binding.tvCommentsCount.visibility = View.VISIBLE
            } else {
                binding.tvCommentsCount.text = ""
                binding.tvCommentsCount.visibility = View.GONE
            }

            // ── Like state ───────────────────────────────────
            bindLike(post)

            // ── Save state ───────────────────────────────────
            bindSave(post)

            // ── Click listeners ──────────────────────────────
            binding.ivLike.setOnClickListener {
                animateLike(binding.ivLike)
                onLikeClick(post)
            }
            binding.tvCommentsCount.setOnClickListener { onCommentClick(post) }
            binding.ivComment.setOnClickListener       { onCommentClick(post) }
            binding.ivShare.setOnClickListener         { onShareClick(post) }
            binding.ivSave.setOnClickListener {
                animateSave(binding.ivSave)
                onSaveClick(post)
            }
            binding.ivUserAvatar.setOnClickListener    { onProfileClick(post) }
            binding.tvUsername.setOnClickListener      { onProfileClick(post) }

            // ── Double tap to like ───────────────────────────
            setupDoubleTapLike(post)
        }

        private fun setupDoubleTapLike(post: Post) {
            val gestureDetector = GestureDetector(
                binding.root.context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        showHeartPopAnimation()
                        if (!post.isLiked) {
                            animateLike(binding.ivLike)
                            onLikeClick(post)
                        }
                        return true
                    }

                    override fun onDown(e: MotionEvent): Boolean {
                        // Must return true to indicate we're interested in the gesture
                        return true
                    }
                }
            )

            // Use the transparent overlay instead of ViewPager2
            binding.doubleTapOverlay.setOnTouchListener { view, event ->
                val gestureHandled = gestureDetector.onTouchEvent(event)

                // If not a double tap, pass the event to ViewPager2 for swipe handling
                if (!gestureHandled && event.action == MotionEvent.ACTION_DOWN) {
                    // Let ViewPager2 handle the touch for swipe gestures
                    binding.vpPostImages.dispatchTouchEvent(event)
                    false
                } else if (gestureHandled) {
                    // Double tap detected, consume the event
                    true
                } else {
                    // Pass all other events to ViewPager2
                    binding.vpPostImages.dispatchTouchEvent(event)
                    false
                }
            }
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
                    ContextCompat.getColor(binding.root.context, R.color.on_background)
                )
        }

        fun bindSave(post: Post) {
            binding.ivSave.setImageResource(
                if (post.isSaved) R.drawable.ic_bookmark_filled
                else R.drawable.ic_bookmark_outline
            )
            binding.ivSave.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(binding.root.context, R.color.on_background)
            )
        }

        private fun animateLike(view: View) {
            view.animate()
                .scaleX(1.3f).scaleY(1.3f).setDuration(150)
                .withEndAction {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                }.start()
        }

        private fun animateSave(view: View) {
            view.animate()
                .scaleX(1.3f).scaleY(1.3f).setDuration(150)
                .withEndAction {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                }.start()
        }

        private fun showHeartPopAnimation() {
            binding.ivHeartOverlay.apply {
                visibility = View.VISIBLE
                alpha      = 0f
                scaleX     = 0f
                scaleY     = 0f
                animate()
                    .alpha(1f).scaleX(1.2f).scaleY(1.2f)
                    .setDuration(250)
                    .setInterpolator(android.view.animation.OvershootInterpolator(2f))
                    .withEndAction {
                        animate()
                            .scaleX(1f).scaleY(1f).setDuration(150)
                            .setInterpolator(android.view.animation.DecelerateInterpolator())
                            .withEndAction {
                                animate()
                                    .alpha(0f).scaleX(1.2f).scaleY(1.2f)
                                    .setDuration(250).setStartDelay(100)
                                    .setInterpolator(android.view.animation.AccelerateInterpolator())
                                    .withEndAction { visibility = View.GONE }
                                    .start()
                            }.start()
                    }.start()
            }
        }

        private fun formatLikes(count: Int): String = when {
            count >= 1_000_000 -> "${count / 1_000_000}M"
            count >= 1_000     -> "${count / 1_000}K"
            else               -> "$count"
        }

        private fun getRelativeTime(timestamp: Long): String {
            val diff = System.currentTimeMillis() - timestamp
            return when {
                diff < 60_000      -> "Just now"
                diff < 3_600_000   -> "${diff / 60_000}m ago"
                diff < 86_400_000  -> "${diff / 3_600_000}h ago"
                diff < 604_800_000 -> "${diff / 86_400_000}d ago"
                else               -> "${diff / 604_800_000}w ago"
            }
        }
    }
}