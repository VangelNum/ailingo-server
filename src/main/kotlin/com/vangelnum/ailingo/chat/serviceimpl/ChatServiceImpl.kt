package com.vangelnum.ailingo.chat.serviceimpl

import com.vangelnum.ailingo.chat.dto.ConversationDto
import com.vangelnum.ailingo.chat.entity.HistoryMessageEntity
import com.vangelnum.ailingo.chat.model.ConversationMessage
import com.vangelnum.ailingo.chat.model.MessageType
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
    private val userService: UserService
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

    override fun getConversations(): MutableList<ConversationDto> {
        return historyRepository.findAllByOwner(userService.getCurrentUser())
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
            Generate 3 very short suggested replies for the user to continue the conversation.
            Do not include any explanations or extra text.
            Example suggestions can be questions or short phrases to continue the dialog based on the last message.
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

    companion object {
        const val STOP_CONVERSATION_PROMPT =
            "Politely inform the user that the conversation message limit for this topic has been reached and you must now conclude the discussion. Wish them well."
    }

    protected fun mapHistoryMessageEntityToConversationMessageDto(historyMessageEntity: HistoryMessageEntity): ConversationMessage {
        return ConversationMessage(
            historyMessageEntity.id?.toString() ?: "",
            historyMessageEntity.conversationId.toString(),
            historyMessageEntity.content,
            historyMessageEntity.timestamp,
            historyMessageEntity.type
        )
    }
}