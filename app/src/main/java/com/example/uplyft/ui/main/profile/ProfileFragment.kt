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
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.GridLayoutManager
import com.example.uplyft.ui.adapter.ProfilePostAdapter
import com.example.uplyft.ui.auth.LoginActivity
import com.example.uplyft.viewmodel.ProfileViewModel
import com.google.firebase.auth.FirebaseAuth
import com.example.uplyft.domain.model.User
import com.example.uplyft.utils.Resource
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import java.io.File
import android.widget.Toast;
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController


class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val authViewModel: AuthViewModel by activityViewModels ()
    private val profileViewModel: ProfileViewModel by viewModels()

    private lateinit var permissionManager: PermissionManager
    private lateinit var profilePostAdapter: ProfilePostAdapter

    private var currentUid: String? = null
    private var cameraImageUri: Uri? = null

    // Image picker — gallery
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { uploadProfileImage(it) }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            uploadProfileImage(cameraImageUri!!)
        }
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

        permissionManager = PermissionManager(this)
        currentUid = FirebaseAuth.getInstance().currentUser?.uid

        setupPostsGrid()
        setupClickListeners()
        setupAddPostListener()
        loadProfile()
        observeStates()
    }

    private fun setupAddPostListener() {
        binding.ivAddPost.setOnClickListener {
            findNavController().navigate(R.id.selectImageFragment)
        }
        binding.btnEditProfile.setOnClickListener {
            findNavController().navigate(
                R.id.action_profileFragment_to_editProfileFragment
            )
        }
    }
    private fun setupPostsGrid() {
        profilePostAdapter = ProfilePostAdapter (
            { post ->
                //navigate to detail screen
            }
       )
        binding.rvProfilePosts.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = profilePostAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupClickListeners() {
        // Tap avatar — show picker dialog
        binding.ivProfileImage.setOnClickListener {
            showImagePickerDialog()
        }

        binding.btnEditProfile.setOnClickListener {
            // navigate to edit profile — wire when ready
        }

        binding.btnLogout.setOnClickListener {
            authViewModel.logout()
            requireActivity().apply {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }
    }

    private fun loadProfile() {
        currentUid?.let { uid ->
            profileViewModel.loadProfile(uid)

            // Load user posts
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    profileViewModel.getUserPosts(uid).collect { posts ->
                        if (_binding == null) return@collect
                        profilePostAdapter.submitList(posts)
                        binding.tvPostCount.text = posts.size.toString()
                    }
                }
            }
        }
    }

    private fun observeStates() {
        // Profile data
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

        // Profile image update
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                profileViewModel.updateState.collect { state ->
                    if (_binding == null) return@collect
                    when (state) {
                        is Resource.Loading -> {
                            binding.progressBar.visibility = View.VISIBLE
                        }
                        is Resource.Success -> {
                            binding.progressBar.visibility = View.GONE
                            // Reload profile to show new image
                            currentUid?.let { profileViewModel.loadProfile(it) }
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

    private fun bindProfile(user: User) {
        binding.tvFullName.text = user.fullName
        binding.tvBio.text      = user.bio
        binding.tvLogo.text     = user.username.ifEmpty { user.fullName }

        Glide.with(this)
            .load(user.profileImageUrl)
            .placeholder(R.drawable.app_logo)
            .error(R.drawable.app_logo)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(binding.ivProfileImage)
    }

    // Show dialog — same as Instagram
    private fun showImagePickerDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Profile Photo")
            .setItems(arrayOf("Take Photo", "Choose from Gallery", "Cancel")) { _, which ->
                when (which) {
                    0 -> permissionManager.requestCamera(
                        onGranted = { openCamera() }
                    )
                    1 -> permissionManager.requestGallery(
                        onGranted = { galleryLauncher.launch("image/*") }
                    )
                    2 -> { /* dismiss */ }
                }
            }
            .show()
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
        currentUid?.let { uid ->
            profileViewModel.updateProfileImage(uri, uid)
        }
    }



override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


}