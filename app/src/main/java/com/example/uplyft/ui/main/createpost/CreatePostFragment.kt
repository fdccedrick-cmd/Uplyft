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

class CreatePostFragment : Fragment() {

    private var _binding: FragmentCreatePostBinding? = null
    private val binding get() = _binding!!
    private var imageUri: Uri? = null
    private val postViewModel: PostViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreatePostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Receive URI from SelectImageFragment
        arguments?.getString("imageUri")?.let { uriString ->
            imageUri = Uri.parse(uriString)
            Glide.with(this).load(imageUri).centerCrop().into(binding.ivThumbnail)
        }

        binding.ivBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.tvShare.setOnClickListener {
            val caption = binding.etCaption.text.toString().trim()
            imageUri?.let { uri ->
                postViewModel.createPost(uri, caption)
            }
        }
        observeUploadState()
    }
    private fun observeUploadState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                postViewModel.uploadState.collect { state ->
                    when (state) {
                        is PostUploadState.Saving -> {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.tvShare.isEnabled = false
                            showToast("Saving...")
                        }
                        is PostUploadState.Uploading -> {
                            showToast("Uploading image...")
                        }
                        is PostUploadState.Syncing -> {
                            showToast("Almost done...")
                        }
                        is PostUploadState.Done -> {
                            binding.progressBar.visibility = View.GONE
                            // Navigate back to home feed
                            findNavController().navigate(R.id.homeFragment)
                        }
                        is PostUploadState.Error -> {
                            binding.progressBar.visibility = View.GONE
                            binding.tvShare.isEnabled = true
                            showToast("Failed: ${state.message}")
                        }
                        null -> Unit
                    }
                }
            }
        }
    }

    private fun showToast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}