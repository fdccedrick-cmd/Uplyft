package com.example.uplyft.ui.main.profile

import com.example.uplyft.utils.PermissionManager
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import com.example.uplyft.R
import com.example.uplyft.databinding.FragmentProfileBinding
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.uplyft.viewmodel.AuthViewModel
import kotlinx.coroutines.launch
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.GridLayoutManager
import com.example.uplyft.ui.adapter.ProfilePostAdapter
import com.example.uplyft.ui.auth.LoginActivity
import com.example.uplyft.viewmodel.ProfileViewModel
import com.google.firebase.auth.FirebaseAuth
import com.example.uplyft.utils.Resource
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import java.io.File
import android.widget.Toast;
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.ViewModelProvider
import com.example.uplyft.utils.UserProfileState
import com.google.android.material.tabs.TabLayout


class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val authViewModel   : AuthViewModel    by activityViewModels()
    private val profileViewModel: ProfileViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
    }

    private lateinit var permissionManager  : PermissionManager
    private lateinit var profilePostAdapter : ProfilePostAdapter

    private var currentUid    : String?  = null
    private var targetUserId  : String?  = null
    private var isOwnProfile  : Boolean  = false
    private var cameraImageUri: Uri?     = null

    // ✅ track current tab
    private var currentTab = 0

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { uploadProfileImage(it) } }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) uploadProfileImage(cameraImageUri!!)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentUid   = FirebaseAuth.getInstance().currentUser?.uid
        targetUserId = arguments?.getString("userId") ?: currentUid

        if (targetUserId == null) {
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
            return
        }

        isOwnProfile = targetUserId == currentUid

        currentTab = 0

        permissionManager = PermissionManager(this)
        setupPostsGrid()
        setupClickListeners()

        // ✅ Start shimmer animation when fragment loads
        binding.shimmerLoadingContainer.startShimmer()

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
                    R.id.action_profileFragment_to_postDetailFragment,
                    bundle
                )
            },
        )
        binding.rvProfilePosts.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter       = profilePostAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupClickListeners() {
        binding.ivBack.setOnClickListener { findNavController().popBackStack() }

        binding.ivProfileImage.setOnClickListener {
            if (isOwnProfile) showImagePickerDialog()
        }

        binding.btnEditProfile.setOnClickListener {
            findNavController().navigate(
                R.id.action_profileFragment_to_editProfileFragment
            )
        }

        binding.ivNotifications.setOnClickListener {
            if (isOwnProfile) showSettingsMenu()
            else showUnfollowMenu()
        }

        binding.btnFollow.setOnClickListener {
            val state = (profileViewModel.profileState.value as? Resource.Success)?.data
            if (state?.isFollowing == true) showUnfollowMenu()
            else targetUserId?.let { profileViewModel.toggleFollow(it) }
        }

        // ✅ Tab switching
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
                binding.rvProfilePosts.visibility   = View.VISIBLE
                binding.layoutEmptySaved.visibility = View.GONE
            }
            1 -> {
                binding.rvProfilePosts.visibility   = View.GONE
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
                    profilePostAdapter.submitList(posts)
                    binding.tvPostCount.text = posts.size.toString()

                    // ✅ Stop shimmer and hide it when posts load
                    binding.shimmerLoadingContainer.stopShimmer()
                    binding.shimmerLoadingContainer.visibility = View.GONE
                    binding.rvProfilePosts.visibility = View.VISIBLE
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
                        }
                        is Resource.Success -> {
                            binding.progressBar.visibility = View.GONE
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                profileViewModel.updateState.collect { state ->
                    if (_binding == null) return@collect
                    when (state) {
                        is Resource.Loading -> binding.progressBar.visibility = View.VISIBLE
                        is Resource.Success -> {
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(requireContext(),
                                "Profile photo updated", Toast.LENGTH_SHORT).show()
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

        binding.tvLogo.text           = user.username.ifEmpty { user.fullName }
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

        if (isOwnProfile) {
            binding.layoutOwnButtons.visibility    = View.VISIBLE
            binding.layoutFollowButtons.visibility = View.GONE
            binding.ivBack.visibility              = View.GONE
        } else {
            binding.layoutOwnButtons.visibility    = View.GONE
            binding.layoutFollowButtons.visibility = View.VISIBLE
            binding.ivBack.visibility              = View.VISIBLE
            updateFollowButton(state.isFollowing, state.isFollowingBack)
        }
    }

    private fun updateFollowButton(isFollowing: Boolean, isFollowingBack: Boolean) {
        when {
            // ✅ already following
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
            // ✅ target follows you but you don't follow back
            isFollowingBack -> {
                binding.btnFollow.text = "Follow Back"
                binding.btnFollow.backgroundTintList =
                    ColorStateList.valueOf(Color.parseColor("#0095F6"))
                binding.btnFollow.setTextColor(Color.WHITE)
            }
            // not following, not followed
            else -> {
                binding.btnFollow.text = "Follow"
                binding.btnFollow.backgroundTintList =
                    ColorStateList.valueOf(Color.parseColor("#0095F6"))
                binding.btnFollow.setTextColor(Color.WHITE)
            }
        }
    }

    private fun showSettingsMenu() {
        val popup = PopupMenu(requireContext(), binding.ivNotifications)
        popup.menu.add("Logout")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Logout" -> {
                    authViewModel.logout()
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                    requireActivity().finish()
                    true
                }
                else -> false
            }
        }
        popup.show()
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

    private fun showImagePickerDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Profile Photo")
            .setItems(arrayOf("Take Photo", "Choose from Gallery", "Cancel")) { _, which ->
                when (which) {
                    0 -> permissionManager.requestCamera(onGranted = { openCamera() })
                    1 -> permissionManager.requestGallery(
                        onGranted = { galleryLauncher.launch("image/*") }
                    )
                }
            }.show()
    }

    private fun openCamera() {
        try {
            val imageFile = File(
                requireContext().cacheDir,
                "profile_${System.currentTimeMillis()}.jpg"
            )
            cameraImageUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                imageFile
            )
            cameraLauncher.launch(cameraImageUri)
        } catch (e: Exception) {
            Toast.makeText(requireContext(),
                "Could not open camera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadProfileImage(uri: Uri) {
        if (!isOwnProfile) return
        currentUid?.let { profileViewModel.updateProfileImage(uri, it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}