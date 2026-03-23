package com.example.uplyft.ui.main.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.uplyft.R
import com.example.uplyft.databinding.ItemSearchHistoryBinding
import com.example.uplyft.data.local.entity.SearchHistoryEntity

class SearchHistoryAdapter(
    private val onHistoryClick: (SearchHistoryEntity) -> Unit,
    private val onRemoveClick: (SearchHistoryEntity) -> Unit
) : ListAdapter<SearchHistoryEntity, SearchHistoryAdapter.SearchHistoryViewHolder>(SearchHistoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchHistoryViewHolder {
        val binding = ItemSearchHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SearchHistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchHistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SearchHistoryViewHolder(
        private val binding: ItemSearchHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onHistoryClick(getItem(position))
                }
            }

            binding.btnRemove.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onRemoveClick(getItem(position))
                }
            }
        }

        fun bind(history: SearchHistoryEntity) {
            binding.apply {
                tvUsername.text = history.username
                tvFullName.text = history.fullName

                // Load profile image - same pattern as NotificationAdapter
                Glide.with(itemView.context)
                    .load(history.profileImageUrl.ifEmpty { null })
                    .placeholder(R.drawable.app_logo)
                    .error(R.drawable.app_logo)
                    .circleCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(ivProfileImage)
            }
        }
    }

    class SearchHistoryDiffCallback : DiffUtil.ItemCallback<SearchHistoryEntity>() {
        override fun areItemsTheSame(oldItem: SearchHistoryEntity, newItem: SearchHistoryEntity): Boolean {
            return oldItem.userId == newItem.userId
        }

        override fun areContentsTheSame(oldItem: SearchHistoryEntity, newItem: SearchHistoryEntity): Boolean {
            return oldItem == newItem
        }
    }
}

