package com.example.uplyft.ui.adapter

import android.app.AlertDialog
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
        }
    }

    fun submitFullList(list: List<Comment>) {
        allComments = list
        submitList(list.filter { it.parentId.isEmpty() })
    }

    fun updateCurrentUsername(username: String) {
        currentUsername = username
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(p: ViewGroup, v: Int): VH {
        return VH(ItemCommentBinding.inflate(LayoutInflater.from(p.context), p, false))
    }

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))

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

            b.tvReply.setOnClickListener { onReply(c) }
            b.ivLikeComment.setOnClickListener { onLike(c) }

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

        private fun setupReplies(c: Comment) {
            val replies = allComments.filter { it.parentId == c.commentId }

            if (replies.isEmpty()) {
                b.layoutViewReplies.visibility = View.GONE
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
                if (isExpanded) expanded.remove(c.commentId)
                else expanded.add(c.commentId)

                notifyItemChanged(bindingAdapterPosition)
            }

            if (isExpanded) {
                b.layoutReplies.removeAllViews()

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

                    b.layoutReplies.addView(item.root)
                }
            }
        }

    }
}