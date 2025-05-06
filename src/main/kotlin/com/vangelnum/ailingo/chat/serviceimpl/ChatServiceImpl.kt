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
import com.vangelnum.ailingo.core.InsufficientFundsException
import com.vangelnum.ailingo.core.InvalidRequestException
import com.vangelnum.ailingo.textanalysis.service.TextAnalysisService
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
    private val objectMapper: ObjectMapper,
    private val textAnalysisService: TextAnalysisService
) : ChatService {
    override fun startConversation(topicName: String): ConversationMessage {
        val topic: TopicEntity =
            topicRepository.findByName(topicName).orElseThrow { InvalidRequestException("Topic $topicName not found") }
        val user = userService.getCurrentUser()

        try {
            userService.changeCoins(-topic.price)
        } catch (e: Exception) {
            throw e
        }

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

    override fun startCustomConversation(topicIdea: String): ConversationMessage {
        val user = userService.getCurrentUser()

        try {
            userService.changeCoins(-200)
        } catch (e: Exception) {
            throw InsufficientFundsException("Not enough coins")
        }

        val conversationId = UUID.randomUUID()

        val prompts = generatePromptsForCustomTopic(topicIdea)
        val welcomePrompt = prompts["welcomePrompt"] ?: throw RuntimeException("Failed to generate Welcome Prompt.")
        val systemPrompt = prompts["systemPrompt"] ?: throw RuntimeException("Failed to generate System Prompt.")

        val customTopic = TopicEntity(
            name = topicIdea,
            image = null,
            price = 20,
            level = 0,
            welcomePrompt = welcomePrompt,
            systemPrompt = systemPrompt,
            messageLimit = 20,
            xpCompleteTopic = 0,
            id = 0,
            creator = user
        )

        val savedTopic = topicRepository.save(customTopic)

        val initialMessageContent: String? = createMessage(savedTopic, emptyList(), null)?.text

        if (initialMessageContent == null) {
            throw RuntimeException("Failed to generate initial bot message.")
        }

        val historyMessage = HistoryMessageEntity(
            topic = savedTopic,
            conversationId = conversationId,
            content = initialMessageContent,
            type = MessageType.SYSTEM,
            owner = user,
            timestamp = Instant.now()
        )

        val savedMessage = historyRepository.save(historyMessage)

        val messagesForSuggestion = historyRepository.findByConversationIdAndOwnerOrderByTimestamp(conversationId, user)
        val suggestions = generateSuggestions(
            savedTopic,
            messagesForSuggestion.map { mapHistoryMessageToMessage(it) }) // Используем savedTopic

        return mapHistoryMessageEntityToConversationMessageDto(savedMessage).apply {
            this.suggestions = suggestions
        }
    }

    private fun generatePromptsForCustomTopic(topicIdea: String): Map<String, String> {
        val chatClient = baseChatClient.mutate()
            .defaultSystem("You are a helpful assistant that creates initial prompts for a conversation topic, designed for English language learning.")
            .defaultOptions(
                DefaultChatOptionsBuilder()
                    .maxTokens(500)
                    .temperature(0.7)
                    .build()
            )
            .build()

        val prompt = """
            Based on the topic idea: "$topicIdea", generate a concise and engaging welcome prompt and a detailed system prompt for an English language learning chatbot.
            The chatbot will help users practice speaking and writing English on this specific topic.
            
            **Instructions for the Chatbot:**
            *   Communicate ONLY in English. Never switch languages.
            *   Strictly adhere to the given topic and do not deviate.
            *   Maintain a friendly and encouraging tone suitable for language learners.
            *   Focus on natural conversation but be prepared to clarify or rephrase if the user struggles.
            *   Do NOT act as a grammar checker during the main conversation (analysis features are separate).
            *   The system prompt should guide the AI's behavior throughout the conversation.
        
            Generate:
            1.  **Welcome Prompt:** A brief (1-2 sentences), engaging introduction to the topic for the user, designed to be the *first message the user sees*. (e.g., "Welcome! Let's practice English by talking about...").
            2.  **System Prompt:** Detailed instructions for the chatbot's behavior *during the conversation*, incorporating the points above (English only, topic adherence, tone, etc.). This prompt will be used to guide the AI's responses to user messages.
        
            Return the prompts in JSON format:
            {
              "welcomePrompt": "...",
              "systemPrompt": "..."
            }
            Return ONLY the JSON object.
            """.trimIndent()

        return try {
            val response = chatClient.prompt(prompt).call().content()
            if (response != null) {
                objectMapper.readValue(response)
            } else {
                mapOf(
                    "welcomePrompt" to "Hello! Let's talk about $topicIdea to practice your English.",
                    "systemPrompt" to "You are a helpful chatbot that talks about the topic: $topicIdea.  You MUST ONLY communicate in English. Focus on assisting English language learners."
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            mapOf(
                "welcomePrompt" to "Hello! Let's talk about $topicIdea to practice your English.",
                "systemPrompt" to "You are a helpful chatbot that talks about the topic: $topicIdea.  You MUST ONLY communicate in English. Focus on assisting English language learners."
            )
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

            userService.checkAndGrantTopicAchievements(user)

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

        val lastMessageContent = conversationHistory.lastOrNull() ?: "Start of conversation"

        val suggestionPrompt = """
        You are an assistant generating conversational suggestions for an English language learner.
        The current conversation topic is: "${topic.name}".
        The last message was: "$lastMessageContent"

        **Instructions:**
        1.  Generate exactly 3 distinct suggested replies for the user.
        2.  Each suggestion should be very short (ideally 3-8 words).
        3.  Suggestions should be natural, relevant continuations of the conversation based on the last message and topic.
        4.  Offer variety: suggest a question, a short statement, or a way to express agreement/disagreement/interest, if appropriate.
        5.  Ensure suggestions make logical sense following the last message. (e.g., don't suggest asking a question if the last message was already a question from the bot).
        6.  Language: English only.
        7.  **Output Format:** Return *only* the 3 suggestions, each on a new line. Do not include *any* prefixes (like '-', '*'), numbering, quotes, explanations, or introductory text.

        **Example Output:**
        Okay, tell me more.
        What happened next?
        I disagree with that.

        Generate the 3 suggestions now:
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
        return textAnalysisService.singleMessageCheck(userInput)
    }

    override fun analyzeConversationBasicGrammar(conversationId: UUID): List<TextAnalysisResult> {
        return textAnalysisService.analyzeConversation(conversationId, AnalysisType.BASIC_GRAMMAR)
    }

    override fun analyzeConversationCommonErrors(conversationId: UUID): List<TextAnalysisResult> {
        return textAnalysisService.analyzeConversation(conversationId, AnalysisType.BEGINNER_ERRORS)
    }

    override fun analyzeConversationClarityStyle(conversationId: UUID): List<TextAnalysisResult> {
        userService.changeCoins(-20)
        return textAnalysisService.analyzeConversation(conversationId, AnalysisType.CLARITY_STYLE)
    }

    override fun analyzeConversationVocabulary(conversationId: UUID): List<TextAnalysisResult> {
        userService.changeCoins(-20)
        return textAnalysisService.analyzeConversation(conversationId, AnalysisType.VOCABULARY_PHRASING)
    }

    companion object {
        const val STOP_CONVERSATION_PROMPT =
            "Act as the assistant. The conversation for this topic has reached its message limit. Inform the user politely and clearly that the limit is reached and you cannot continue the conversation further. Conclude the chat and wish them well. This is your final response."

    }
}