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
import androidx.viewpager2.widget.ViewPager2


class PostAdapter(
    private val onLikeClick    : (Post) -> Unit,
    private val onCommentClick : (Post) -> Unit,
    private val onShareClick   : (Post) -> Unit,
    private val onProfileClick : (Post) -> Unit,
    private val onSaveClick    : (Post) -> Unit,
    private val onRetryClick   : (Post) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val posts = mutableListOf<Post>()
    private var isLoadingMore = false

    companion object {
        private const val VIEW_TYPE_POST = 0
        private const val VIEW_TYPE_LOADING = 1
    }

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

    fun showLoading() {
        if (!isLoadingMore) {
            isLoadingMore = true
            notifyItemInserted(posts.size)
        }
    }

    fun hideLoading() {
        if (isLoadingMore) {
            isLoadingMore = false
            notifyItemRemoved(posts.size)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < posts.size) VIEW_TYPE_POST else VIEW_TYPE_LOADING
    }

    override fun getItemCount(): Int = posts.size + if (isLoadingMore) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_POST -> {
                val binding = ItemPostBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                PostViewHolder(binding)
            }
            VIEW_TYPE_LOADING -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_loading, parent, false)
                LoadingViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is PostViewHolder && position < posts.size) {
            holder.bind(posts[position])
        }
    }

    inner class PostViewHolder(
        private val binding: ItemPostBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        // single clean bind — handles both single and multiple images
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
                binding.tvImageCount.text = binding.root.context.getString(
                    R.string.image_count_format, 1, urls.size
                )
                binding.dotsIndicator.attachTo(binding.vpPostImages)

                binding.vpPostImages.registerOnPageChangeCallback(
                    object : ViewPager2.OnPageChangeCallback() {
                        override fun onPageSelected(pos: Int) {
                            binding.tvImageCount.text = binding.root.context.getString(
                                R.string.image_count_format, pos + 1, urls.size
                            )
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

            // ── Upload status ────────────────────────────────
            bindUploadStatus(post)

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

        fun bindUploadStatus(post: Post) {
            android.util.Log.d("PostAdapter", "bindUploadStatus: postId=${post.postId}, uploadStatus=${post.uploadStatus}")

            when (post.uploadStatus) {
                "pending", "uploading" -> {
                    // Show gray overlay with progress
                    binding.uploadStatusOverlay.visibility = View.VISIBLE
                    binding.uploadStatusOverlay.isClickable = false
                    binding.uploadProgressBar.visibility = View.VISIBLE
                    binding.tvUploadStatus.text = if (post.uploadStatus == "pending") "Pending..." else "Uploading..."
                    binding.btnRetry.visibility = View.GONE

                    // Disable interactions
                    binding.ivLike.isEnabled = false
                    binding.ivComment.isEnabled = false
                    binding.ivShare.isEnabled = false
                    binding.ivSave.isEnabled = false

                    // Add gray tint to entire post
                    binding.root.alpha = 0.6f
                }
                "failed" -> {
                    // Show gray overlay with retry button
                    binding.uploadStatusOverlay.visibility = View.VISIBLE
                    binding.uploadStatusOverlay.isClickable = true
                    binding.uploadStatusOverlay.isFocusable = true
                    binding.uploadProgressBar.visibility = View.GONE
                    binding.tvUploadStatus.text = "Upload failed"
                    binding.btnRetry.visibility = View.VISIBLE

                    android.util.Log.d("PostAdapter", "Setting retry click for postId=${post.postId}")

                    // Setup retry click
                    binding.uploadStatusOverlay.setOnClickListener {
                        android.util.Log.d("PostAdapter", "Retry clicked for postId=${post.postId}")
                        onRetryClick(post)
                    }
                    binding.btnRetry.setOnClickListener {
                        android.util.Log.d("PostAdapter", "Retry button clicked for postId=${post.postId}")
                        onRetryClick(post)
                    }

                    // Disable other interactions
                    binding.ivLike.isEnabled = false
                    binding.ivComment.isEnabled = false
                    binding.ivShare.isEnabled = false
                    binding.ivSave.isEnabled = false

                    binding.root.alpha = 0.6f
                }
                else -> { // "synced" or any other status
                    // Hide overlay, enable interactions
                    binding.uploadStatusOverlay.visibility = View.GONE
                    binding.uploadStatusOverlay.isClickable = false
                    binding.ivLike.isEnabled = true
                    binding.ivComment.isEnabled = true
                    binding.ivShare.isEnabled = true
                    binding.ivSave.isEnabled = true
                    binding.root.alpha = 1.0f
                }
            }
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

    // ─────────────────────────────────────────────
    // LOADING VIEW HOLDER
    // ─────────────────────────────────────────────

    class LoadingViewHolder(view: View) : RecyclerView.ViewHolder(view)
}

