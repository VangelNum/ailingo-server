package com.vangelnum.ailingo.chat.service

import com.vangelnum.ailingo.chat.model.ConversationMessage
import com.vangelnum.ailingo.chat.model.ConversationSummary
import com.vangelnum.ailingo.chat.model.TextAnalysisResult
import java.util.UUID

interface ChatService {

    fun startConversation(topicName: String): ConversationMessage

    fun continueDialog(chatId: UUID, userInput: String): ConversationMessage

    fun getMessages(chatId: UUID): List<ConversationMessage>

    fun getConversations(): List<ConversationSummary>

    fun singleMessageCheck(userInput: String): String?

    fun analyzeConversationBasicGrammar(conversationId: UUID): List<TextAnalysisResult>
    fun analyzeConversationCommonErrors(conversationId: UUID): List<TextAnalysisResult>
    fun analyzeConversationClarityStyle(conversationId: UUID): List<TextAnalysisResult>
    fun analyzeConversationVocabulary(conversationId: UUID): List<TextAnalysisResult>
}