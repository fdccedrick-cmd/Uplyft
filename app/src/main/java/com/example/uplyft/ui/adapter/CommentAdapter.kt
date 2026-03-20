package com.example.uplyft.ui.adapter

import android.graphics.Color
import android.text.*
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.*
import androidx.recyclerview.widget.*
import com.bumptech.glide.Glide
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

    inner class VH(private val b: ItemCommentBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(c: Comment) {

            b.root.alpha = if (c.isPending) 0.5f else 1f

            Glide.with(b.root)
                .load(c.userImage)
                .placeholder(R.drawable.app_logo)
                .circleCrop()
                .into(b.ivUserAvatar)

            b.tvUsername.text = c.username

            bindText(c)

            b.tvReply.setOnClickListener { onReply(c) }
            b.ivLikeComment.setOnClickListener { onLike(c) }

            setupReplies(c)
        }

        private fun bindText(c: Comment) {
            if (c.isGif) {
                b.ivGif.visibility = View.VISIBLE
                b.tvCommentText.visibility = View.GONE
                Glide.with(b.root).asGif().load(c.gifUrl).into(b.ivGif)
            } else {
                b.ivGif.visibility = View.GONE
                b.tvCommentText.visibility = View.VISIBLE

                val span = SpannableString(c.text)
                makeClickable(span)

                b.tvCommentText.text = span
                b.tvCommentText.movementMethod = LinkMovementMethod.getInstance()
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

                    item.tvUsername.text = r.username
                    item.tvCommentText.text = r.text

                    b.layoutReplies.addView(item.root)
                }
            }
        }

        private fun makeClickable(span: SpannableString) {
            Regex("@(\\w+)").findAll(span).forEach {
                val user = it.groupValues[1]

                if (user.equals(currentUsername, true)) return@forEach

                span.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        onUsername(user)
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        ds.color = Color.parseColor("#0095F6")
                        ds.isUnderlineText = false
                    }
                }, it.range.first, it.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                span.setSpan(
                    ForegroundColorSpan(Color.parseColor("#0095F6")),
                    it.range.first,
                    it.range.last + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }
}