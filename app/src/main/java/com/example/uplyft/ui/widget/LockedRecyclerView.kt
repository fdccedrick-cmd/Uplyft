package com.example.uplyft.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.view.NestedScrollingParent3
import androidx.core.view.NestedScrollingChild3
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior

class LockedRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : RecyclerView(context, attrs, defStyle) {

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
        type: Int
    ): Boolean {
        // ✅ scrolling DOWN (dy > 0) → block sheet from expanding
        return if (dy > 0) false
        else super.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        // ✅ when scrolling down stop nested scroll parent (BottomSheet)
        if (e.action == MotionEvent.ACTION_MOVE) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return super.onTouchEvent(e)
    }
}