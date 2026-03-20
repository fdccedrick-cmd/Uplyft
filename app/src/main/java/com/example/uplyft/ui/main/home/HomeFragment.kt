package com.example.uplyft.ui.main.home

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.uplyft.R
import com.example.uplyft.databinding.FragmentHomeBinding
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.uplyft.domain.model.Post
import com.example.uplyft.ui.adapter.PostAdapter
import com.example.uplyft.viewmodel.FeedState
import com.example.uplyft.viewmodel.PostViewModel
import android.content.Intent
import android.graphics.Color
import android.widget.Toast
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.example.uplyft.ui.main.comments.CommentsBottomSheet

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val postViewModel: PostViewModel by activityViewModels()
    private lateinit var postAdapter: PostAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        parentFragmentManager.setFragmentResultListener(
            "navigate_to_profile",
            viewLifecycleOwner
        ) { _, bundle ->
            val userId = bundle.getString("userId") ?: return@setFragmentResultListener
            val b = Bundle().apply { putString("userId", userId) }
            findNavController().navigate(
                R.id.action_homeFragment_to_userProfileFragment, b
            )
        }
        setupRecyclerView()
        setupClickListeners()
        observeFeedState()
        observePosts()
        setupSwipeRefresh()
    }
    private fun setupSwipeRefresh() {
        binding.swipeRefresh.apply {
            // match your app accent color
            setColorSchemeColors(Color.BLACK)
            setOnRefreshListener {
                postViewModel.loadFeed()
            }
        }
    }

    private fun setupRecyclerView() {
        postAdapter = PostAdapter(
            onLikeClick    = { post -> postViewModel.toggleLike(post) },
            onCommentClick = { post ->
                CommentsBottomSheet.newInstance(post.postId)
                    .show(parentFragmentManager, CommentsBottomSheet.TAG)
            },
            onShareClick   = { post -> sharePost(post) },
            onProfileClick = { post ->
                val currentUid = FirebaseAuth.getInstance().currentUser?.uid
                if (post.userId == currentUid) {
                    requireActivity()
                        .findViewById<BottomNavigationView>(R.id.bottomNavigationView)
                        .selectedItemId = R.id.profileFragment
                } else {
                    val bundle = Bundle().apply { putString("userId", post.userId) }
                    findNavController().navigate(
                        R.id.action_homeFragment_to_userProfileFragment, bundle
                    )
                }
            }
        )

        val layoutManager = LinearLayoutManager(requireContext())

        binding.rvFeed.apply {
            this.layoutManager = layoutManager
            adapter            = postAdapter
            // RecycledViewPool — reuses ViewHolders efficiently
            setRecycledViewPool(RecyclerView.RecycledViewPool())
            // Smooth image loading — don't stop Glide on fast scroll
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                    when (newState) {
                        RecyclerView.SCROLL_STATE_IDLE,
                        RecyclerView.SCROLL_STATE_DRAGGING ->
                            Glide.with(this@HomeFragment).resumeRequests()
                        RecyclerView.SCROLL_STATE_SETTLING ->
                            Glide.with(this@HomeFragment).pauseRequests()
                    }
                }
                // Pagination — load more when near bottom
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0) return   // only trigger on downward scroll
                    val visible    = layoutManager.childCount
                    val total      = layoutManager.itemCount
                    val firstVisible = layoutManager.findFirstVisibleItemPosition()
                    if (visible + firstVisible >= total - 3) {
                        postViewModel.loadMorePosts()
                    }
                }
            })
        }
    }

    private fun setupClickListeners() {
        binding.ivAddPost.setOnClickListener {
            findNavController().navigate(R.id.selectImageFragment)
        }
    }

    private fun observeFeedState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                postViewModel.feedState.collect { state ->
                    if(_binding == null) return@collect
                    when (state) {
                        is FeedState.Loading -> {
                            // only show shimmer on first load
                            // swipeRefresh spinner shows on pull-to-refresh
                            if (!binding.swipeRefresh.isRefreshing) {
                                binding.shimmerLayout.visibility = View.VISIBLE
                                binding.shimmerLayout.startShimmer()
                                binding.rvFeed.visibility = View.GONE
                            }
                        }
                        is FeedState.Success -> {
                            binding.shimmerLayout.stopShimmer()
                            binding.shimmerLayout.visibility = View.GONE
                            binding.rvFeed.visibility        = View.VISIBLE
                            binding.swipeRefresh.isRefreshing = false  // ← stop spinner
                        }
                        is FeedState.Error -> {
                            binding.shimmerLayout.stopShimmer()
                            binding.shimmerLayout.visibility  = View.GONE
                            binding.rvFeed.visibility         = View.VISIBLE
                            binding.swipeRefresh.isRefreshing = false  // ← stop spinner
                            Toast.makeText(requireContext(),
                                state.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun observePosts() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                postViewModel.posts.collect { posts ->
                    postAdapter.submitList(posts)
                }
            }
        }
    }

    private fun sharePost(post: Post) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Check out this post on Uplyft! ${post.imageUrl}")
        }
        startActivity(Intent.createChooser(intent, "Share via"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}