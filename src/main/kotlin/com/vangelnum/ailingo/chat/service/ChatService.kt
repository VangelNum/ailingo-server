package com.vangelnum.ailingo.chat.service

import com.vangelnum.ailingo.chat.dto.ConversationDto
import com.vangelnum.ailingo.chat.model.ConversationMessage
import java.util.UUID

interface ChatService {

    fun startConversation(topicName: String): ConversationMessage

    fun continueDialog(chatId: UUID, userInput: String): ConversationMessage

    fun getMessages(chatId: UUID): List<ConversationMessage>

    fun getConversations(): List<ConversationDto>
}
