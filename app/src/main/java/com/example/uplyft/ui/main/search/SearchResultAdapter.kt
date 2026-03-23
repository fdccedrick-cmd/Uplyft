package com.example.uplyft.ui.main.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.uplyft.R
import com.example.uplyft.databinding.ItemSearchUserBinding
import com.example.uplyft.domain.model.User

class SearchResultAdapter(
    private val onUserClick: (User) -> Unit
) : ListAdapter<User, SearchResultAdapter.SearchResultViewHolder>(SearchResultDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val binding = ItemSearchUserBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SearchResultViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SearchResultViewHolder(
        private val binding: ItemSearchUserBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onUserClick(getItem(position))
                }
            }
        }

        fun bind(user: User) {
            binding.apply {
                tvUsername.text = user.username
                tvFullName.text = user.fullName

                // Debug logging
                android.util.Log.d("SearchResultAdapter", "Loading image for ${user.username}: profileImageUrl='${user.profileImageUrl}'")

                // Load profile image - same pattern as NotificationAdapter
                Glide.with(itemView.context)
                    .load(user.profileImageUrl.ifEmpty { null })
                    .placeholder(R.drawable.app_logo)
                    .error(R.drawable.app_logo)
                    .circleCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(ivProfileImage)
            }
        }
    }

    class SearchResultDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem == newItem
        }
    }
}

