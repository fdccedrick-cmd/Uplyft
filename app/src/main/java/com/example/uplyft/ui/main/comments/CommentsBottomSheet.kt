package com.example.uplyft.ui.main.comments

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import com.google.android.material.bottomsheet.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.example.uplyft.ui.adapter.MentionAdapter
import com.example.uplyft.viewmodel.MentionViewModel
import androidx.navigation.fragment.findNavController
import android.text.Editable
import android.text.TextWatcher


class CommentsBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: CommentViewModel by activityViewModels()

    private lateinit var adapter              : CommentAdapter
    private lateinit var recyclerView         : LockedRecyclerView
    private lateinit var inputBar             : View
    private lateinit var etComment            : EditText
    private lateinit var tvPost               : TextView
    private lateinit var emptyState           : LinearLayout
    private lateinit var shimmerLoadingContainer: LinearLayout

    private var sheetBehavior  : BottomSheetBehavior<View>? = null
    private var postId         : String?  = null
    private var currentUid     : String?  = null
    private var currentUsername: String   = ""

    private lateinit var mentionViewModel: MentionViewModel
    private lateinit var mentionAdapter  : MentionAdapter
    private var currentMentionQuery      : String = ""
    private var isMentioning             : Boolean = false

    companion object {
        const val TAG = "CommentsBottomSheet"
        fun newInstance(postId: String) = CommentsBottomSheet().apply {
            arguments = Bundle().apply { putString("postId", postId) }
        }
    }

    override fun getTheme() = R.style.BottomSheetRoundedTheme

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return (super.onCreateDialog(savedInstanceState) as BottomSheetDialog).also { dialog ->
            // ✅ ADJUST_NOTHING — we handle keyboard manually via WindowInsetsAnimation
            dialog.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_comments, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        postId     = arguments?.getString("postId")
        currentUid = FirebaseAuth.getInstance().currentUser?.uid
        if (postId == null) { dismiss(); return }

        recyclerView  = view.findViewById(R.id.rvComments)
        emptyState    = view.findViewById(R.id.layoutEmptyState)
        shimmerLoadingContainer = view.findViewById(R.id.shimmerLoadingContainer)

        mentionViewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
        ).get(MentionViewModel::class.java)

        view.post {
            setupSheet()
            setupRecycler()
            observeData()
            loadUser()
            // Load comments for this post
            postId?.let {
                viewModel.loadComments(it)
                // DEBUG: Sync comment count from Firestore (remove after testing)
                viewModel.syncPostCommentCountFromFirestore(it)
            }
        }
    }

    // ─────────────────────────────────────────────
    // SHEET + INPUT
    // ─────────────────────────────────────────────
    private fun updateRecyclerPadding() {
        val navHeight = getNavBarHeight()
        inputBar.post {
            val inputHeight = inputBar.height
            if (inputHeight > 0) {
                val pad = (inputHeight - navHeight).coerceAtLeast(0)
                if (recyclerView.paddingBottom != pad) {
                    recyclerView.setPadding(0, 0, 0, pad)
                    recyclerView.clipToPadding = false
                }
            }
        }
    }
    private fun setupSheet() {
        val dialog      = dialog as? BottomSheetDialog ?: return
        val sheet       = dialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        val windowDecor2 = dialog.window?.decorView as? ViewGroup ?: return

        sheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        sheet.requestLayout()

        val screenH = resources.displayMetrics.heightPixels

        sheetBehavior = BottomSheetBehavior.from(sheet).apply {
            peekHeight        = (screenH * 0.65).toInt()  // 65% of screen initially
            maxHeight         = screenH
            state             = BottomSheetBehavior.STATE_COLLAPSED  // Start at 65%
            skipCollapsed     = false
            isHideable        = true
            isDraggable       = true  // Allow dragging
            isFitToContents   = false
            halfExpandedRatio = 0.65f

            addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_COLLAPSED ||
                        newState == BottomSheetBehavior.STATE_EXPANDED  ||
                        newState == BottomSheetBehavior.STATE_HALF_EXPANDED) {
                        updateRecyclerPadding()
                    }
                }
                override fun onSlide(bottomSheet: View, slideOffset: Float) {}
            })
        }

        inputBar = LayoutInflater.from(requireContext())
            .inflate(R.layout.layout_comment_input, windowDecor2, false)

        etComment    = inputBar.findViewById(R.id.etComment)
        tvPost       = inputBar.findViewById(R.id.tvPost)

        val inputParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM }

        windowDecor2.addView(inputBar, inputParams)

        // ✅ set nav spacer immediately
        val navHeight = getNavBarHeight()
        inputBar.findViewById<View>(R.id.navBarSpacer).apply {
            layoutParams.height = navHeight
            requestLayout()
        }

        // ✅ initial padding after inputBar measured
        inputBar.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    val inputHeight = inputBar.height
                    if (inputHeight > 0) {
                        updateRecyclerPadding()
                        if (::adapter.isInitialized && adapter.itemCount > 0) {
                            recyclerView.post {
                                recyclerView.scrollToPosition(adapter.itemCount - 1)
                            }
                        }
                        inputBar.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    }
                }
            }
        )

        // ✅ keyboard detection via root view layout changes
        ViewCompat.setWindowInsetsAnimationCallback(
            windowDecor2,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: List<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                    val nav       = getNavBarHeight()

                    // ✅ animate input bar position in real time with keyboard
                    (inputBar.layoutParams as FrameLayout.LayoutParams).bottomMargin =
                        if (imeBottom > nav) imeBottom else 0
                    inputBar.requestLayout()

                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    super.onEnd(animation)
                    if (!isAdded) return
                    // ✅ update recycler padding after keyboard fully shown/hidden
                    inputBar.post {
                        val inputHeight = inputBar.height
                        val nav         = getNavBarHeight()
                        if (inputHeight > 0) {
                            val pad = (inputHeight - nav).coerceAtLeast(0)
                            recyclerView.setPadding(0, 0, 0, pad)
                            recyclerView.clipToPadding = false
                        }
                    }
                }
            }
        )

        setupDragHandle()
        setupInput()
        loadCurrentUserAvatar()
    }

    private fun getNavBarHeight(): Int {
        val resourceId = resources.getIdentifier(
            "navigation_bar_height", "dimen", "android"
        )
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    // ─────────────────────────────────────────────
    // DRAG HANDLE
    // ─────────────────────────────────────────────

    private fun setupDragHandle() {
        val dragHandle = view?.findViewById<View>(R.id.dragHandle) ?: return

        recyclerView.isNestedScrollingEnabled = true
        sheetBehavior?.isDraggable            = true

        dragHandle.setOnClickListener {
            sheetBehavior?.state =
                if (sheetBehavior?.state == BottomSheetBehavior.STATE_EXPANDED)
                    BottomSheetBehavior.STATE_COLLAPSED
                else
                    BottomSheetBehavior.STATE_EXPANDED
        }
    }

    // ─────────────────────────────────────────────
    // INPUT
    // ─────────────────────────────────────────────

    private fun setupInput() {

        // ✅ setup mention suggestions RecyclerView
        val rvMentions = inputBar.findViewById<RecyclerView>(R.id.rvMentionSuggestions)
        val mentionDivider = inputBar.findViewById<View>(R.id.mentionDivider)

        mentionAdapter = MentionAdapter { user ->
            // ✅ insert @username into EditText
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
            // hide suggestions
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

                // ✅ find last @ before cursor
                val sub    = text.substring(0, cursor)
                val atIdx  = sub.lastIndexOf('@')

                Log.d("CommentsBottomSheet", "onTextChanged: text='$text', cursor=$cursor, atIdx=$atIdx")

                if (atIdx >= 0) {
                    val afterAt = sub.substring(atIdx + 1)
                    Log.d("CommentsBottomSheet", "Found @ at index $atIdx, afterAt='$afterAt'")
                    // ✅ only trigger if no space after @ (still typing username)
                    if (!afterAt.contains(' ')) {
                        isMentioning        = true
                        currentMentionQuery = afterAt
                        Log.d("CommentsBottomSheet", "Triggering mention search with query='$afterAt'")
                        currentUid?.let {
                            mentionViewModel.searchMentions(afterAt, it)
                        }
                        return
                    }
                }

                // hide suggestions
                Log.d("CommentsBottomSheet", "Hiding mention suggestions")
                isMentioning = false
                mentionViewModel.clearSuggestions()
            }
        })

        etComment.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                etComment.postDelayed({
                    if (!isAdded) return@postDelayed
                    val imm = requireContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(etComment, 0)
                }, 100)
            }
        }

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

    // ─────────────────────────────────────────────
    // RECYCLER
    // ─────────────────────────────────────────────

    private fun setupRecycler() {
        adapter = CommentAdapter(
            currentUid      = currentUid ?: "",
            currentUsername = currentUsername,
            onDelete        = { c ->
                postId?.let { viewModel.deleteComment(it, c.commentId) }
            },
            onLike          = { viewModel.toggleCommentLike(it) },
            onAvatar        = { comment ->
                // ✅ if own comment, navigate to ProfileFragment
                // ✅ if other user, navigate to their UserProfileFragment
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
            adapter                  = this@CommentsBottomSheet.adapter
            isNestedScrollingEnabled = true
            overScrollMode           = View.OVER_SCROLL_NEVER
            clipToPadding            = false
            // Prevent jumping during item changes
            itemAnimator             = null
        }
    }

    // ─────────────────────────────────────────────
    // OBSERVE
    // ─────────────────────────────────────────────

    private var lastCommentCount = 0

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.comments.collect { list ->
                        val currentCount = list.size

                        adapter.submitFullList(list)
                        emptyState.visibility           = if (list.isEmpty()) View.VISIBLE else View.GONE
                        shimmerLoadingContainer.visibility = View.GONE
                        recyclerView.visibility         = View.VISIBLE

                        // Only auto-scroll if a new comment was added
                        if (currentCount > lastCommentCount && list.isNotEmpty()) {
                            recyclerView.post {
                                if (adapter.itemCount > 0) {
                                    recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
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

                // ✅ mention suggestions observer — here it works reliably
                launch {
                    mentionViewModel.suggestions.collect { list ->
                        if (!isAdded) return@collect
                        if (!::mentionAdapter.isInitialized) return@collect

                        Log.d("CommentsBottomSheet", "Received ${list.size} mention suggestions, isMentioning=$isMentioning")

                        val rvMentions     = inputBar.findViewById<RecyclerView>(
                            R.id.rvMentionSuggestions)
                        val mentionDivider = inputBar.findViewById<View>(
                            R.id.mentionDivider)

                        mentionAdapter.submitList(list)
                        val show = list.isNotEmpty() && isMentioning

                        Log.d("CommentsBottomSheet", "Show mention suggestions: $show (list.size=${list.size}, isMentioning=$isMentioning)")

                        rvMentions.visibility     = if (show) View.VISIBLE else View.GONE
                        mentionDivider.visibility = if (show) View.VISIBLE else View.GONE

                        // ✅ update recycler padding when suggestions appear/disappear
                        if (show) updateRecyclerPadding()
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────

    private fun loadUser() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(requireContext())
            currentUsername = withContext(Dispatchers.IO) {
                currentUid?.let { db.userDao().getUserById(it)?.username } ?: ""
            }
            if (::adapter.isInitialized) adapter.updateCurrentUsername(currentUsername)
        }
    }

    private fun loadCurrentUserAvatar() {
        currentUid?.let { uid ->
            lifecycleScope.launch {
                val user = withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(requireContext())
                        .userDao().getUserById(uid)?.toDomain()
                }
                if (!isAdded) return@launch
                Glide.with(this@CommentsBottomSheet)
                    .load(user?.profileImageUrl?.ifEmpty { null })
                    .placeholder(R.drawable.app_logo)
                    .circleCrop()
                    .into(
                        inputBar.findViewById(R.id.ivCurrentUserAvatar)
                    )
            }
        }
    }

    private fun focusKeyboard() {
        etComment.requestFocus()
        val imm = requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(etComment, InputMethodManager.SHOW_IMPLICIT)
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
        // Navigate to ProfileFragment (own profile) via bottom nav
        try {
            requireActivity()
                .findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigationView)
                ?.selectedItemId = R.id.profileFragment
        } catch (e: Exception) {
            // Fallback if bottom nav not available
        }
    }

    private fun navigateToUserProfile(userId: String) {
        // Navigate to UserProfileFragment using NavController
        try {
            val navController = requireParentFragment().findNavController()
            val bundle = Bundle().apply { putString("userId", userId) }

            // Try to navigate from current destination
            when (navController.currentDestination?.id) {
                R.id.homeFragment -> {
                    navController.navigate(R.id.action_homeFragment_to_userProfileFragment, bundle)
                }
                R.id.postDetailFragment -> {
                    navController.navigate(R.id.action_postDetailFragment_to_userProfileFragment, bundle)
                }
                else -> {
                    // Fallback: send fragment result
                    parentFragmentManager.setFragmentResult(
                        "navigate_to_profile",
                        bundle
                    )
                }
            }
        } catch (e: Exception) {
            // Fallback to fragment result if NavController not accessible
            parentFragmentManager.setFragmentResult(
                "navigate_to_profile",
                Bundle().apply { putString("userId", userId) }
            )
        }
    }


    override fun onDismiss(dialog: DialogInterface) {
        val windowDecor = (dialog as? BottomSheetDialog)
            ?.window?.decorView as? ViewGroup
        if (::inputBar.isInitialized) windowDecor?.removeView(inputBar)
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::recyclerView.isInitialized) recyclerView.adapter = null
    }
}