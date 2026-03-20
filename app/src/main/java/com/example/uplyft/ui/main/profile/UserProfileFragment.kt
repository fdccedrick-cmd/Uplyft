package com.example.uplyft.ui.main.profile

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.uplyft.R
import com.example.uplyft.databinding.FragmentUserProfileBinding
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.uplyft.utils.Resource
import kotlinx.coroutines.launch
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.ViewModelProvider
import com.example.uplyft.ui.adapter.ProfilePostAdapter
import com.example.uplyft.utils.UserProfileState
import com.example.uplyft.utils.PermissionManager
import com.google.firebase.auth.FirebaseAuth
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import com.example.uplyft.ui.main.comments.CommentsBottomSheet
import com.google.android.material.tabs.TabLayout
import com.example.uplyft.viewmodel.ProfileViewModel


class UserProfileFragment : Fragment() {

    private var _binding: FragmentUserProfileBinding? = null
    private val binding get() = _binding!!

    private val profileViewModel: ProfileViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
    }

    private lateinit var permissionManager : PermissionManager
    private lateinit var profilePostAdapter: ProfilePostAdapter

    private var currentUid  : String? = null
    private var targetUserId: String? = null
    private var currentTab  : Int     = 0
    private var cameraImageUri: Uri?  = null

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { } }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentUid   = FirebaseAuth.getInstance().currentUser?.uid
        targetUserId = arguments?.getString("userId")

        if (targetUserId == null) {
            findNavController().popBackStack()
            return
        }

        // ✅ if own profile — redirect to profileFragment
        if (targetUserId == currentUid) {
            findNavController().navigate(R.id.profileFragment)
            return
        }

        permissionManager = PermissionManager(this)
        setupPostsGrid()
        setupClickListeners()
        loadProfile()
        observeStates()
    }

    private fun setupPostsGrid() {
        profilePostAdapter = ProfilePostAdapter(
            onPostClick = { post ->
                val bundle = Bundle().apply {
                    putString("postId", post.postId)
                    putString("userId", post.userId)
                }
                findNavController().navigate(
                    R.id.action_userProfileFragment_to_postDetailFragment,
                    bundle
                )
            }
        )
        binding.rvUserPosts.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter       = profilePostAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupClickListeners() {
        binding.ivBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnFollow.setOnClickListener {
            val state = (profileViewModel.profileState.value as? Resource.Success)?.data
            if (state?.isFollowing == true) showUnfollowMenu()
            else targetUserId?.let { profileViewModel.toggleFollow(it) }
        }

        binding.ivOptions.setOnClickListener {
            showUnfollowMenu()
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                updateTabContent()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateTabContent() {
        if (_binding == null) return
        when (currentTab) {
            0 -> {
                binding.rvUserPosts.visibility     = View.VISIBLE
                binding.layoutEmptySaved.visibility = View.GONE
            }
            1 -> {
                binding.rvUserPosts.visibility     = View.GONE
                binding.layoutEmptySaved.visibility = View.VISIBLE
            }
        }
    }

    private fun loadProfile() {
        val uid = targetUserId ?: return
        profileViewModel.loadProfile(uid)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                profileViewModel.getUserPosts(uid).collect { posts ->
                    if (_binding == null) return@collect
                    if (currentTab == 0) profilePostAdapter.submitList(posts)
                    binding.tvPostCount.text = posts.size.toString()
                }
            }
        }
    }

    private fun observeStates() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                profileViewModel.profileState.collect { state ->
                    if (_binding == null) return@collect
                    when (state) {
                        is Resource.Loading -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.scrollView.visibility  = View.GONE
                        }
                        is Resource.Success -> {
                            binding.progressBar.visibility = View.GONE
                            binding.scrollView.visibility  = View.VISIBLE
                            bindProfile(state.data)
                        }
                        is Resource.Error -> {
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(requireContext(),
                                state.message, Toast.LENGTH_SHORT).show()
                        }
                        null -> Unit
                    }
                }
            }
        }
    }

    private fun bindProfile(state: UserProfileState) {
        val user = state.user

        binding.tvUsername.text       = user.username.ifEmpty { user.fullName }
        binding.tvFullName.text       = user.fullName
        binding.tvBio.text            = user.bio
        binding.tvFollowersCount.text = state.followersCount.toString()
        binding.tvFollowingCount.text = state.followingCount.toString()
        binding.tvPostCount.text      = state.posts.size.toString()

        Glide.with(this)
            .load(user.profileImageUrl.ifEmpty { null })
            .placeholder(R.drawable.app_logo)
            .error(R.drawable.app_logo)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(binding.ivProfileImage)

        profilePostAdapter.submitList(state.posts)
        updateFollowButton(state.isFollowing, state.isFollowingBack)

        // show ⋮ only when following
        binding.ivOptions.visibility = if (state.isFollowing) View.VISIBLE else View.GONE
    }

    private fun updateFollowButton(isFollowing: Boolean, isFollowingBack: Boolean) {
        when {
            isFollowing -> {
                binding.btnFollow.text = "Following"
                binding.btnFollow.backgroundTintList =
                    ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.surface)
                    )
                binding.btnFollow.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.on_background)
                )
            }
            isFollowingBack -> {
                binding.btnFollow.text = "Follow Back"
                binding.btnFollow.backgroundTintList =
                    ColorStateList.valueOf(Color.parseColor("#0095F6"))
                binding.btnFollow.setTextColor(Color.WHITE)
            }
            else -> {
                binding.btnFollow.text = "Follow"
                binding.btnFollow.backgroundTintList =
                    ColorStateList.valueOf(Color.parseColor("#0095F6"))
                binding.btnFollow.setTextColor(Color.WHITE)
            }
        }
    }

    private fun showUnfollowMenu() {
        val popup = PopupMenu(requireContext(), binding.btnFollow)
        popup.menu.add("Unfollow")
        popup.setOnMenuItemClickListener { item ->
            if (item.title == "Unfollow") {
                targetUserId?.let { profileViewModel.toggleFollow(it) }
                true
            } else false
        }
        popup.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}