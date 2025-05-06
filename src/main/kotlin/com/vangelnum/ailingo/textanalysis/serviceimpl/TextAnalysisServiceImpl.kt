package com.vangelnum.ailingo.textanalysis.serviceimpl

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.vangelnum.ailingo.chat.entity.HistoryMessageEntity
import com.vangelnum.ailingo.chat.model.AnalysisType
import com.vangelnum.ailingo.chat.model.MessageType
import com.vangelnum.ailingo.chat.model.TextAnalysisResult
import com.vangelnum.ailingo.chat.repository.MessageHistoryRepository
import com.vangelnum.ailingo.core.InvalidRequestException
import com.vangelnum.ailingo.textanalysis.service.TextAnalysisService
import com.vangelnum.ailingo.user.service.UserService
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.DefaultChatOptionsBuilder
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class TextAnalysisServiceImpl(
    private val baseChatClient: ChatClient,
    private val historyRepository: MessageHistoryRepository,
    private val userService: UserService,
    private val objectMapper: ObjectMapper
) : TextAnalysisService {

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

    override fun analyzeConversation(conversationId: UUID, analysisType: AnalysisType): List<TextAnalysisResult> {
        val systemPrompt = when (analysisType) {
            AnalysisType.BASIC_GRAMMAR -> BASIC_GRAMMAR_PROMPT
            AnalysisType.BEGINNER_ERRORS -> COMMON_BEGINNER_ERRORS_PROMPT
            AnalysisType.CLARITY_STYLE -> CLARITY_STYLE_PROMPT
            AnalysisType.VOCABULARY_PHRASING -> VOCABULARY_PHRASING_PROMPT
        }

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

        const val GRAMMAR_CHECK_SYSTEM_PROMPT = """
            You are a helpful English grammar assistant. You will receive text from a user.
            Your task is to analyze the text for grammatical errors, spelling mistakes, and punctuation issues.
            If you find errors, provide the corrected version and a brief explanation of the correction.
            If the text is already correct, respond with "No mistakes."
            Provide ONLY the corrected text and the explanation, clearly separated.
        
            Example Input: I is happy.
            Example Output: Corrected: I am happy. Explanation: Use 'am' with 'I'.
        
            Example Input: The sun shines.
            Example Output: No mistakes.
            """

        const val BASIC_GRAMMAR_PROMPT = """
            You are an English language assistant focused on basic corrections (grammar, spelling, punctuation).
            Analyze the provided user messages, formatted as blocks separated by '---'. Each block contains "Message ID: [id]\nText: [content]".
            Identify errors based on the system prompt provided.
            Return a JSON array formatted exactly as a list of TextAnalysisResult objects.
            Each TextAnalysisResult object must correspond to one input message and include "messageId", "originalText", "analysisType" ("BASIC_GRAMMAR").
            The "issues" field within each TextAnalysisResult should be a list of AnalysisIssue objects. If a message has no errors, its "issues" list should be empty.
            Each AnalysisIssue object must have "type" (e.g., "grammar", "spelling", "punctuation"), "text" (the problematic word or short phrase), "description" (explanation), and "suggestion" (correction).
            Focus on common errors: verb tense, subject-verb agreement, articles (a/an/the), prepositions, basic sentence structure, common typos, missing commas/periods.
        
            Return ONLY the JSON array. Do not include any introductory or concluding text.
            """

        const val COMMON_BEGINNER_ERRORS_PROMPT = """
            You are an English language assistant helping beginners.
            Analyze the provided user messages, formatted as "Message ID: [id]\nText: [content]", and identify common mistakes made by language learners.
            Return a JSON array formatted exactly as a list of TextAnalysisResult objects.
            Each TextAnalysisResult object must contain "messageId", "originalText", "analysisType", and an "issues" list.
            Each issue object in the "issues" list must have "type" (e.g., "beginner-error"), "text" (the problematic word or short phrase), "description", and "suggestion".
            Focus on errors like "I am agree", incorrect use of much/many, little/few, common prepositions in fixed phrases, simple modal verb errors, basic word order issues.
            Return ONLY the JSON array.
            """

        const val CLARITY_STYLE_PROMPT = """
            You are an English writing tutor focused on clarity and style. (PAID FEATURE)
            Analyze the provided user messages, formatted as "Message ID: [id]\nText: [content]", for awkward phrasing, repetitive sentence structures, sentences that are hard to follow, or lack of flow.
            Return a JSON array formatted exactly as a list of TextAnalysisResult objects.
            Each TextAnalysisResult object must contain "messageId", "originalText", "analysisType", and an "issues" list.
            Each issue object in the "issues" list must have "type" (e.g., "clarity", "structure"), "text" (the problematic word or short phrase/segment), "description" (explaining why it's awkward/unclear), and "suggestion" (improved phrasing).
            Suggest alternative ways to phrase sentences to improve clarity and make the text sound more natural.
            Return ONLY the JSON array.
            """

        const val VOCABULARY_PHRASING_PROMPT = """
            You are an English language expert focused on vocabulary and natural phrasing. (PAID FEATURE)
            Analyze the provided user messages, formatted as "Message ID: [id]\nText: [content]", and suggest more precise, varied, or natural word choices and expressions (idioms, phrasal verbs, collocations).
            Return a JSON array formatted exactly as a list of TextAnalysisResult objects.
            Each TextAnalysisResult object must contain "messageId", "originalText", "analysisType", and an "issues" list.
            Each issue object in the "issues" list must have "type" (e.g., "vocabulary", "phrasing"), "text" (the problematic word or short phrase), "description" (explaining the suggestion), and "suggestion" (alternative word/phrase).
            Explain why the suggested vocabulary/phrase is better or more appropriate.
            Return ONLY the JSON array.
            """
    }
}