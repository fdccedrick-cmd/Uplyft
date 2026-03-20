package com.example.uplyft.ui.adapter

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.*
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.*
import androidx.recyclerview.widget.*
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.uplyft.R
import com.example.uplyft.databinding.ItemCommentBinding
import com.example.uplyft.domain.model.Comment

class CommentAdapter(
    private val currentUid: String,
    private var currentUsername: String,
    private val onDelete: (Comment) -> Unit,
    private val onReply: (Comment) -> Unit,
    private val onLike: (Comment) -> Unit,
    private val onAvatar: (Comment) -> Unit,
    private val onUsername: (String) -> Unit
) : ListAdapter<Comment, CommentAdapter.VH>(DIFF) {

    private val expanded = mutableSetOf<String>()
    private var allComments: List<Comment> = emptyList()

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Comment>() {
            override fun areItemsTheSame(a: Comment, b: Comment) =
                a.commentId == b.commentId

            override fun areContentsTheSame(a: Comment, b: Comment) =
                a == b

            // Return payload when only like state changed
            override fun getChangePayload(oldItem: Comment, newItem: Comment): Any? {
                return if (oldItem.isLiked != newItem.isLiked ||
                          oldItem.likesCount != newItem.likesCount ||
                          oldItem.replyCount != newItem.replyCount) {
                    "like_update"
                } else null
            }
        }
    }

    fun submitFullList(list: List<Comment>) {
        val oldAllComments = allComments
        allComments = list

        val topLevel = list.filter { it.parentId.isEmpty() }
        submitList(topLevel)

        // Check if any reply likes changed - notify parent to rebuild
        val oldReplies = oldAllComments.filter { it.parentId.isNotEmpty() }
        val newReplies = list.filter { it.parentId.isNotEmpty() }

        newReplies.forEach { newReply ->
            val oldReply = oldReplies.find { it.commentId == newReply.commentId }
            if (oldReply != null &&
                (oldReply.isLiked != newReply.isLiked || oldReply.likesCount != newReply.likesCount)) {
                // Reply like state changed - find and notify parent
                val parentIndex = topLevel.indexOfFirst { it.commentId == newReply.parentId }
                if (parentIndex != -1) {
                    notifyItemChanged(parentIndex, "like_update")
                }
            }
        }
    }

    fun updateCurrentUsername(username: String) {
        currentUsername = username
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(p: ViewGroup, v: Int): VH {
        return VH(ItemCommentBinding.inflate(LayoutInflater.from(p.context), p, false))
    }

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains("like_update")) {
            val comment = getItem(position)
            holder.bindLike(comment)
            // Also rebuild replies to show updated like states
            holder.rebindReplies(comment)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    private fun getRelativeTime(timestamp: Long): String {
        val now  = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 0             -> "Just now"
            diff < 60_000        -> "Just now"
            diff < 3_600_000     -> "${diff / 60_000}m"
            diff < 86_400_000    -> "${diff / 3_600_000}h"
            diff < 604_800_000   -> "${diff / 86_400_000}d"
            diff < 2_592_000_000 -> "${diff / 604_800_000}w"
            else                 -> {
                // show actual date for old comments
                val sdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
                sdf.format(java.util.Date(timestamp))
            }
        }
    }
    inner class VH(private val b: ItemCommentBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(c: Comment) {

            b.root.alpha = if (c.isPending) 0.5f else 1f

            // ✅ CRITICAL: Clear reply views to prevent recycling issues
            b.layoutReplies.removeAllViews()
            b.layoutReplies.visibility = View.GONE
            b.layoutViewReplies.visibility = View.GONE

            // ✅ clear previous image BEFORE loading new one
            // prevents wrong avatar showing during fast scroll
            Glide.with(b.root)
                .clear(b.ivUserAvatar)

            Glide.with(b.root)
                .load(c.userImage.ifEmpty { null })
                .placeholder(R.drawable.app_logo)
                .error(R.drawable.app_logo)
                .circleCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(b.ivUserAvatar)

            // ✅ username always set fresh
            b.tvUsername.text = c.username.ifEmpty { "user" }

            // ✅ timestamp beside username
            b.tvTime.text = if (c.isPending) "Sending..."
            else getRelativeTime(c.createdAt)
            b.tvTime.visibility = View.VISIBLE

            bindText(c)
            bindLike(c)

            b.tvReply.setOnClickListener { onReply(c) }
            b.ivLikeComment.setOnClickListener {
                animateLike(b.ivLikeComment, !c.isLiked)
                onLike(c)
            }

            // ✅ make avatar and username clickable
            b.ivUserAvatar.setOnClickListener { onAvatar(c) }
            b.tvUsername.setOnClickListener { onAvatar(c) }

            // ✅ long press delete
            if (c.userId == currentUid && !c.isPending) {
                b.root.setOnLongClickListener {
                    AlertDialog.Builder(b.root.context)
                        .setMessage("Delete this comment?")
                        .setPositiveButton("Delete") { _, _ -> onDelete(c) }
                        .setNegativeButton("Cancel", null)
                        .show()
                    true
                }
            } else {
                b.root.setOnLongClickListener(null)
            }

            setupReplies(c)
        }

        fun bindLike(c: Comment) {
            // Set heart icon based on like state
            b.ivLikeComment.setImageResource(
                if (c.isLiked) R.drawable.ic_heart_filled
                else R.drawable.ic_heart_outline
            )

            // Set heart color
            b.ivLikeComment.imageTintList = if (c.isLiked)
                ColorStateList.valueOf(Color.RED)
            else
                ColorStateList.valueOf(Color.parseColor("#999999"))

            // Show like count only if > 0
            if (c.likesCount > 0) {
                b.tvCommentLikeCount.text = c.likesCount.toString()
                b.tvCommentLikeCount.visibility = View.VISIBLE
            } else {
                b.tvCommentLikeCount.visibility = View.GONE
            }
        }

        private fun animateLike(view: View, liked: Boolean) {
            if (liked) {
                // Instagram-style bounce animation
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

        private fun bindText(c: Comment) {
            if (c.isGif) {
                b.ivGif.visibility        = View.VISIBLE
                b.tvCommentText.visibility = View.GONE
                Glide.with(b.root).asGif().load(c.gifUrl).into(b.ivGif)
            } else {
                b.ivGif.visibility        = View.GONE
                b.tvCommentText.visibility = View.VISIBLE

                val span = SpannableStringBuilder(c.text)

                Regex("@(\\w+)").findAll(c.text).forEach { match ->
                    val mentionedUser = match.groupValues[1]
                    val start         = match.range.first
                    val end           = match.range.last + 1

                    when {
                        // ✅ current user's mention — gray, not clickable
                        mentionedUser.equals(currentUsername, ignoreCase = true) -> {
                            span.setSpan(
                                ForegroundColorSpan(Color.GRAY),
                                start, end,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                        // ✅ other user's mention — blue, clickable → navigate to profile
                        else -> {
                            span.setSpan(object : ClickableSpan() {
                                override fun onClick(widget: View) {
                                    onUsername(mentionedUser)
                                }
                                override fun updateDrawState(ds: TextPaint) {
                                    ds.color          = Color.parseColor("#0095F6")
                                    ds.isUnderlineText = false
                                }
                            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                }

                b.tvCommentText.text            = span
                b.tvCommentText.movementMethod  = LinkMovementMethod.getInstance()
            }
        }

        fun rebindReplies(c: Comment) {
            // Only rebuild if currently expanded
            if (expanded.contains(c.commentId) && b.layoutReplies.visibility == View.VISIBLE) {
                val replies = allComments.filter { it.parentId == c.commentId }
                if (replies.isNotEmpty()) {
                    b.layoutReplies.removeAllViews()
                    buildReplyViews(replies)
                }
            }
        }

        private fun setupReplies(c: Comment) {
            val replies = allComments.filter { it.parentId == c.commentId }

            if (replies.isEmpty()) {
                b.layoutViewReplies.visibility = View.GONE
                b.layoutReplies.visibility = View.GONE
                return
            }

            val isExpanded = expanded.contains(c.commentId)

            b.layoutViewReplies.visibility = View.VISIBLE
            b.tvViewReplies.text =
                if (isExpanded) "Hide replies"
                else "View replies (${replies.size})"

            b.layoutReplies.visibility =
                if (isExpanded) View.VISIBLE else View.GONE

            b.layoutViewReplies.setOnClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener

                if (isExpanded) {
                    expanded.remove(c.commentId)
                    // Clear views when collapsing
                    b.layoutReplies.removeAllViews()
                } else {
                    expanded.add(c.commentId)
                }

                // Just toggle visibility without notifying
                val nowExpanded = expanded.contains(c.commentId)
                b.tvViewReplies.text = if (nowExpanded) "Hide replies" else "View replies (${replies.size})"
                b.layoutReplies.visibility = if (nowExpanded) View.VISIBLE else View.GONE

                // Build views when expanding
                if (nowExpanded) {
                    b.layoutReplies.removeAllViews()  // Clear first
                    buildReplyViews(replies)
                }
            }

            // ALWAYS rebuild reply views if expanded to show latest like states
            if (isExpanded) {
                b.layoutReplies.removeAllViews()
                buildReplyViews(replies)
            }
        }

        private fun buildReplyViews(replies: List<Comment>) {
            replies.forEach { r ->
                val item = ItemCommentBinding.inflate(
                    LayoutInflater.from(b.root.context),
                    b.layoutReplies,
                    false
                )

                // ✅ clear + load avatar for each reply
                Glide.with(item.root)
                    .clear(item.ivUserAvatar)

                Glide.with(item.root)
                    .load(r.userImage.ifEmpty { null })
                    .placeholder(R.drawable.app_logo)
                    .error(R.drawable.app_logo)
                    .circleCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(item.ivUserAvatar)

                item.tvUsername.text = r.username.ifEmpty { "user" }
                item.tvTime.text     = getRelativeTime(r.createdAt)
                item.tvCommentText.text = r.text

                // Bind like state for reply
                item.ivLikeComment.setImageResource(
                    if (r.isLiked) R.drawable.ic_heart_filled
                    else R.drawable.ic_heart_outline
                )
                item.ivLikeComment.imageTintList = if (r.isLiked)
                    ColorStateList.valueOf(Color.RED)
                else
                    ColorStateList.valueOf(Color.parseColor("#999999"))

                if (r.likesCount > 0) {
                    item.tvCommentLikeCount.text = r.likesCount.toString()
                    item.tvCommentLikeCount.visibility = View.VISIBLE
                } else {
                    item.tvCommentLikeCount.visibility = View.GONE
                }

                // Add like click listener with animation
                item.ivLikeComment.setOnClickListener {
                    animateLike(item.ivLikeComment, !r.isLiked)
                    onLike(r)
                }

                // ✅ make reply avatar and username clickable
                item.ivUserAvatar.setOnClickListener { onAvatar(r) }
                item.tvUsername.setOnClickListener { onAvatar(r) }

                b.layoutReplies.addView(item.root)
            }
        }

    }
}