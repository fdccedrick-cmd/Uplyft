package com.example.uplyft.ui.adapter

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.uplyft.R
import com.example.uplyft.domain.model.Notification
import com.example.uplyft.utils.NotificationTypes
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration


// ui/adapter/NotificationAdapter.kt
class NotificationAdapter(
    private val currentUid    : String,
    private val onItemClick   : (Notification) -> Unit,
    private val onFollowClick : (Notification) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotifVH>() {

    private val items = mutableListOf<Notification>()
    private val followListeners = mutableMapOf<String, ListenerRegistration>()

    fun submitList(list: List<Notification>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotifVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotifVH(view)
    }

    override fun onBindViewHolder(holder: NotifVH, position: Int) {
        holder.bind(items[position])
    }

    inner class NotifVH(view: View) : RecyclerView.ViewHolder(view) {

        private var followListener: ListenerRegistration? = null

        fun bind(notif: Notification) {
            followListener?.remove()
            followListener = null

            // unread dot
            itemView.findViewById<View>(R.id.vUnread).visibility =
                if (!notif.isRead) View.VISIBLE else View.GONE

            // background tint for unread
            itemView.setBackgroundColor(
                if (!notif.isRead)
                    ContextCompat.getColor(itemView.context, R.color.surface)
                else
                    Color.TRANSPARENT
            )

            // avatar
            val ivAvatar = itemView.findViewById<ShapeableImageView>(R.id.ivAvatar)
            Glide.with(itemView)
                .load(notif.fromImage.ifEmpty { null })
                .placeholder(R.drawable.app_logo)
                .circleCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(ivAvatar)

            // message — bold username
            val tvMessage = itemView.findViewById<TextView>(R.id.tvMessage)
            val full      = SpannableString(notif.message)
            val endIdx    = notif.fromUsername.length + 1 // +1 for @
            if (notif.message.startsWith("@${notif.fromUsername}")) {
                full.setSpan(
                    StyleSpan(Typeface.BOLD),
                    0, endIdx.coerceAtMost(full.length),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            tvMessage.text = full

            // time
            itemView.findViewById<TextView>(R.id.tvTime).text =
                getRelativeTime(notif.createdAt)

            // post thumbnail — show for like/comment notifs
            val ivThumb  = itemView.findViewById<ImageView>(R.id.ivPostThumb)
            val tvFollow = itemView.findViewById<TextView>(R.id.tvFollowBack)

            when (notif.type) {
                NotificationTypes.LIKE_POST,
                NotificationTypes.COMMENT,
                NotificationTypes.LIKE_COMMENT -> {
                    ivThumb.visibility  = View.VISIBLE
                    tvFollow.visibility = View.GONE
                    // load post thumbnail if postId exists
                    if (notif.postId.isNotEmpty()) {
                        FirebaseFirestore.getInstance()
                            .collection("posts")
                            .document(notif.postId)
                            .get()
                            .addOnSuccessListener { doc ->
                                val imageUrl = doc.getString("imageUrl") ?: ""
                                Glide.with(itemView)
                                    .load(imageUrl.ifEmpty { null })
                                    .centerCrop()
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .into(ivThumb)
                            }
                    }
                }
                NotificationTypes.FOLLOW,
                NotificationTypes.FOLLOW_BACK -> {
                    ivThumb.visibility  = View.GONE
                    tvFollow.visibility = View.VISIBLE

                    // Check follow status and update button in real-time
                    followListener = checkFollowStatus(notif.fromUserId, tvFollow)

                    tvFollow.setOnClickListener { onFollowClick(notif) }
                }
                else -> {
                    ivThumb.visibility  = View.GONE
                    tvFollow.visibility = View.GONE
                }
            }

            itemView.setOnClickListener { onItemClick(notif) }
        }

        private fun getRelativeTime(timestamp: Long): String {
            val diff = System.currentTimeMillis() - timestamp
            return when {
                diff < 60_000      -> "Just now"
                diff < 3_600_000   -> "${diff / 60_000}m"
                diff < 86_400_000  -> "${diff / 3_600_000}h"
                diff < 604_800_000 -> "${diff / 86_400_000}d"
                else               -> "${diff / 604_800_000}w"
            }
        }
    }

    private fun checkFollowStatus(targetUserId: String, button: TextView): ListenerRegistration {
        val followId = "${currentUid}_${targetUserId}"

        return FirebaseFirestore.getInstance()
            .collection("follows")
            .document(followId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                val isFollowing = snapshot?.exists() == true
                button.text = if (isFollowing) "Following" else "Follow"
                button.setBackgroundResource(
                    if (isFollowing) R.drawable.bg_button_secondary
                    else R.drawable.bg_button_primary
                )
                button.setTextColor(
                    if (isFollowing) ContextCompat.getColor(button.context, R.color.on_background)
                    else ContextCompat.getColor(button.context, android.R.color.white)
                )
                button.isEnabled = true
            }
    }

    fun cleanup() {
        followListeners.values.forEach { it.remove() }
        followListeners.clear()
    }
}