package org.openaustria.googletv.hermes

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.openaustria.googletv.R

/**
 * Zeilen des Chat-Verlaufs. Jede Zeile ist fokussierbar, damit man den Verlauf per D-Pad
 * durchblättern kann; Nutzer-Nachrichten stehen rechts, Agent-Antworten links.
 */
class ChatAdapter : ListAdapter<ChatMessage, ChatAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val bubble: View = itemView.findViewById(R.id.chat_bubble)
        private val sender: TextView = itemView.findViewById(R.id.chat_sender)
        private val text: TextView = itemView.findViewById(R.id.chat_text)

        fun bind(message: ChatMessage) {
            val isUser = message.role == ChatRole.USER
            (bubble.layoutParams as FrameLayout.LayoutParams).gravity = if (isUser) Gravity.END else Gravity.START
            bubble.requestLayout()
            bubble.setBackgroundResource(if (isUser) R.drawable.bg_chat_user else R.drawable.bg_chat_agent)
            sender.setText(
                when {
                    !isUser -> R.string.chat_sender_agent
                    message.status == MessageStatus.PENDING -> R.string.chat_sender_user_pending
                    message.status == MessageStatus.FAILED -> R.string.chat_sender_user_failed
                    else -> R.string.chat_sender_user
                }
            )
            text.text = message.text
        }
    }

    private object Diff : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage) = oldItem == newItem
    }
}
