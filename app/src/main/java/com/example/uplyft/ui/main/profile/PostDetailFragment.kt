package com.example.uplyft.ui.main.profile

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.uplyft.R
import com.example.uplyft.databinding.FragmentPostDetailBinding
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.example.uplyft.domain.model.Post
import com.example.uplyft.viewmodel.PostViewModel
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.uplyft.ui.adapter.PostAdapter
import com.example.uplyft.utils.Resource
import com.example.uplyft.utils.UserProfileState
import com.google.firebase.auth.FirebaseAuth
import com.example.uplyft.ui.main.comments.CommentsBottomSheet

// ui/main/profile/PostDetailFragment.kt
class PostDetailFragment : Fragment() {

    private var _binding: FragmentPostDetailBinding? = null
    private val binding get() = _binding!!

    private val postViewModel: PostViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
    }

    private lateinit var postAdapter: PostAdapter
    private var clickedPostId: String? = null
    private var ownerUserId  : String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPostDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        clickedPostId = arguments?.getString("postId")
        ownerUserId   = arguments?.getString("userId")

        setupRecyclerView()
        setupClickListeners()
        loadPosts()
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
                // navigate back to profile — already on it
                findNavController().popBackStack()
            }
        )
        binding.rvPostDetail.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter       = postAdapter
        }
    }

    private fun setupClickListeners() {
        binding.ivBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun loadPosts() {
        val userId = ownerUserId ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                postViewModel.getUserPosts(userId).collect { posts ->
                    if (_binding == null) return@collect
                    if (posts.isEmpty()) return@collect

                    // Set owner name in header
                    val ownerName = posts.firstOrNull()?.username
                        ?: posts.firstOrNull()?.userId ?: ""
                    binding.tvOwnerName.text = ownerName

                    postAdapter.submitList(posts)

                    // ✅ scroll to clicked post
                    val index = posts.indexOfFirst { it.postId == clickedPostId }
                    if (index != -1) {
                        binding.rvPostDetail.scrollToPosition(index)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}