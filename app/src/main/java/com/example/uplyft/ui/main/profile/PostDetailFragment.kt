package com.example.uplyft.ui.main.profile

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.uplyft.R
import com.example.uplyft.databinding.FragmentPostDetailBinding
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.example.uplyft.domain.model.Post
import com.example.uplyft.viewmodel.PostViewModel
import kotlinx.coroutines.launch
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.uplyft.ui.adapter.PostAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import androidx.fragment.app.activityViewModels

// ui/main/profile/PostDetailFragment.kt
class PostDetailFragment : Fragment() {

    private var _binding: FragmentPostDetailBinding? = null
    private val binding get() = _binding!!

    private val postViewModel: PostViewModel by activityViewModels()

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
                val bundle = Bundle().apply { putString("postId", post.postId) }
                findNavController().navigate(
                    R.id.action_postDetailFragment_to_commentsFragment,
                    bundle
                )
            },
            onShareClick   = { post -> sharePost(post) },
            onSaveClick    = { post -> postViewModel.toggleSavePost(post) },
            onRetryClick   = { post -> postViewModel.retryUpload(post) },
            onProfileClick = { post ->
                val currentUid = FirebaseAuth.getInstance().currentUser?.uid
                if (post.userId == currentUid) {
                    findNavController().popBackStack()
                } else {
                    val bundle = Bundle().apply { putString("userId", post.userId) }
                    findNavController().navigate(
                        R.id.action_postDetailFragment_to_userProfileFragment, bundle
                    )
                }
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
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                when {
                    // ✅ case 1: came from notification — only postId passed
                    // fetch single post from Firestore directly
                    ownerUserId == null && clickedPostId != null -> {
                        loadSinglePost(clickedPostId!!)
                    }

                    // ✅ case 2: came from profile grid — userId + postId passed
                    ownerUserId != null -> {
                        loadUserPosts(ownerUserId!!)
                    }
                }
            }
        }
    }

    // ✅ load single post by postId — for notification deep link
    private suspend fun loadSinglePost(postId: String) {
        try {
            binding.progressBar.visibility = View.VISIBLE

            val doc = FirebaseFirestore.getInstance()
                .collection("posts")
                .document(postId)
                .get()
                .await()

            if (_binding == null) return

            binding.progressBar.visibility = View.GONE

            if (!doc.exists()) {
                Toast.makeText(requireContext(),
                    "Post not found", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
                return
            }

            // ✅ manual mapping
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid
            val isLiked    = if (currentUid != null) {
                FirebaseFirestore.getInstance()
                    .collection("posts")
                    .document(postId)
                    .collection("likes")
                    .document(currentUid)
                    .get()
                    .await()
                    .exists()
            } else false

            @Suppress("UNCHECKED_CAST")
            val imageUrls = try {
                doc.get("imageUrls") as? List<String>
                    ?: listOf(doc.getString("imageUrl") ?: "")
            } catch (e: Exception) {
                listOf(doc.getString("imageUrl") ?: "")
            }.filter { it.isNotBlank() }

            val post = Post(
                postId        = doc.id,
                userId        = doc.getString("userId")       ?: "",
                username      = doc.getString("username")     ?: "",
                userImageUrl  = doc.getString("userImageUrl") ?: "",
                imageUrl      = doc.getString("imageUrl")     ?: "",
                imageUrls     = imageUrls,
                caption       = doc.getString("caption")      ?: "",
                likesCount    = doc.getLong("likesCount")?.toInt()    ?: 0,
                commentsCount = doc.getLong("commentsCount")?.toInt() ?: 0,
                isLiked       = isLiked,
                createdAt     = doc.getLong("createdAt")      ?: 0L
            )

            binding.tvOwnerName.text = post.username
            postAdapter.submitList(listOf(post))

        } catch (e: Exception) {
            if (_binding == null) return
            binding.progressBar.visibility = View.GONE
            Toast.makeText(requireContext(),
                "Failed to load post", Toast.LENGTH_SHORT).show()
            Log.e("PostDetail", "loadSinglePost error: ${e.message}")
        }
    }

    // ✅ load all user posts and scroll to clicked — for profile grid tap
    private fun loadUserPosts(userId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            postViewModel.getUserPosts(userId).collect { posts ->
                if (_binding == null) return@collect
                if (posts.isEmpty()) return@collect

                val ownerName = posts.firstOrNull()?.username ?: ""
                binding.tvOwnerName.text = ownerName

                postAdapter.submitList(posts)

                // scroll to clicked post
                val index = posts.indexOfFirst { it.postId == clickedPostId }
                if (index != -1) {
                    binding.rvPostDetail.scrollToPosition(index)
                }
            }
        }
    }

    private fun sharePost(post: Post) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT,
                "Check out this post on Uplyft! ${post.imageUrl}")
        }
        startActivity(Intent.createChooser(intent, "Share via"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}