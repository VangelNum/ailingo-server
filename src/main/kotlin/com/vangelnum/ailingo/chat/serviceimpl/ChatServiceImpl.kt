package com.vangelnum.ailingo.chat.serviceimpl

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.vangelnum.ailingo.chat.entity.HistoryMessageEntity
import com.vangelnum.ailingo.chat.model.AnalysisType
import com.vangelnum.ailingo.chat.model.ConversationMessage
import com.vangelnum.ailingo.chat.model.ConversationSummary
import com.vangelnum.ailingo.chat.model.MessageType
import com.vangelnum.ailingo.chat.model.TextAnalysisResult
import com.vangelnum.ailingo.chat.repository.MessageHistoryRepository
import com.vangelnum.ailingo.chat.service.ChatService
import com.vangelnum.ailingo.core.InvalidRequestException
import com.vangelnum.ailingo.topics.entity.TopicEntity
import com.vangelnum.ailingo.topics.repository.TopicRepository
import com.vangelnum.ailingo.user.service.UserService
import jakarta.transaction.Transactional
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.DefaultChatOptionsBuilder
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class ChatServiceImpl(
    private val baseChatClient: ChatClient,
    private val topicRepository: TopicRepository,
    private val historyRepository: MessageHistoryRepository,
    private val userService: UserService,
    private val objectMapper: ObjectMapper
) : ChatService {
    override fun startConversation(topicName: String): ConversationMessage {
        val topic: TopicEntity =
            topicRepository.findByName(topicName).orElseThrow { InvalidRequestException("Topic $topicName not found") }
        val user = userService.getCurrentUser()

        val conversationId = UUID.randomUUID()
        val initialMessageContent: String? = createMessage(topic, emptyList(), null)?.text

        if (initialMessageContent == null) {
            throw RuntimeException("Failed to generate Welcome Prompt.")
        }

        val historyMessage = HistoryMessageEntity(
            topic = topic,
            conversationId = conversationId,
            content = initialMessageContent,
            type = MessageType.SYSTEM,
            owner = user,
            timestamp = Instant.now()
        )

        val savedMessage = historyRepository.save(historyMessage)

        val suggestions = generateSuggestions(topic, listOf(mapHistoryMessageToMessage(savedMessage)))
        return mapHistoryMessageEntityToConversationMessageDto(savedMessage).apply {
            this.suggestions = suggestions
        }
    }

    override fun continueDialog(chatId: UUID, userInput: String): ConversationMessage {
        val user = userService.getCurrentUser()
        val messages = historyRepository.findByConversationIdAndOwnerOrderByTimestamp(chatId, user)

        if (messages.isEmpty()) {
            throw InvalidRequestException("Conversation not found or access denied.")
        }
        val topic = messages.first().topic

        if (messages.last().type == MessageType.FINAL) {
            return mapHistoryMessageEntityToConversationMessageDto(messages.last())
        }

        historyRepository.save(
            HistoryMessageEntity(
                topic = topic,
                conversationId = chatId,
                content = userInput,
                owner = user,
                type = MessageType.USER,
                timestamp = Instant.now()
            )
        )

        val updatedMessages = historyRepository.findByConversationIdAndOwnerOrderByTimestamp(chatId, user)
        val promptMessages = updatedMessages.map { mapHistoryMessageToMessage(it) }

        val savedAiMessage: HistoryMessageEntity = if (updatedMessages.size < topic.messageLimit) {
            val aiResponse = createMessage(topic, promptMessages, userInput)
            historyRepository.save(
                HistoryMessageEntity(
                    topic = topic,
                    conversationId = chatId,
                    content = aiResponse?.text,
                    owner = user,
                    type = MessageType.ASSISTANT,
                    timestamp = Instant.now()
                )
            )
        } else {
            val systemMessageToStop = SystemMessage(STOP_CONVERSATION_PROMPT)
            val finalAiResponse = createMessage(topic, promptMessages + systemMessageToStop, userInput)

            val finalHistoryMessage = historyRepository.save(
                HistoryMessageEntity(
                    topic = topic,
                    conversationId = chatId,
                    content = finalAiResponse?.text,
                    owner = user,
                    type = MessageType.FINAL,
                    timestamp = Instant.now()
                )
            )
            finalHistoryMessage
        }

        val updatedMessagesForSuggestion = historyRepository.findByConversationIdAndOwnerOrderByTimestamp(chatId, user)
        val suggestions =
            generateSuggestions(topic, updatedMessagesForSuggestion.map { mapHistoryMessageToMessage(it) })
        return mapHistoryMessageEntityToConversationMessageDto(savedAiMessage).apply {
            this.suggestions = suggestions
        }
    }


    override fun getMessages(chatId: UUID): List<ConversationMessage> {
        val user = userService.getCurrentUser()
        val messages = historyRepository.findByConversationIdAndOwnerOrderByTimestamp(chatId, user)
        if (messages.isEmpty()) {
            return emptyList()
        }
        return messages.map { mapHistoryMessageEntityToConversationMessageDto(it) }
    }

    private fun createMessage(
        topic: TopicEntity,
        messages: List<Message>,
        userInput: String? = null
    ): AssistantMessage? {
        val chatClient = baseChatClient.mutate()
            .defaultSystem(topic.systemPrompt)
            .defaultOptions(getOptions())
            .build()

        val promptToSend = if (userInput != null) {
            Prompt(messages + listOf(UserMessage(userInput)))
        } else {
            Prompt(listOf(SystemMessage(topic.welcomePrompt)))
        }

        return try {
            chatClient.prompt(promptToSend)
                .call()
                .chatResponse()
                ?.result
                ?.output
        } catch (e: Exception) {
            null
        }
    }

    private fun mapHistoryMessageToMessage(historyMessage: HistoryMessageEntity): Message {
        return when (historyMessage.type) {
            MessageType.SYSTEM -> SystemMessage(historyMessage.content ?: "")
            MessageType.USER -> UserMessage(historyMessage.content ?: "")
            MessageType.ASSISTANT -> AssistantMessage(historyMessage.content ?: "")
            MessageType.FINAL -> AssistantMessage(historyMessage.content ?: "")
        }
    }

    private fun generateSuggestions(topic: TopicEntity, conversationHistory: List<Message>): List<String> {
        val chatClient = baseChatClient.mutate()
            .defaultSystem("You are a helpful assistant that generates suggestions for user replies in a conversation. Provide only suggestions, do not answer as assistant.")
            .defaultOptions(
                DefaultChatOptionsBuilder()
                    .maxTokens(50)
                    .temperature(0.8)
                    .build()
            )
            .build()

        val suggestionPrompt = """
            Generate 3 very short suggested replies in English for the user to continue the conversation.
            Do not include any explanations or extra text. Do not add quotes to responses.
            Example suggestions can be questions or short phrases to continue the dialog based on the last message.
            Ensure that the generated suggestions are appropriate and relevant to the conversation topic.
            Last message was: ${conversationHistory.lastOrNull() ?: "Start of conversation"}
            Topic of conversation: ${topic.name}
            Suggestions:
        """.trimIndent()

        val promptToSend = Prompt(conversationHistory + listOf(SystemMessage(suggestionPrompt)))

        return try {
            val response = chatClient.prompt(promptToSend).call().content()
            response?.lines()?.mapNotNull { it.removePrefix("- ").trim().takeIf { it.isNotEmpty() } } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getOptions() = DefaultChatOptionsBuilder()
        .maxTokens(200)
        .temperature(0.7)
        .build()

    protected fun mapHistoryMessageEntityToConversationMessageDto(historyMessageEntity: HistoryMessageEntity): ConversationMessage {
        return ConversationMessage(
            historyMessageEntity.id?.toString() ?: "",
            historyMessageEntity.conversationId.toString(),
            historyMessageEntity.content,
            historyMessageEntity.timestamp,
            historyMessageEntity.type
        )
    }

    override fun getConversations(): List<ConversationSummary> {
        val user = userService.getCurrentUser()
        val latestMessages = historyRepository.findLatestMessagesByOwnerGroupedByConversationId(user)

        return latestMessages.groupBy { it.conversationId }
            .map { (_, messages) ->
                val latestMessage = messages.maxByOrNull { it.timestamp } ?: return@map null
                ConversationSummary(
                    conversationId = latestMessage.conversationId.toString(),
                    topicName = latestMessage.topic.name,
                    topicImage = latestMessage.topic.image,
                    lastMessageTimestamp = latestMessage.timestamp,
                    isCompleted = messages.any { it.type == MessageType.FINAL }
                )
            }
            .filterNotNull()
            .sortedByDescending { it.lastMessageTimestamp }
    }

    override fun singleMessageCheck(userInput: String): String? {
        if (userInput.isBlank()) {
            throw InvalidRequestException("User text for grammar check cannot be empty.")
        }

        val chatClient = baseChatClient.mutate()
            .defaultSystem(GRAMMAR_CHECK_SYSTEM_PROMPT)
            .defaultOptions(
                DefaultChatOptionsBuilder()
                    .maxTokens(200)
                    .temperature(0.3)
                    .build()
            )
            .build()

        val promptToSend = Prompt(userInput)

        return try {
            chatClient.prompt(promptToSend)
                .call()
                .content()
        } catch (e: Exception) {
            null
        }
    }


    override fun analyzeConversationBasicGrammar(conversationId: UUID): List<TextAnalysisResult> {
        return analyzeConversation(conversationId, BASIC_GRAMMAR_PROMPT, AnalysisType.BASIC_GRAMMAR)
    }

    override fun analyzeConversationCommonErrors(conversationId: UUID): List<TextAnalysisResult> {
        return analyzeConversation(conversationId, COMMON_BEGINNER_ERRORS_PROMPT, AnalysisType.BEGINNER_ERRORS)
    }

    override fun analyzeConversationClarityStyle(conversationId: UUID): List<TextAnalysisResult> {
        return analyzeConversation(conversationId, CLARITY_STYLE_PROMPT, AnalysisType.CLARITY_STYLE)
    }

    override fun analyzeConversationVocabulary(conversationId: UUID): List<TextAnalysisResult> {
        return analyzeConversation(conversationId, VOCABULARY_PHRASING_PROMPT, AnalysisType.VOCABULARY_PHRASING)
    }


    private fun analyzeConversation(conversationId: UUID, systemPrompt: String, analysisType: AnalysisType): List<TextAnalysisResult> {
        val user = userService.getCurrentUser()
        val messages = historyRepository.findByConversationIdAndOwnerOrderByTimestamp(conversationId, user)

        if (messages.isEmpty()) {
            throw InvalidRequestException("Conversation not found or access denied.")
        }

        val userMessagesToAnalyze = messages.filter { it.type == MessageType.USER && !it.content.isNullOrBlank() }

        if (userMessagesToAnalyze.isEmpty()) {
            return emptyList()
        }

        return analyzeMessagesWithAI(userMessagesToAnalyze, systemPrompt, analysisType)
    }

    private fun analyzeMessagesWithAI(
        userMessages: List<HistoryMessageEntity>,
        systemPrompt: String,
        analysisType: AnalysisType
    ): List<TextAnalysisResult> {
        val formattedMessages = userMessages.joinToString(separator = "\n---\n") { message ->
            "Message ID: ${message.id}\nText: ${message.content}"
        }

        val chatClient = baseChatClient.mutate()
            .defaultSystem(systemPrompt)
            .defaultOptions(
                DefaultChatOptionsBuilder()
                    .maxTokens(1000)
                    .temperature(0.1)
                    .build()
            )
            .build()

        val userPrompt = """
            Analyze the following user messages for ${analysisType.name.replace("_", " ")}.
            Identify issues based on the system prompt provided.
            Return a JSON array formatted exactly as a list of TextAnalysisResult objects.
            Each TextAnalysisResult object must correspond to one input message and include "messageId", "originalText", "analysisType".
            The "issues" field within each TextAnalysisResult should be a list of AnalysisIssue objects.
            Each AnalysisIssue object must have "type", "text" (the problematic word or short phrase), "description", and "suggestion".
            Do NOT include "startOffset" or "endOffset".
            Return ONLY the JSON array.

            Input Messages:
            $formattedMessages

            JSON Output (List<TextAnalysisResult>):
            """.trimIndent()

        return try {
            val promptToSend = Prompt(userPrompt)
            val aiResponse = chatClient.prompt(promptToSend).call().content()

            if (aiResponse != null && aiResponse.trim().startsWith("[")) {
                objectMapper.readValue<List<TextAnalysisResult>>(aiResponse)
            } else {
                println("AI did not return expected JSON format for analysis of type $analysisType. Response: '$aiResponse'")
                emptyList()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            println("Error during AI analysis for type $analysisType: ${e.message}")
            emptyList()
        }
    }

    companion object {
        const val STOP_CONVERSATION_PROMPT =
            "Politely inform the user that the conversation message limit for this topic has been reached and you must now conclude the discussion. Wish them well."

        const val GRAMMAR_CHECK_SYSTEM_PROMPT = """
            You are a helpful English grammar assistant. You will receive a sentence and your task is to correct it.
            Return the corrected sentence with a short explanation of why this is correct. If the sentence is already correct, return No mistakes.
        """

        const val BASIC_GRAMMAR_PROMPT = """
            You are an English language assistant focused on basic corrections (grammar, spelling, punctuation).
            Analyze the provided user messages, formatted as "Message ID: [id]\nText: [content]", and identify errors.
            Return a JSON array formatted exactly as a list of TextAnalysisResult objects.
            Each TextAnalysisResult object must contain "messageId", "originalText", "analysisType", and an "issues" list.
            Each issue object in the "issues" list must have "type" (e.g., "grammar", "spelling", "punctuation"), "text" (the problematic word or short phrase), "description" (explanation), and "suggestion" (correction).
            Do NOT include "startOffset" or "endOffset".
            Focus on common errors: verb tense, subject-verb agreement, articles (a/an/the), prepositions, basic sentence structure, common typos, missing commas/periods.
            Return ONLY the JSON array.
            """

        const val COMMON_BEGINNER_ERRORS_PROMPT = """
            You are an English language assistant helping beginners.
            Analyze the provided user messages, formatted as "Message ID: [id]\nText: [content]", and identify common mistakes made by language learners.
            Return a JSON array formatted exactly as a list of TextAnalysisResult objects.
            Each TextAnalysisResult object must contain "messageId", "originalText", "analysisType", and an "issues" list.
            Each issue object in the "issues" list must have "type" (e.g., "beginner-error"), "text" (the problematic word or short phrase), "description", and "suggestion".
            Do NOT include "startOffset" or "endOffset".
            Focus on errors like "I am agree", incorrect use of much/many, little/few, common prepositions in fixed phrases, simple modal verb errors, basic word order issues.
            Return ONLY the JSON array.
            """

        const val CLARITY_STYLE_PROMPT = """
            You are an English writing tutor focused on clarity and style. (PAID FEATURE)
            Analyze the provided user messages, formatted as "Message ID: [id]\nText: [content]", for awkward phrasing, repetitive sentence structures, sentences that are hard to follow, or lack of flow.
            Return a JSON array formatted exactly as a list of TextAnalysisResult objects.
            Each TextAnalysisResult object must contain "messageId", "originalText", "analysisType", and an "issues" list.
            Each issue object in the "issues" list must have "type" (e.g., "clarity", "structure"), "text" (the problematic word or short phrase/segment), "description" (explaining why it's awkward/unclear), and "suggestion" (improved phrasing).
            Do NOT include "startOffset" or "endOffset".
            Suggest alternative ways to phrase sentences to improve clarity and make the text sound more natural.
            Return ONLY the JSON array.
            """

        const val VOCABULARY_PHRASING_PROMPT = """
            You are an English language expert focused on vocabulary and natural phrasing. (PAID FEATURE)
            Analyze the provided user messages, formatted as "Message ID: [id]\nText: [content]", and suggest more precise, varied, or natural word choices and expressions (idioms, phrasal verbs, collocations).
            Return a JSON array formatted exactly as a list of TextAnalysisResult objects.
            Each TextAnalysisResult object must contain "messageId", "originalText", "analysisType", and an "issues" list.
            Each issue object in the "issues" list must have "type" (e.g., "vocabulary", "phrasing"), "text" (the problematic word or short phrase), "description" (explaining the suggestion), and "suggestion" (alternative word/phrase).
            Do NOT include "startOffset" or "endOffset".
            Explain why the suggested vocabulary/phrase is better or more appropriate.
            Return ONLY the JSON array.
            """
    }
}