package com.example.uplyft.ui.main.profile

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.uplyft.R
import com.example.uplyft.databinding.FragmentEditProfileBinding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.uplyft.domain.model.User
import com.example.uplyft.utils.PermissionManager
import com.example.uplyft.utils.Resource
import com.google.firebase.auth.FirebaseAuth
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import com.example.uplyft.viewmodel.EditProfileViewModel
import kotlinx.coroutines.launch
import java.io.File


class EditProfileFragment : Fragment() {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EditProfileViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
    }

    private lateinit var permissionManager: PermissionManager
    private var currentUid: String? = null
    private var cameraImageUri: Uri? = null

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.updateProfileImage(it, currentUid!!) } }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            viewModel.updateProfileImage(cameraImageUri!!, currentUid!!)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid == null) {
            findNavController().popBackStack()
            return
        }

        permissionManager = PermissionManager(this)

        viewModel.loadUser(currentUid!!)
        setupClickListeners()
        observeStates()
    }

    private fun setupClickListeners() {
        binding.ivBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.tvEditPhoto.setOnClickListener { showImagePickerDialog() }
        binding.ivProfileImage.setOnClickListener { showImagePickerDialog() }

        binding.tvDone.setOnClickListener {
            val name     = binding.etName.text.toString()
            val username = binding.etUsername.text.toString()
            val bio      = binding.etBio.text.toString()
            viewModel.saveProfile(currentUid!!, name, username, bio)
        }
    }

    private fun observeStates() {
        // Load user into fields
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loadState.collect { state ->
                    if (_binding == null) return@collect
                    when (state) {
                        is Resource.Loading -> {
                            binding.progressBar.visibility = View.VISIBLE
                        }
                        is Resource.Success -> {
                            binding.progressBar.visibility = View.GONE
                            populateFields(state.data)
                        }
                        is Resource.Error -> {
                            binding.progressBar.visibility = View.GONE
                            toast(state.message)
                        }
                        null -> Unit
                    }
                }
            }
        }

        // Save / image update result
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saveState.collect { state ->
                    if (_binding == null) return@collect
                    when (state) {
                        is Resource.Loading -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.tvDone.isEnabled = false
                        }
                        is Resource.Success -> {
                            binding.progressBar.visibility = View.GONE
                            binding.tvDone.isEnabled = true
                            toast("Profile updated")
                            findNavController().popBackStack()  // go back to profile
                        }
                        is Resource.Error -> {
                            binding.progressBar.visibility = View.GONE
                            binding.tvDone.isEnabled = true
                            toast(state.message)
                        }
                        null -> Unit
                    }
                }
            }
        }
    }

    private fun populateFields(user: User) {
        binding.etName.setText(user.fullName)
        binding.etUsername.setText(user.username)
        binding.etBio.setText(user.bio)

        Glide.with(this)
            .load(user.profileImageUrl)
            .placeholder(R.drawable.app_logo)
            .error(R.drawable.app_logo)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(binding.ivProfileImage)
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
            toast("Could not open camera")
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}