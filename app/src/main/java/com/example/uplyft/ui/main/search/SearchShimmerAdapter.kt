package com.example.uplyft.ui.main.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.uplyft.databinding.ItemSearchShimmerBinding

class SearchShimmerAdapter : RecyclerView.Adapter<SearchShimmerAdapter.ShimmerViewHolder>() {

    private val itemCount = 10

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShimmerViewHolder {
        val binding = ItemSearchShimmerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ShimmerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ShimmerViewHolder, position: Int) {
        // No binding needed for shimmer
    }

    override fun getItemCount(): Int = itemCount

    class ShimmerViewHolder(binding: ItemSearchShimmerBinding) : RecyclerView.ViewHolder(binding.root)
}

