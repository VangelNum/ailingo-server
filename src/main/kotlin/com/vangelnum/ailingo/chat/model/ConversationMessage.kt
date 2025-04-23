package com.vangelnum.ailingo.chat.model

import java.time.Instant

data class ConversationMessage(
    val id: String,
    val conversationId: String,
    val content: String?,
    val timestamp: Instant,
    val type: MessageType,
    var suggestions: List<String>? = null
)