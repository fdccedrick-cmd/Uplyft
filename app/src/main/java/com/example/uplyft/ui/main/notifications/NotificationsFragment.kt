package com.example.uplyft.ui.main.notifications

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import com.example.uplyft.R
import com.example.uplyft.databinding.FragmentNotificationsBinding
import com.example.uplyft.domain.model.Notification
import com.example.uplyft.ui.adapter.NotificationAdapter
import com.example.uplyft.utils.NotificationTypes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.uplyft.viewmodel.NotificationViewModel


// ui/main/notifications/NotificationsFragment.kt
class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private var hasLoaded = false
    private val viewModel: NotificationViewModel by activityViewModels()
    private lateinit var notifAdapter: NotificationAdapter

    private val currentUid get() =
        FirebaseAuth.getInstance().currentUser?.uid ?: ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecycler()
        setupClickListeners()
        observeData()

        if (!hasLoaded) {
            hasLoaded = true
            viewModel.loadNotifications(currentUid)
        }
    }
    override fun onResume() {
        super.onResume()
        viewModel.markAllRead(currentUid)
    }

    private fun setupRecycler() {
        notifAdapter = NotificationAdapter(
            currentUid    = currentUid,
            onItemClick   = { notif -> handleNotifClick(notif) },
            onFollowClick = { notif -> handleFollowClick(notif) }
        )
        binding.rvNotifications.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter       = notifAdapter
        }
    }

    private fun setupClickListeners() {
        binding.ivBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.tvMarkAllRead.setOnClickListener {
            viewModel.markAllRead(currentUid)
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.isLoading.collect { loading ->
                        binding.shimmerLayout.visibility =
                            if (loading) View.VISIBLE else View.GONE
                        if (loading) binding.shimmerLayout
                            .startShimmer()
                        else binding.shimmerLayout.stopShimmer()
                    }
                }

                launch {
                    viewModel.notifications.collect { list ->
                        notifAdapter.submitList(list)

                        binding.layoutEmpty.visibility =
                            if (list.isEmpty()) View.VISIBLE else View.GONE
                        binding.rvNotifications.visibility =
                            if (list.isEmpty()) View.GONE else View.VISIBLE

                        // show mark all read only if unread exist
                        val hasUnread = list.any { !it.isRead }
                        binding.tvMarkAllRead.visibility =
                            if (hasUnread) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun handleNotifClick(notif: Notification) {
        when (notif.type) {
            NotificationTypes.LIKE_POST,
            NotificationTypes.COMMENT,
            NotificationTypes.LIKE_COMMENT -> {
                if (notif.postId.isNotEmpty()) {
                    val bundle = Bundle().apply {
                        putString("postId", notif.postId)
                    }
                    findNavController().navigate(
                        R.id.action_notificationsFragment_to_postDetailFragment,
                        bundle
                    )
                }
            }
            NotificationTypes.FOLLOW,
            NotificationTypes.FOLLOW_BACK -> {
                val bundle = Bundle().apply {
                    putString("userId", notif.fromUserId)
                }
                findNavController().navigate(
                    R.id.action_notificationsFragment_to_userProfileFragment,
                    bundle
                )
            }
        }
    }

    private fun handleFollowClick(notif: Notification) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val db         = FirebaseFirestore.getInstance()
                val followId   = "${currentUid}_${notif.fromUserId}"
                val followRef  = db.collection("follows").document(followId)
                val exists     = followRef.get().await().exists()

                if (!exists) {
                    followRef.set(mapOf(
                        "followerId"  to currentUid,
                        "followingId" to notif.fromUserId,
                        "createdAt"   to System.currentTimeMillis()
                    )).await()
                    Toast.makeText(requireContext(),
                        "Following @${notif.fromUsername}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(),
                        "Already following @${notif.fromUsername}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(),
                    "Failed to follow", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}