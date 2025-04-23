package com.vangelnum.ailingo.chat.dto

import java.time.Instant
import java.util.UUID

data class ConversationSummaryDto(
    val conversationId: UUID,
    val topicName: String,
    val creationTimestamp: Instant
)