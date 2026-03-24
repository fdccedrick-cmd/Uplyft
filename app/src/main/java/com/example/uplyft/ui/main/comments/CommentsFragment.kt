package com.example.uplyft.ui.main.comments

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.uplyft.R
import com.example.uplyft.data.local.AppDatabase
import com.example.uplyft.data.local.entity.toDomain
import com.example.uplyft.ui.adapter.CommentAdapter
import com.example.uplyft.ui.widget.LockedRecyclerView
import com.example.uplyft.utils.Resource
import com.example.uplyft.viewmodel.CommentViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.example.uplyft.ui.adapter.MentionAdapter
import com.example.uplyft.viewmodel.MentionViewModel
import androidx.navigation.fragment.findNavController
import android.text.Editable
import android.text.TextWatcher
import com.google.android.material.appbar.MaterialToolbar
import com.facebook.shimmer.ShimmerFrameLayout

class CommentsFragment : Fragment() {

    private val viewModel: CommentViewModel by activityViewModels()

    private lateinit var adapter              : CommentAdapter
    private lateinit var recyclerView         : LockedRecyclerView
    private lateinit var etComment            : EditText
    private lateinit var tvPost               : TextView
    private lateinit var emptyState           : LinearLayout
    private lateinit var shimmerLoadingContainer: ShimmerFrameLayout
    private lateinit var toolbar              : MaterialToolbar

    private var postId         : String?  = null
    private var currentUid     : String?  = null
    private var currentUsername: String   = ""

    private lateinit var mentionViewModel: MentionViewModel
    private lateinit var mentionAdapter  : MentionAdapter
    private var currentMentionQuery      : String = ""
    private var isMentioning             : Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_comments, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        postId     = arguments?.getString("postId")
        currentUid = FirebaseAuth.getInstance().currentUser?.uid

        if (postId == null) {
            findNavController().navigateUp()
            return
        }

        recyclerView  = view.findViewById(R.id.rvComments)
        emptyState    = view.findViewById(R.id.layoutEmptyState)
        shimmerLoadingContainer = view.findViewById(R.id.shimmerLoadingContainer)
        toolbar       = view.findViewById(R.id.toolbar)

        mentionViewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
        ).get(MentionViewModel::class.java)

        setupToolbar()
        setupRecycler()
        setupInput(view)
        setupKeyboardHandling(view)
        observeData()
        loadUser()
        loadCurrentUserAvatar(view)

        // Load comments for this post
        postId?.let {
            // Check if comments are already loaded (navigation back scenario)
            val currentComments = viewModel.comments.value
            if (currentComments.isNotEmpty()) {
                // Comments already loaded, hide shimmer immediately
                hasLoadedOnce = true
                shimmerLoadingContainer.visibility = View.GONE
                stopShimmerAnimation()
                emptyState.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            } else {
                // First time loading, show shimmer
                shimmerLoadingContainer.visibility = View.VISIBLE
                emptyState.visibility = View.GONE
                recyclerView.visibility = View.GONE
            }

            viewModel.loadComments(it)
            viewModel.syncPostCommentCountFromFirestore(it)
        }
    }

    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    // ─────────────────────────────────────────────
    // SHIMMER ANIMATION
    // ─────────────────────────────────────────────

    private fun stopShimmerAnimation() {
        shimmerLoadingContainer.stopShimmer()
    }

    private fun setupRecycler() {
        adapter = CommentAdapter(
            currentUid      = currentUid ?: "",
            currentUsername = currentUsername,
            onDelete        = { c ->
                postId?.let { viewModel.deleteComment(it, c.commentId) }
            },
            onLike          = { viewModel.toggleCommentLike(it) },
            onAvatar        = { comment ->
                if (comment.userId == currentUid) {
                    navigateToOwnProfile()
                } else {
                    navigateToUserProfile(comment.userId)
                }
            },
            onUsername      = { navigateToProfile(it) }
        )

        recyclerView.apply {
            layoutManager            = LinearLayoutManager(requireContext())
            adapter                  = this@CommentsFragment.adapter
            isNestedScrollingEnabled = true
            overScrollMode           = View.OVER_SCROLL_NEVER
            clipToPadding            = false
            itemAnimator             = null
        }
    }

    private fun setupInput(view: View) {
        etComment = view.findViewById(R.id.etComment)
        tvPost    = view.findViewById(R.id.tvPost)

        val rvMentions = view.findViewById<RecyclerView>(R.id.rvMentionSuggestions)
        val mentionDivider = view.findViewById<View>(R.id.mentionDivider)

        mentionAdapter = MentionAdapter { user ->
            val text    = etComment.text.toString()
            val cursor  = etComment.selectionStart
            val atIndex = text.lastIndexOf('@', cursor - 1)
            if (atIndex >= 0) {
                val newText = text.substring(0, atIndex) +
                        "@${user.username} " +
                        text.substring(cursor)
                etComment.setText(newText)
                etComment.setSelection(atIndex + user.username.length + 2)
            }
            rvMentions.visibility      = View.GONE
            mentionDivider.visibility  = View.GONE
            isMentioning               = false
            currentUid?.let { mentionViewModel.clearSuggestions() }
        }

        rvMentions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter       = mentionAdapter
        }

        etComment.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                val has          = !s.isNullOrBlank()
                tvPost.isEnabled = has
                tvPost.alpha     = if (has) 1f else 0.4f

                val text   = s?.toString() ?: return
                val cursor = (st + c).coerceAtMost(text.length)
                val sub    = text.substring(0, cursor)
                val atIdx  = sub.lastIndexOf('@')

                if (atIdx >= 0) {
                    val afterAt = sub.substring(atIdx + 1)
                    if (!afterAt.contains(' ')) {
                        isMentioning        = true
                        currentMentionQuery = afterAt
                        currentUid?.let {
                            mentionViewModel.searchMentions(afterAt, it)
                        }
                        return
                    }
                }

                isMentioning = false
                mentionViewModel.clearSuggestions()
            }
        })

        tvPost.setOnClickListener {
            val text = etComment.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            postId?.let { viewModel.addComment(it, text) }
            etComment.text.clear()
            rvMentions.visibility     = View.GONE
            mentionDivider.visibility = View.GONE
            isMentioning              = false
            hideKeyboard()
        }
    }

    private fun setupKeyboardHandling(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            // Adjust bottom padding of RecyclerView
            val bottomPadding = imeInsets.bottom - navInsets.bottom
            recyclerView.setPadding(0, 0, 0, bottomPadding.coerceAtLeast(0))

            insets
        }
    }

    private var lastCommentCount = 0
    private var hasLoadedOnce = false

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.comments.collect { list ->
                        val currentCount = list.size

                        adapter.submitFullList(list)

                        // ✅ FIXED: Only handle shimmer/empty state on first load
                        if (!hasLoadedOnce) {
                            // First load complete
                            hasLoadedOnce = true
                            shimmerLoadingContainer.visibility = View.GONE
                            stopShimmerAnimation()

                            if (list.isEmpty()) {
                                // No comments - show empty state
                                emptyState.visibility = View.VISIBLE
                                recyclerView.visibility = View.GONE
                            } else {
                                // Has comments - show recycler
                                emptyState.visibility = View.GONE
                                recyclerView.visibility = View.VISIBLE

                                // Auto-scroll to latest on first load
                                recyclerView.post {
                                    if (adapter.itemCount > 0) {
                                        recyclerView.scrollToPosition(adapter.itemCount - 1)
                                    }
                                }
                            }
                        } else {
                            // Subsequent updates (new comments added or navigation back)
                            // Just update visibility, don't touch shimmer
                            if (list.isEmpty()) {
                                emptyState.visibility = View.VISIBLE
                                recyclerView.visibility = View.GONE
                            } else {
                                emptyState.visibility = View.GONE
                                recyclerView.visibility = View.VISIBLE

                                // Auto-scroll to latest comment only if count increased
                                if (currentCount > lastCommentCount) {
                                    recyclerView.post {
                                        if (adapter.itemCount > 0) {
                                            recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
                                        }
                                    }
                                }
                            }
                        }

                        lastCommentCount = currentCount
                    }
                }

                launch {
                    viewModel.addState.collect { state ->
                        if (state is Resource.Error) {
                            Toast.makeText(requireContext(),
                                state.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                launch {
                    mentionViewModel.suggestions.collect { list ->
                        if (!isAdded) return@collect
                        if (!::mentionAdapter.isInitialized) return@collect

                        val rvMentions     = view?.findViewById<RecyclerView>(
                            R.id.rvMentionSuggestions) ?: return@collect
                        val mentionDivider = view?.findViewById<View>(
                            R.id.mentionDivider) ?: return@collect

                        mentionAdapter.submitList(list)
                        val show = list.isNotEmpty() && isMentioning

                        rvMentions.visibility     = if (show) View.VISIBLE else View.GONE
                        mentionDivider.visibility = if (show) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun loadUser() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(requireContext())
            currentUsername = withContext(Dispatchers.IO) {
                currentUid?.let { db.userDao().getUserById(it)?.username } ?: ""
            }
            if (::adapter.isInitialized) adapter.updateCurrentUsername(currentUsername)
        }
    }

    private fun loadCurrentUserAvatar(view: View) {
        currentUid?.let { uid ->
            lifecycleScope.launch {
                val user = withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(requireContext())
                        .userDao().getUserById(uid)?.toDomain()
                }
                if (!isAdded) return@launch
                Glide.with(this@CommentsFragment)
                    .load(user?.profileImageUrl?.ifEmpty { null })
                    .placeholder(R.drawable.app_logo)
                    .circleCrop()
                    .into(view.findViewById(R.id.ivCurrentUserAvatar))
            }
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etComment.windowToken, 0)
    }

    private fun navigateToProfile(username: String) {
        lifecycleScope.launch {
            val db     = AppDatabase.getInstance(requireContext())
            val userId = withContext(Dispatchers.IO) {
                db.userDao().getUserByUsername(username)?.uid
            }
            userId?.let { uid ->
                navigateToUserProfile(uid)
            }
        }
    }

    private fun navigateToOwnProfile() {
        try {
            // Instagram-style:
            // 1. Pop everything back to the main tab that opened comments (usually Home)
            // 2. Then switch to Profile tab
            // This ensures clean navigation: Home → [Comments dismissed] → Profile

            // Pop back to the start destination (homeFragment)
            findNavController().popBackStack(R.id.homeFragment, false)

            // Now switch to profile tab (will be at root level)
            requireActivity()
                .findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigationView)
                ?.selectedItemId = R.id.profileFragment
        } catch (e: Exception) {
            Log.e("CommentsFragment", "Failed to navigate to own profile", e)
        }
    }

    private fun navigateToUserProfile(userId: String) {
        val bundle = Bundle().apply { putString("userId", userId) }

        try {
            when (findNavController().currentDestination?.id) {
                R.id.commentsFragment -> {
                    findNavController().navigate(
                        R.id.action_commentsFragment_to_userProfileFragment,
                        bundle
                    )
                }
                else -> {
                    Log.e("CommentsFragment", "Unknown current destination")
                }
            }
        } catch (e: Exception) {
            Log.e("CommentsFragment", "Navigation failed", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::recyclerView.isInitialized) recyclerView.adapter = null
    }
}


