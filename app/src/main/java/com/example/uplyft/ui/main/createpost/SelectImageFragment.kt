package com.example.uplyft.ui.main.createpost

import com.example.uplyft.utils.PermissionManager
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.uplyft.R
import com.example.uplyft.databinding.FragmentSelectImageBinding
import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.example.uplyft.ui.adapter.GalleryAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.core.content.FileProvider
import java.lang.Exception
import java.util.UUID
import android.content.Context


// ui/main/createpost/SelectImageFragment.kt
class SelectImageFragment : Fragment() {

    private var _binding: FragmentSelectImageBinding? = null
    private val binding get() = _binding!!

    private lateinit var permissionManager: PermissionManager
    private lateinit var galleryAdapter   : GalleryAdapter

    private var selectedUri    : Uri?    = null
    private var cameraImageUri : Uri?    = null
    private var isMultiMode    : Boolean = false

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            selectedUri               = cameraImageUri
            binding.tvNext.visibility = View.VISIBLE
            Glide.with(this).load(cameraImageUri)
                .centerCrop().into(binding.ivPreview)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSelectImageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        permissionManager = PermissionManager(this)
        setupGalleryGrid()

        // ✅ Reset multi-select state when returning to fragment
        isMultiMode = false
        galleryAdapter.resetMultiSelect()

        checkPermissionAndLoad()
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.ivClose.setOnClickListener {
            findNavController().popBackStack()
        }

        // ✅ toggle multi-select
        binding.tvSelect.setOnClickListener {
            isMultiMode = !isMultiMode

            if (isMultiMode) {
                // Entering multi-select mode
                galleryAdapter.enterMultiSelectMode()
                binding.tvSelect.text              = "Cancel"
                binding.tvSelectedCount.visibility = View.GONE
                binding.tvNext.visibility          = View.GONE
            } else {
                // Exiting multi-select mode
                galleryAdapter.exitMultiSelectMode()
                binding.tvSelect.text              = "Select"
                binding.tvSelectedCount.visibility = View.GONE

                // Restore single select next button if image selected
                selectedUri?.let { binding.tvNext.visibility = View.VISIBLE }
            }
        }

        binding.tvNext.setOnClickListener {
            val uris = if (isMultiMode) {
                val selectedList = galleryAdapter.getSelectedUris()
                if (selectedList.isEmpty()) return@setOnClickListener
                selectedList
            } else {
                listOf(selectedUri ?: return@setOnClickListener)
            }
            if (uris.isEmpty()) return@setOnClickListener

            val bundle = Bundle().apply {
                putStringArrayList("imageUris", ArrayList(uris.map { it.toString() }))
            }
            findNavController().navigate(
                R.id.action_selectImageFragment_to_createPostFragment, bundle
            )
        }
    }

    private fun setupGalleryGrid() {
        galleryAdapter = GalleryAdapter(
            onImageSelected = { uri ->
                selectedUri               = uri
                binding.tvNext.visibility = View.VISIBLE
                Glide.with(this).load(uri).centerCrop().into(binding.ivPreview)
            },
            onCameraClick   = {
                permissionManager.requestCamera(onGranted = { openCamera() })
            },
            onMultiSelected = { uris ->
                if (uris.isEmpty()) {
                    binding.tvNext.visibility          = View.GONE
                    binding.tvSelectedCount.visibility = View.GONE
                } else {
                    binding.tvNext.visibility          = View.VISIBLE
                    binding.tvSelectedCount.visibility = View.VISIBLE
                    binding.tvSelectedCount.text       = "${uris.size}/10"
                    Glide.with(this).load(uris.first())
                        .centerCrop().into(binding.ivPreview)
                }
            }
        )
        binding.rvGallery.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter       = galleryAdapter
        }
    }

    private fun checkPermissionAndLoad() {
        permissionManager.requestGallery(onGranted = { loadGalleryImages() })
    }

    private fun loadGalleryImages() {
        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val images = withContext(Dispatchers.IO) { fetchGalleryImages(context) }
                if (_binding == null) return@launch
                if (images.isNotEmpty()) {
                    selectedUri               = images.first()
                    binding.tvNext.visibility = View.VISIBLE
                    Glide.with(this@SelectImageFragment)
                        .load(images.first()).centerCrop().into(binding.ivPreview)
                }
                galleryAdapter.submitList(images)
            } catch (e: Exception) {
                if (_binding == null) return@launch
                Toast.makeText(requireContext(),
                    "Failed to load images", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchGalleryImages(context: Context): List<Uri> {
        val images     = mutableListOf<Uri>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder  = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        context.contentResolver.query(
            collection, projection, null, null, sortOrder
        )?.use { cursor ->
            val col = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                images.add(ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    cursor.getLong(col)
                ))
            }
        }
        return images
    }

    private fun openCamera() {
        try {
            val file = File(requireContext().cacheDir,
                "camera_${System.currentTimeMillis()}.jpg")
            cameraImageUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider", file
            )
            cameraLauncher.launch(cameraImageUri)
        } catch (e: Exception) {
            Toast.makeText(requireContext(),
                "Could not open camera", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}