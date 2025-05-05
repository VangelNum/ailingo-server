package com.vangelnum.ailingo.chat.model

import java.time.Instant

data class ConversationSummary(
    val conversationId: String,
    val topicName: String,
    val topicImage: String?,
    val lastMessageTimestamp: Instant,
    val isCompleted: Boolean
)