package com.example.uplyft.ui.main.search

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import com.example.uplyft.R
import com.example.uplyft.databinding.FragmentSearchBinding
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.fragment.app.viewModels
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.uplyft.viewmodel.SearchViewModel
import com.example.uplyft.data.local.AppDatabase
import com.example.uplyft.data.remote.firebase.UserFirebaseSource
import com.example.uplyft.data.repository.SearchRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.uplyft.domain.model.User

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = AppDatabase.getInstance(requireContext())
                val repository = SearchRepository(
                    UserFirebaseSource(),
                    com.example.uplyft.data.remote.firebase.AuthFirebaseSource(),
                    database.searchHistoryDao()
                )
                @Suppress("UNCHECKED_CAST")
                return SearchViewModel(repository) as T
            }
        }
    }

    private lateinit var searchResultAdapter: SearchResultAdapter
    private lateinit var searchHistoryAdapter: SearchHistoryAdapter
    private lateinit var shimmerAdapter: SearchShimmerAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        setupSearchBar()
        observeViewModel()
    }

    private fun setupRecyclerViews() {
        // Search Results Adapter
        searchResultAdapter = SearchResultAdapter { user ->
            viewModel.onUserClicked(user)
            navigateToUserProfile(user.uid)
        }

        binding.rvSearchResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchResultAdapter
        }

        // Search History Adapter
        searchHistoryAdapter = SearchHistoryAdapter(
            onHistoryClick = { history ->
                navigateToUserProfile(history.userId)
            },
            onRemoveClick = { history ->
                viewModel.onRemoveFromHistory(history.userId)
            }
        )

        binding.rvSearchHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchHistoryAdapter
        }

        // Shimmer Adapter
        shimmerAdapter = SearchShimmerAdapter()
        binding.rvShimmer.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = shimmerAdapter
        }
    }

    private fun setupSearchBar() {
        binding.etSearch.addTextChangedListener { text ->
            val query = text.toString()
            Log.d("SearchFragment", "Text changed: '$query'")
            viewModel.onSearchQueryChanged(query)

            // Show/hide clear button
            binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text.clear()
            viewModel.clearSearch()
        }

        binding.btnClearAll.setOnClickListener {
            viewModel.onClearAllHistory()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Observe search query
                launch {
                    viewModel.searchQuery.collect { query ->
                        if (query.isEmpty()) {
                            showSearchHistory()
                        } else {
                            showSearchResults()
                        }
                    }
                }

                // Observe loading state
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        if (isLoading) {
                            showShimmer()
                        } else {
                            hideShimmer()
                        }
                    }
                }

                // Observe search results
                launch {
                    viewModel.searchResults.collect { results ->
                        Log.d("SearchFragment", "Results received: ${results.size} items")
                        searchResultAdapter.submitList(results)

                        if (viewModel.searchQuery.value.isNotEmpty() && !viewModel.isLoading.value) {
                            binding.tvNoResults.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
                            binding.rvSearchResults.visibility = if (results.isEmpty()) View.GONE else View.VISIBLE
                        }
                    }
                }

                // Observe search history
                launch {
                    viewModel.searchHistory.collect { history ->
                        searchHistoryAdapter.submitList(history)

                        // Show/hide empty state and header
                        val isEmpty = history.isEmpty()
                        binding.tvEmptyHistory.visibility = if (isEmpty) View.VISIBLE else View.GONE
                        binding.rvSearchHistory.visibility = if (isEmpty) View.GONE else View.VISIBLE
                        binding.layoutHistoryHeader.visibility = if (isEmpty) View.GONE else View.VISIBLE
                    }
                }
            }
        }
    }

    private fun showSearchHistory() {
        binding.layoutSearchHistory.visibility = View.VISIBLE
        binding.layoutSearchResults.visibility = View.GONE
    }

    private fun showSearchResults() {
        binding.layoutSearchHistory.visibility = View.GONE
        binding.layoutSearchResults.visibility = View.VISIBLE
        binding.tvNoResults.visibility = View.GONE
    }

    private fun showShimmer() {
        binding.layoutShimmer.visibility = View.VISIBLE
        binding.shimmerLayout.startShimmer()
        binding.rvSearchResults.visibility = View.GONE
        binding.tvNoResults.visibility = View.GONE
    }

    private fun hideShimmer() {
        binding.layoutShimmer.visibility = View.GONE
        binding.shimmerLayout.stopShimmer()

        // Show results or no results message
        val hasResults = searchResultAdapter.currentList.isNotEmpty()
        binding.rvSearchResults.visibility = if (hasResults) View.VISIBLE else View.GONE
        binding.tvNoResults.visibility = if (hasResults) View.GONE else View.VISIBLE
    }

    private fun navigateToUserProfile(userId: String) {
        val bundle = Bundle().apply {
            putString("userId", userId)
        }
        findNavController().navigate(R.id.userProfileFragment, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

