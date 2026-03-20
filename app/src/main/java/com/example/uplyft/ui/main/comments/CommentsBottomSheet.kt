package com.example.uplyft.ui.main.comments

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.example.uplyft.domain.model.Comment
import com.example.uplyft.ui.adapter.CommentAdapter
import com.example.uplyft.ui.widget.LockedRecyclerView
import com.example.uplyft.utils.Resource
import com.example.uplyft.viewmodel.CommentViewModel
import com.google.android.material.bottomsheet.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CommentsBottomSheet : BottomSheetDialogFragment() {

    private val viewModel: CommentViewModel by activityViewModels()

    private lateinit var adapter     : CommentAdapter
    private lateinit var recyclerView: LockedRecyclerView
    private lateinit var inputBar    : View
    private lateinit var etComment   : EditText
    private lateinit var tvPost      : TextView
    private lateinit var layoutReply : LinearLayout
    private lateinit var tvReplyingTo: TextView
    private lateinit var emptyState  : LinearLayout

    private var sheetBehavior  : BottomSheetBehavior<View>? = null
    private var replyingTo     : Comment? = null
    private var postId         : String?  = null
    private var currentUid     : String?  = null
    private var currentUsername: String   = ""

    companion object {
        const val TAG = "CommentsBottomSheet"
        fun newInstance(postId: String) = CommentsBottomSheet().apply {
            arguments = Bundle().apply { putString("postId", postId) }
        }
    }

    override fun getTheme() = R.style.BottomSheetRoundedTheme

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return (super.onCreateDialog(savedInstanceState) as BottomSheetDialog).also { dialog ->
            dialog.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
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

        view.post {
            setupSheet()
            setupRecycler()
            observeData()
            loadUser()
            postId?.let { viewModel.loadComments(it) }
        }
    }

    // ─────────────────────────────────────────────
    // SHEET + INPUT
    // ─────────────────────────────────────────────

    private fun setupSheet() {
        val dialog      = dialog as? BottomSheetDialog ?: return
        val sheet       = dialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        val windowDecor = dialog.window?.decorView as? ViewGroup ?: return

        sheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        sheet.requestLayout()

        val screenH = resources.displayMetrics.heightPixels

        sheetBehavior = BottomSheetBehavior.from(sheet).apply {
            peekHeight    = (screenH * 0.65).toInt()
            maxHeight     = screenH
            state         = BottomSheetBehavior.STATE_COLLAPSED
            skipCollapsed = false
            isHideable    = true
            isDraggable   = true
        }

        // ✅ inflate input into dialog window — independent of sheet scroll
        inputBar = LayoutInflater.from(requireContext())
            .inflate(R.layout.layout_comment_input, windowDecor, false)

        etComment    = inputBar.findViewById(R.id.etComment)
        tvPost       = inputBar.findViewById(R.id.tvPost)
        layoutReply  = inputBar.findViewById(R.id.layoutReplyIndicator)
        tvReplyingTo = inputBar.findViewById(R.id.tvReplyingTo)

        val inputParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM }

        windowDecor.addView(inputBar, inputParams)

        // ✅ after inputBar is drawn — set recycler padding to exact height
        inputBar.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    val inputHeight = inputBar.height
                    if (inputHeight > 0) {
                        recyclerView.setPadding(0, 0, 0, inputHeight)
                        recyclerView.clipToPadding = false
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

        // ✅ keyboard insets — input slides above keyboard
        ViewCompat.setOnApplyWindowInsetsListener(inputBar) { v, insets ->
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navHeight = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            ).bottom

            // fill gap behind nav bar
            inputBar.findViewById<View>(R.id.navBarSpacer)?.apply {
                layoutParams.height = navHeight
                requestLayout()
            }

            // move input above keyboard when open
            (v.layoutParams as FrameLayout.LayoutParams).bottomMargin =
                if (imeHeight > 0) imeHeight else 0
            v.requestLayout()

            // update recycler padding when keyboard state changes
            v.post {
                val inputHeight = v.height
                if (inputHeight > 0) {
                    val pad = if (imeHeight > 0) {
                        inputHeight - navHeight
                    } else {
                        inputHeight
                    }
                    recyclerView.setPadding(0, 0, 0, pad)
                    recyclerView.clipToPadding = false
                }
            }

            insets
        }

        ViewCompat.requestApplyInsets(inputBar)

        setupDragHandle()
        setupInput()
        loadCurrentUserAvatar()
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
        inputBar.findViewById<ImageView>(R.id.ivCancelReply).setOnClickListener {
            replyingTo             = null
            layoutReply.visibility = View.GONE
            etComment.hint         = "Add a comment..."
        }

        etComment.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                val has          = !s.isNullOrBlank()
                tvPost.isEnabled = has
                tvPost.alpha     = if (has) 1f else 0.4f
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
            val parentId = replyingTo?.commentId ?: ""
            postId?.let { viewModel.addComment(it, text, parentId) }
            replyingTo             = null
            layoutReply.visibility = View.GONE
            etComment.hint         = "Add a comment..."
            etComment.text.clear()
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
            onReply         = { comment ->
                replyingTo             = comment
                layoutReply.visibility = View.VISIBLE
                tvReplyingTo.text      = "Replying to @${comment.username}"
                focusKeyboard()
            },
            onLike          = { viewModel.toggleCommentLike(it) },
            onAvatar        = { dismiss() },
            onUsername      = { navigateToProfile(it) }
        )

        recyclerView.apply {
            layoutManager            = LinearLayoutManager(requireContext())
            adapter                  = this@CommentsBottomSheet.adapter
            isNestedScrollingEnabled = true
            overScrollMode           = View.OVER_SCROLL_NEVER
            clipToPadding            = false
        }
    }

    // ─────────────────────────────────────────────
    // OBSERVE
    // ─────────────────────────────────────────────

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.comments.collect { list ->
                        adapter.submitFullList(list)
                        val topLevel = list.filter { it.parentId.isEmpty() }
                        emptyState.visibility   = if (topLevel.isEmpty()) View.VISIBLE else View.GONE
                        recyclerView.visibility = View.VISIBLE
                        if (topLevel.isNotEmpty()) {
                            recyclerView.post {
                                recyclerView.post {
                                    recyclerView.scrollToPosition(adapter.itemCount - 1)
                                }
                            }
                        }
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
                dismiss()
                parentFragmentManager.setFragmentResult(
                    "navigate_to_profile",
                    Bundle().apply { putString("userId", uid) }
                )
            }
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