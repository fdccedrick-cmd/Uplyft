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
import kotlinx.coroutines.launch
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
import com.example.uplyft.viewmodel.NotificationViewModel
import com.example.uplyft.utils.NetworkUtils
import androidx.appcompat.app.AlertDialog
import com.example.uplyft.utils.SeedData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val postViewModel: PostViewModel by activityViewModels()
    private val notifViewModel: NotificationViewModel by activityViewModels()
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

        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        notifViewModel.startListeningUnreadCount(currentUid)

        binding.ivNotifications.setOnClickListener {
            findNavController().navigate(
                R.id.action_homeFragment_to_notificationsFragment
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                notifViewModel.unreadCount.collect { count ->
                    if (count > 0) {
                        binding.tvNotifBadge.visibility = View.VISIBLE
                        binding.tvNotifBadge.text =
                            if (count > 99) "99+" else count.toString()
                    } else {
                        binding.tvNotifBadge.visibility = View.GONE
                    }
                }
            }
        }

        setupRecyclerView()
        setupClickListeners()
        observeFeedState()
        observePosts()
        observeLoadingState()
        setupSwipeRefresh()

        // Check network on view created
        checkNetworkAndShowDialog()

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
                val bundle = Bundle().apply { putString("postId", post.postId) }
                findNavController().navigate(
                    R.id.action_homeFragment_to_commentsFragment,
                    bundle
                )
            },
            onShareClick   = { post -> sharePost(post) },
            onSaveClick    = { post -> postViewModel.toggleSavePost(post) },
            onRetryClick   = { post -> postViewModel.retryUpload(post) },
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

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    // Pagination: Load more when near bottom
                    if (dy > 0) { // Scrolling down
                        val visibleItemCount = layoutManager.childCount
                        val totalItemCount = layoutManager.itemCount
                        val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                        // Load more when 3 items from bottom
                        if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 3
                            && firstVisibleItemPosition >= 0) {
                            // Only load if network is available
                            if (NetworkUtils.isNetworkAvailable(requireContext())) {
                                postViewModel.loadMorePosts()
                            }
                        }
                    }
                }
            })
        }
    }

    private fun setupClickListeners() {
        binding.ivAddPost.setOnClickListener {
            findNavController().navigate(R.id.selectImageFragment)
        }

        //DEBUG: Long press to show seed menu
        binding.ivAddPost.setOnLongClickListener {
            showSeedMenu()
            true
        }
    }

    private fun showSeedMenu() {
        AlertDialog.Builder(requireContext())
            .setTitle("🔧 Debug Menu")
            .setMessage("Choose an action for testing:")
            .setPositiveButton("Seed 50 Posts") { _, _ ->
                seedPosts()
            }
            .setNegativeButton("Delete All My Posts") { _, _ ->
                deleteAllPosts()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun seedPosts() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Toast.makeText(requireContext(), "Seeding 50 posts...", Toast.LENGTH_SHORT).show()

                withContext(Dispatchers.IO) {
                    SeedData.seedPosts()
                }

                Toast.makeText(requireContext(), "✅ Seeded 50 posts! Pull to refresh.", Toast.LENGTH_LONG).show()

                // Auto refresh
                postViewModel.loadFeed()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "❌ Seed failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun deleteAllPosts() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete All Posts?")
            .setMessage("This will permanently delete all your posts from Firestore.")
            .setPositiveButton("Delete") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        Toast.makeText(requireContext(), "Deleting posts...", Toast.LENGTH_SHORT).show()

                        withContext(Dispatchers.IO) {
                            SeedData.deleteAllMyPosts()
                        }

                        Toast.makeText(requireContext(), "✅ Deleted all posts! Pull to refresh.", Toast.LENGTH_LONG).show()

                        // Auto refresh
                        postViewModel.loadFeed()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "❌ Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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

    private fun observeLoadingState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                postViewModel.isLoadingMore.collect { isLoading ->
                    if (isLoading) {
                        postAdapter.showLoading()
                    } else {
                        postAdapter.hideLoading()
                    }
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

    private fun checkNetworkAndShowDialog() {
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            AlertDialog.Builder(requireContext())
                .setTitle("No Internet Connection")
                .setMessage("Cellular data is turned off. You're viewing cached posts only.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Check network when fragment resumes
        checkNetworkAndShowDialog()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}