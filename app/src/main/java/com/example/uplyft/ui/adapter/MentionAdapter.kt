package com.example.uplyft.ui.adapter

import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.uplyft.R
import com.example.uplyft.domain.model.MentionUser

// ui/adapter/MentionAdapter.kt
class MentionAdapter(
    private val onMentionClick: (MentionUser) -> Unit
) : RecyclerView.Adapter<MentionAdapter.MentionVH>() {

    private val users = mutableListOf<MentionUser>()

    fun submitList(list: List<MentionUser>) {
        users.clear()
        users.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemCount() = users.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MentionVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_mention_suggestion, parent, false)
        return MentionVH(view)
    }

    override fun onBindViewHolder(holder: MentionVH, position: Int) {
        holder.bind(users[position])
    }

    inner class MentionVH(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(user: MentionUser) {
            Glide.with(itemView)
                .load(user.profileImage.ifEmpty { null })
                .placeholder(R.drawable.app_logo)
                .circleCrop()
                .into(itemView.findViewById(R.id.ivMentionAvatar))

            itemView.findViewById<TextView>(R.id.tvMentionUsername).text =
                user.username
            itemView.findViewById<TextView>(R.id.tvMentionFullName).text =
                user.fullName

            val tvMutual = itemView.findViewById<TextView>(R.id.tvMutual)
            tvMutual.visibility = if (user.isMutual) View.VISIBLE else View.GONE

            itemView.setOnClickListener { onMentionClick(user) }
        }
    }
}