package org.openaustria.googletv.hermes

import android.view.Gravity
import android.view.KeyEvent
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
 * durchblättern kann; Nutzer-Nachrichten stehen rechts, Agent-Antworten links. OK auf einer nicht
 * gesendeten Nachricht ruft [onRetry] auf.
 */
class ChatAdapter(
    private val onRetry: (ChatMessage) -> Unit,
) : ListAdapter<ChatMessage, ChatAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onRetry)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val bubble: View = itemView.findViewById(R.id.chat_bubble)
        private val sender: TextView = itemView.findViewById(R.id.chat_sender)
        private val text: TextView = itemView.findViewById(R.id.chat_text)

        init {
            itemView.setOnKeyListener { row, keyCode, event -> scrollWithinRow(row, keyCode, event) }
        }

        fun bind(message: ChatMessage, onRetry: (ChatMessage) -> Unit) {
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
            if (message.status == MessageStatus.FAILED) {
                itemView.setOnClickListener { onRetry(message) }
            } else {
                itemView.setOnClickListener(null)
                itemView.isClickable = false
            }
        }

        /**
         * D-Pad hoch/runter in einer Zeile, die über den sichtbaren Verlauf hinausragt: erst seitenweise
         * durch die Zeile scrollen, dann springt der Fokus weiter. Sonst bliebe das Ende langer Antworten
         * unerreichbar — besonders bei der neuesten, unter der keine Zeile mehr folgt.
         */
        private fun scrollWithinRow(row: View, keyCode: Int, event: KeyEvent): Boolean {
            val down = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> true
                KeyEvent.KEYCODE_DPAD_UP -> false
                else -> return false
            }
            val list = row.parent as? RecyclerView ?: return false
            val amount = rowScrollAmount(row.top, row.bottom, list.paddingTop, list.height - list.paddingBottom, down)
            if (amount == 0) return false
            if (event.action == KeyEvent.ACTION_DOWN) list.smoothScrollBy(0, amount)
            return true
        }
    }

    private object Diff : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage) = oldItem == newItem
    }
}

/**
 * Scrollweg für D-Pad hoch ([down] = false) oder runter in einer Zeile von [rowTop] bis [rowBottom],
 * wenn [visibleTop] bis [visibleBottom] sichtbar ist. 0, sobald die Zeile in dieser Richtung ganz
 * sichtbar ist — dann übernimmt die normale Fokus-Navigation. Eine Seite ist drei Viertel der
 * sichtbaren Höhe, damit beim Blättern etwas Text als Anschluss stehen bleibt.
 */
internal fun rowScrollAmount(rowTop: Int, rowBottom: Int, visibleTop: Int, visibleBottom: Int, down: Boolean): Int {
    val page = ((visibleBottom - visibleTop) * 3 / 4).coerceAtLeast(1)
    return if (down) {
        minOf(rowBottom - visibleBottom, page).coerceAtLeast(0)
    } else {
        -(minOf(visibleTop - rowTop, page).coerceAtLeast(0))
    }
}
