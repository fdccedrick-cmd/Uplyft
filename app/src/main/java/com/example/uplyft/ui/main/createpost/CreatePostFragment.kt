package com.example.uplyft.ui.main.createpost

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.uplyft.R
import com.example.uplyft.databinding.FragmentCreatePostBinding
import androidx.navigation.fragment.findNavController
import android.net.Uri
import android.widget.Toast
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.example.uplyft.viewmodel.PostViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.uplyft.utils.PostUploadState
import kotlinx.coroutines.launch
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomnavigation.BottomNavigationView
class CreatePostFragment : Fragment() {

    private var _binding : FragmentCreatePostBinding? = null
    private val binding get() = _binding!!
    private val postViewModel: PostViewModel by activityViewModels()
    private var imageUris: List<Uri> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreatePostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val uriStrings = arguments?.getStringArrayList("imageUris") ?: arrayListOf()
        imageUris = uriStrings.map { Uri.parse(it) }

        if (imageUris.isNotEmpty()) {
            // show first image as thumbnail
            Glide.with(this).load(imageUris.first())
                .centerCrop().into(binding.ivThumbnail)

            // show badge if multiple
            if (imageUris.size > 1) {
                binding.ivMultiBadge.visibility = View.VISIBLE
            }
        }

        binding.ivBack.setOnClickListener { findNavController().popBackStack() }

        binding.tvShare.setOnClickListener {
            val caption = binding.etCaption.text.toString().trim()
            if (imageUris.isEmpty()) return@setOnClickListener
            postViewModel.createPost(imageUris, caption)
        }

        observeUploadState()
    }

    private fun observeUploadState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                postViewModel.uploadState.collect { state ->
                    when (state) {
                        is PostUploadState.Saving,
                        is PostUploadState.Uploading,
                        is PostUploadState.Syncing -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.tvShare.isEnabled      = false
                        }
                        is PostUploadState.Done -> {
                            binding.progressBar.visibility = View.GONE
                            requireActivity()
                                .findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(
                                    R.id.bottomNavigationView
                                ).selectedItemId = R.id.homeFragment
                        }
                        is PostUploadState.Error -> {
                            binding.progressBar.visibility = View.GONE
                            binding.tvShare.isEnabled      = true
                            Toast.makeText(requireContext(),
                                state.message, Toast.LENGTH_SHORT).show()
                        }
                        null -> Unit
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}