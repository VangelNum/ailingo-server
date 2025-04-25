package com.vangelnum.ailingo.chat.dto

import java.time.Instant
import java.util.UUID

interface ConversationDto {
    val conversationId: UUID
    val topicName: String
    val creationTimestamp: Instant
}