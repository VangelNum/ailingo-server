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
            userService.changeCoins(-20)
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
        Based on the user's topic idea: "$topicIdea", generate two prompts for an English language learning chatbot: a "welcomePrompt" and a "systemPrompt".

        **Instructions:**
        1.  **Target Audience:** The chatbot is for English language learners (assume intermediate level unless the topic suggests otherwise).
        2.  **Chatbot Role:** The chatbot should act as a friendly, patient, and knowledgeable conversation partner or tutor focused *only* on the topic "$topicIdea".
        3.  **Language:** The chatbot MUST communicate *exclusively* in English. Never use any other language.
        4.  **Topic Focus:** The chatbot must strictly adhere to the topic "$topicIdea" and gently guide the conversation back if the user strays too far.
        5.  **Welcome Prompt:**
            *   Should be concise (1-2 sentences), engaging, friendly, and clearly introduce the topic.
            *   Should invite the user to start the conversation.
            *   Example structure: "Hi there! Ready to practice your English by discussing [brief topic paraphrase]? What are your first thoughts on it?"
        6.  **System Prompt:**
            *   This is a *hidden* instruction for the AI model defining its core behavior.
            *   It must clearly define the chatbot's persona (friendly tutor), knowledge base (focused on "$topicIdea"), constraints (English-only, stay on topic), and primary goal (help user practice English conversation related to the topic).
            *   Include specific instructions like: "Politely correct major user errors if it doesn't disrupt the flow. Ask open-ended questions to encourage conversation. Maintain a positive and supportive tone."
            *   Explicitly state: "You must *only* speak English. You must *only* discuss '$topicIdea'."
        7.  **Output Format:** Return *ONLY* a valid JSON object containing the two prompts as strings. Do not include *any* introductory text, explanations, or markdown formatting.

        **JSON Output Structure:**
        ```json
        {
          "welcomePrompt": "...",
          "systemPrompt": "..."
        }
        ```

        Generate the JSON now based on the topic: "$topicIdea".
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
        // Optional: Get the type of the last message to tailor suggestions
        // val lastMessageType = conversationHistory.lastOrNull()?.messageType

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
            response?.lines()
                ?.mapNotNull { it.trim().takeIf { line -> line.isNotEmpty() } }
                ?.take(3)
                ?: emptyList()
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


    private fun analyzeConversation(
        conversationId: UUID,
        systemPrompt: String,
        analysisType: AnalysisType
    ): List<TextAnalysisResult> {
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
        You are an expert English grammar assistant. Your task is to analyze a single sentence provided by the user and correct any grammatical errors.

        **Instructions:**
        1.  Carefully review the user's sentence for grammar, punctuation, and spelling mistakes.
        2.  If errors are found:
            *   Return the fully corrected sentence on the first line.
            *   On the second line, provide a *brief* (1-sentence) explanation focusing on the *main* correction made (e.g., "Corrected verb tense from present simple to past simple," or "Added missing article 'the'").
        3.  If the original sentence is grammatically correct and natural-sounding:
            *   Return *only* the exact text: `No mistakes found.`

        **Output Format:**
        *   **If incorrect:**
            [Corrected Sentence]
            [Brief Explanation]
        *   **If correct:**
            No mistakes.

        Analyze the user's input now.
        """

        const val BASIC_GRAMMAR_PROMPT = """
        You are an English language analysis assistant specialized in identifying basic grammatical errors (grammar, spelling, punctuation).
        You will receive a batch of user messages, each tagged with a "Message ID".
        Your task is to analyze *each* message individually based on the specified focus and return the findings *only* as a single, valid JSON array conforming *exactly* to the List<TextAnalysisResult> structure.
    
        **Analysis Focus (Basic Grammar):**
        *   Verb tense errors
        *   Subject-verb agreement issues
        *   Incorrect use of articles (a/an/the)
        *   Preposition mistakes
        *   Basic sentence structure errors (e.g., run-on sentences, fragments)
        *   Common spelling mistakes (typos)
        *   Punctuation errors (missing commas, periods, question marks)
    
        **Input Format:**
        The input will be multiple messages formatted like this:
        Message ID: [UUID]
        Text: [User's message content]
        ---
        Message ID: [UUID]
        Text: [User's message content]
    
        **Output Requirements:**
        1.  **CRITICAL:** Your *entire* response MUST be a single valid JSON array `[...]`. No introductory text, no explanations, no markdown backticks, nothing before `[` or after `]`.
        2.  The array must contain one JSON object for *each* input message, representing a `TextAnalysisResult`.
        3.  Each `TextAnalysisResult` object MUST have these fields:
            *   `messageId`: (String) The UUID of the message being analyzed.
            *   `originalText`: (String) The original text of the user message.
            *   `analysisType`: (String) The type of analysis performed (use "BASIC_GRAMMAR" for this prompt).
            *   `issues`: (Array<AnalysisIssue>) A list of issues found in this message.
        4.  **If a message has NO issues** of the specified type, the `issues` array for that message MUST be empty (`[]`). Do *not* omit the message object.
        5.  Each `AnalysisIssue` object within the `issues` array MUST have these fields:
            *   `type`: (String) The category of the error (e.g., "grammar", "spelling", "punctuation").
            *   `text`: (String) The *specific* incorrect word or short phrase (max ~5 words) from the original text.
            *   `description`: (String) A brief explanation of *why* it's an issue.
            *   `suggestion`: (String) The suggested correction for the identified "text".
        6.  Do NOT include `startOffset` or `endOffset` fields in the `AnalysisIssue` object.
    
        **Example JSON Output Structure (Illustrative):**
        ```json
        [
          {
            "messageId": "123e4567-e89b-12d3-a456-426614174000",
            "originalText": "I goed to the store yesterday.",
            "analysisType": "BASIC_GRAMMAR",
            "issues": [
              {
                "type": "grammar",
                "text": "goed",
                "description": "Incorrect past tense form of 'go'.",
                "suggestion": "went"
              }
            ]
          },
          {
            "messageId": "abcdef01-e89b-12d3-a456-426614174001",
            "originalText": "This sentence is correct.",
            "analysisType": "BASIC_GRAMMAR",
            "issues": [] // Empty array as no issues were found
          }
        ]
        ```
    
        Now, analyze the following user messages and return ONLY the JSON array:
    
        [Input Messages will be appended here by the code]
        """

        const val COMMON_BEGINNER_ERRORS_PROMPT = """
            You are an English language analysis assistant specialized in identifying common errors frequently made by beginner to intermediate language learners.
            You will receive a batch of user messages, each tagged with a "Message ID".
            Your task is to analyze *each* message individually based on the specified focus and return the findings *only* as a single, valid JSON array conforming *exactly* to the List<TextAnalysisResult> structure.
            
            **Analysis Focus (Common Beginner Errors):**
            *   **Verb Issues:** Incorrect tense usage (e.g., `I go yesterday`), subject-verb agreement (`He don't like`), incorrect modal verb forms or usage (`I must to go`).
            *   **Articles:** Missing or incorrect use of a/an/the.
            *   **Prepositions:** Common errors with in/on/at, to/for, etc., especially in fixed phrases.
            *   **Nouns/Pronouns:** Pluralization errors (`informations`), incorrect pronoun case (`Me went`).
            *   **Adjectives/Adverbs:** Confusion between forms (`He speaks slow` instead of `slowly`).
            *   **Quantifiers:** Incorrect use of much/many, little/few, some/any.
            *   **Word Choice:** Common confusions (e.g., make/do, say/tell, borrow/lend).
            *   **Basic Sentence Structure:** Incorrect word order, missing subjects/verbs, run-on sentences using simple conjunctions.
            *   **Common Incorrect Phrases:** Such as "I am agree", "depend on the context" (missing 'it'), "more better".
            
            **Input Format:**
            The input will be multiple messages formatted like this:
            Message ID: [UUID]
            Text: [User's message content]
            ---
            Message ID: [UUID]
            Text: [User's message content]
            
            **Output Requirements:**
            1.  **CRITICAL:** Your *entire* response MUST be a single valid JSON array `[...]`. No introductory text, no explanations, no markdown backticks, nothing before `[` or after `]`.
            2.  The array must contain one JSON object for *each* input message, representing a `TextAnalysisResult`.
            3.  Each `TextAnalysisResult` object MUST have these fields:
                *   `messageId`: (String) The UUID of the message being analyzed.
                *   `originalText`: (String) The original text of the user message.
                *   `analysisType`: (String) The type of analysis performed (use **"BEGINNER_ERRORS"** for this prompt).
                *   `issues`: (Array<AnalysisIssue>) A list of issues found in this message.
            4.  **If a message has NO issues** of the specified type, the `issues` array for that message MUST be empty (`[]`). Do *not* omit the message object.
            5.  Each `AnalysisIssue` object within the `issues` array MUST have these fields:
                *   `type`: (String) Use **"common-error"** for all issues identified by this prompt.
                *   `text`: (String) The *specific* incorrect word or short phrase (max ~5 words) from the original text.
                *   `description`: (String) A brief explanation of *why* it's a common error for learners.
                *   `suggestion`: (String) The suggested correction for the identified "text".
            6.  Do NOT include `startOffset` or `endOffset` fields in the `AnalysisIssue` object.
            
            **Example JSON Output Structure (Illustrative):**
            ```json
            [
              {
                "messageId": "123e4567-e89b-12d3-a456-426614174000",
                "originalText": "I am agree with you.",
                "analysisType": "BEGINNER_ERRORS",
                "issues": [
                  {
                    "type": "common-error",
                    "text": "am agree",
                    "description": "'Agree' is a verb, so it doesn't need 'am'. This is a common error.",
                    "suggestion": "agree"
                  }
                ]
              },
              {
                "messageId": "abcdef01-e89b-12d3-a456-426614174001",
                "originalText": "He plays football well.",
                "analysisType": "BEGINNER_ERRORS",
                "issues": [] // Empty array as no common beginner issues were found
              }
            ]
            """

        const val CLARITY_STYLE_PROMPT = """
        You are an English writing analysis assistant focused on improving clarity, style, and natural flow, potentially for more advanced learners.
        You will receive a batch of user messages, each tagged with a "Message ID".
        Your task is to analyze *each* message individually based on the specified focus and return the findings *only* as a single, valid JSON array conforming *exactly* to the List<TextAnalysisResult> structure.
        
        **Analysis Focus (Clarity & Style):**
        *   **Awkward Phrasing:** Sentences or parts of sentences that sound unnatural or are difficult to understand.
        *   **Wordiness/Redundancy:** Using more words than necessary to express an idea.
        *   **Vagueness:** Lack of specific detail where it would improve understanding.
        *   **Repetitive Sentence Structure:** Starting sentences similarly or using the same grammatical patterns too often.
        *   **Flow and Cohesion:** Lack of smooth transitions between ideas or sentences.
        *   **Passive Voice Overuse:** Using passive voice when active voice would be clearer and more direct (judge contextually).
        *   **Ambiguity:** Sentences that could be interpreted in more than one way.
        
        **Input Format:**
        The input will be multiple messages formatted like this:
        Message ID: [UUID]
        Text: [User's message content]
        ---
        Message ID: [UUID]
        Text: [User's message content]
        
        **Output Requirements:**
        1.  **CRITICAL:** Your *entire* response MUST be a single valid JSON array `[...]`. No introductory text, no explanations, no markdown backticks, nothing before `[` or after `]`.
        2.  The array must contain one JSON object for *each* input message, representing a `TextAnalysisResult`.
        3.  Each `TextAnalysisResult` object MUST have these fields:
            *   `messageId`: (String) The UUID of the message being analyzed.
            *   `originalText`: (String) The original text of the user message.
            *   `analysisType`: (String) The type of analysis performed (use **"CLARITY_STYLE"** for this prompt).
            *   `issues`: (Array<AnalysisIssue>) A list of issues found in this message.
        4.  **If a message has NO issues** of the specified type, the `issues` array for that message MUST be empty (`[]`). Do *not* omit the message object.
        5.  Each `AnalysisIssue` object within the `issues` array MUST have these fields:
            *   `type`: (String) Use **"clarity"** or **"style"** based on the nature of the issue.
            *   `text`: (String) The *specific* awkward or problematic phrase or sentence segment (can be longer than a few words for style issues).
            *   `description`: (String) A brief explanation of *why* the identified text affects clarity or style (e.g., "awkward phrasing", "wordy", "repetitive structure").
            *   `suggestion`: (String) An alternative phrasing or way to structure the sentence to improve it.
        6.  Do NOT include `startOffset` or `endOffset` fields in the `AnalysisIssue` object.
        
        **Example JSON Output Structure (Illustrative):**
        ```json
        [
          {
            "messageId": "123e4567-e89b-12d3-a456-426614174000",
            "originalText": "The report was written by me, and it was submitted then.",
            "analysisType": "CLARITY_STYLE",
            "issues": [
              {
                "type": "style",
                "text": "The report was written by me, and it was submitted then.",
                "description": "Uses passive voice twice and 'then' is slightly vague. Active voice is more direct.",
                "suggestion": "I wrote the report and then submitted it."
              }
            ]
          },
          {
            "messageId": "abcdef01-e89b-12d3-a456-426614174001",
            "originalText": "The meeting was very productive.",
            "analysisType": "CLARITY_STYLE",
            "issues": [] // Empty array as no major clarity/style issues were found
          }
        ]
            """

        const val VOCABULARY_PHRASING_PROMPT = """
        You are an English language expert focused on improving vocabulary usage and natural phrasing, suitable for intermediate to advanced learners.
        You will receive a batch of user messages, each tagged with a "Message ID".
        Your task is to analyze *each* message individually based on the specified focus and return the findings *only* as a single, valid JSON array conforming *exactly* to the List<TextAnalysisResult> structure.
        
        **Analysis Focus (Vocabulary & Phrasing):**
        *   **Word Choice:** Identifying basic or repetitive vocabulary where more precise, descriptive, or varied words could be used (e.g., replacing 'good'/'bad'/'nice' with stronger alternatives).
        *   **Collocations:** Spotting unnatural word combinations (e.g., 'make homework' instead of 'do homework', 'strong rain' instead of 'heavy rain').
        *   **Idiomatic Language:** Suggesting relevant idioms or phrasal verbs where appropriate to sound more natural (use sparingly and ensure relevance).
        *   **Natural Phrasing:** Identifying slightly awkward or non-native sounding phrasing and suggesting more natural alternatives.
        *   **Register:** Pointing out potential mismatches in formality (e.g., using very formal words in a casual chat context, or vice versa), though less critical in chat.
        
        **Input Format:**
        The input will be multiple messages formatted like this:
        Message ID: [UUID]
        Text: [User's message content]
        ---
        Message ID: [UUID]
        Text: [User's message content]
        
        **Output Requirements:**
        1.  **CRITICAL:** Your *entire* response MUST be a single valid JSON array `[...]`. No introductory text, no explanations, no markdown backticks, nothing before `[` or after `]`.
        2.  The array must contain one JSON object for *each* input message, representing a `TextAnalysisResult`.
        3.  Each `TextAnalysisResult` object MUST have these fields:
            *   `messageId`: (String) The UUID of the message being analyzed.
            *   `originalText`: (String) The original text of the user message.
            *   `analysisType`: (String) The type of analysis performed (use **"VOCABULARY_PHRASING"** for this prompt).
            *   `issues`: (Array<AnalysisIssue>) A list of issues found in this message.
        4.  **If a message has NO issues** of the specified type, the `issues` array for that message MUST be empty (`[]`). Do *not* omit the message object.
        5.  Each `AnalysisIssue` object within the `issues` array MUST have these fields:
            *   `type`: (String) Use **"vocabulary"** or **"phrasing"** based on the nature of the issue.
            *   `text`: (String) The *specific* word or short phrase identified for improvement.
            *   `description`: (String) A brief explanation of *why* a change is suggested (e.g., "basic word choice", "unnatural collocation", "more precise alternative exists").
            *   `suggestion`: (String) The suggested alternative word, phrase, idiom, or collocation.
        6.  Do NOT include `startOffset` or `endOffset` fields in the `AnalysisIssue` object.
        
        **Example JSON Output Structure (Illustrative):**
        ```json
        [
          {
            "messageId": "123e4567-e89b-12d3-a456-426614174000",
            "originalText": "It was a very good movie, I felt happy.",
            "analysisType": "VOCABULARY_PHRASING",
            "issues": [
              {
                "type": "vocabulary",
                "text": "very good",
                "description": "Basic vocabulary. Consider more descriptive words.",
                "suggestion": "excellent / fantastic / superb"
              },
              {
                "type": "vocabulary",
                "text": "happy",
                "description": "General emotion. Could be more specific.",
                "suggestion": "thrilled / delighted / uplifted"
              }
            ]
          },
           {
            "messageId": "123e4567-e89b-12d3-a456-426614174002",
            "originalText": "We need to make more research.",
            "analysisType": "VOCABULARY_PHRASING",
            "issues": [
               {
                "type": "phrasing",
                "text": "make more research",
                "description": "Unnatural collocation. The common verb with 'research' is 'do'.",
                "suggestion": "do more research"
              }
             ]
           },
          {
            "messageId": "abcdef01-e89b-12d3-a456-426614174001",
            "originalText": "The weather is great today.",
            "analysisType": "VOCABULARY_PHRASING",
            "issues": [] // Empty array as vocabulary/phrasing is adequate
          }
        ]
            """
    }
}