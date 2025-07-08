package com.example.triptogether

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.view.Gravity
import android.widget.LinearLayout


class ChatAdapter(private val messages: MutableList<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: TextView = itemView.findViewById(android.R.id.text1)
        val messageContainer: LinearLayout = itemView.findViewById(android.R.id.widget_frame)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val layout = if (viewType == 0) // Bot message
            R.layout.item_bot_message
        else // User message
            R.layout.item_user_message
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]
        holder.messageText.text = message.message

        // Set gravity and background based on message type
        holder.messageContainer.gravity = if (message.isUser) Gravity.END else Gravity.START
        holder.messageContainer.background = holder.itemView.context.getDrawable(
            if (message.isUser) R.drawable.user_message_background else R.drawable.ai_message_background
        )

        // Add padding inside the message bubble
        val padding = holder.itemView.context.resources.getDimensionPixelSize(R.dimen.message_padding)
        holder.messageText.setPadding(padding, padding, padding, padding)

        // Optional: Add a slight elevation for a card-like effect
        holder.messageContainer.elevation = 4f
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) 1 else 0 // 0 for bot, 1 for user
    }

    override fun getItemCount(): Int = messages.size

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
}