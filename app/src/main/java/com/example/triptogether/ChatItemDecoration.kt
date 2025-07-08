package com.example.triptogether

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class ChatItemDecoration(
    private val userSpacing: Int,  // Spacing below user messages
    private val aiSpacing: Int,    // Spacing below AI messages
    private val messages: List<ChatMessage>  // Reference to the message list
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return

        // Apply spacing based on message type, except for the last item
        if (position < parent.adapter!!.itemCount - 1) {
            outRect.bottom = if (messages[position].isUser) userSpacing else aiSpacing
        }

        // Optional: Add horizontal padding for narrower messages
        outRect.left = userSpacing / 2
        outRect.right = userSpacing / 2
    }
}