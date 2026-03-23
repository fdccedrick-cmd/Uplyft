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
    private val onLike: (Comment) -> Unit,
    private val onAvatar: (Comment) -> Unit,
    private val onUsername: (String) -> Unit
) : ListAdapter<Comment, CommentAdapter.VH>(DIFF) {

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
                          oldItem.likesCount != newItem.likesCount) {
                    "like_update"
                } else null
            }
        }
    }

    fun submitFullList(list: List<Comment>) {
        allComments = list
        submitList(list)
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

            // ...existing code...
            Glide.with(b.root)
                .clear(b.ivUserAvatar)

            Glide.with(b.root)
                .load(c.userImage.ifEmpty { null })
                .placeholder(R.drawable.app_logo)
                .error(R.drawable.app_logo)
                .circleCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(b.ivUserAvatar)

            b.tvUsername.text = c.username.ifEmpty { "user" }
            b.tvTime.text = if (c.isPending) "Sending..."
            else getRelativeTime(c.createdAt)
            b.tvTime.visibility = View.VISIBLE

            bindText(c)
            bindLike(c)

            b.ivLikeComment.setOnClickListener {
                animateLike(b.ivLikeComment, !c.isLiked)
                onLike(c)
            }

            // ...existing code...
            b.ivUserAvatar.setOnClickListener { onAvatar(c) }
            b.tvUsername.setOnClickListener { onAvatar(c) }

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


    }
}