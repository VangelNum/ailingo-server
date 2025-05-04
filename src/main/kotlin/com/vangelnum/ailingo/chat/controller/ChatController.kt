package com.vangelnum.ailingo.chat.controller

import com.vangelnum.ailingo.chat.model.AnalysisType
import com.vangelnum.ailingo.chat.model.ConversationMessage
import com.vangelnum.ailingo.chat.model.ConversationSummary
import com.vangelnum.ailingo.chat.model.TextAnalysisResult
import com.vangelnum.ailingo.chat.service.ChatService
import com.vangelnum.ailingo.core.InvalidRequestException
import com.vangelnum.ailingo.user.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "Взаимодействие с ботом")
@RestController
@RequestMapping("/api/v1/conversations")
class ChatController(
    private val chatService: ChatService,
    private val userService: UserService
) {

    @Operation(
        summary = "Начать новый диалог по теме",
        description = "Создает новый диалог с ботом на выбранную тему и возвращает первое сообщение бота."
    )
    @PostMapping("/{topicName}")
    fun startConversation(@PathVariable topicName: String): ConversationMessage {
        return chatService.startConversation(topicName)
    }

    @Operation(
        summary = "Начать новый диалог по своей теме",
        description = "Создает новый диалог с ботом на тему, сгенерированную на основе запроса пользователя и возвращает первое сообщение бота."
    )
    @PostMapping("/custom")
    fun startCustomConversation(@RequestBody topicIdea: String): ConversationMessage {
        return chatService.startCustomConversation(topicIdea)
    }

    @Operation(
        summary = "Получить сообщения конкретного диалога",
        description = "Возвращает все сообщения для указанного ID диалога, принадлежащего текущему пользователю."
    )
    @GetMapping("/{conversationId}")
    fun getConversationMessages(@PathVariable conversationId: String): List<ConversationMessage> {
        try {
            val conversationUuid = UUID.fromString(conversationId)
            return chatService.getMessages(conversationUuid)
        } catch (e: IllegalArgumentException) {
            throw InvalidRequestException("Invalid conversation ID format.")
        }
    }

    @Operation(
        summary = "Продолжить диалог",
        description = "Отправляет сообщение пользователя в существующий диалог и возвращает ответ бота."
    )
    @PostMapping("/continue/{conversationId}")
    fun continueDialog(@PathVariable conversationId: String, @RequestBody userInput: String): ConversationMessage {
        try {
            val conversationUuid = UUID.fromString(conversationId)
            return chatService.continueDialog(conversationUuid, userInput)
        } catch (e: IllegalArgumentException) {
            throw InvalidRequestException("Invalid conversation ID format.")
        }
    }

    @Operation(
        summary = "Получить список всех активных диалогов пользователя",
        description = "Возвращает сводную информацию по каждому диалогу, принадлежащему текущему пользователю."
    )
    @GetMapping("/all")
    fun getConversations(): List<ConversationSummary> {
        return chatService.getConversations()
    }

    @Operation(
        summary = "Проверка одного сообщения на грамматические ошибки (Бесплатно)"
    )
    @PostMapping("/grammarCheck")
    fun singleMessageCheck(@RequestBody(required = true) userInput: String): ResponseEntity<String> {
        val grammarCheckResult = chatService.singleMessageCheck(userInput)
        return ResponseEntity.ok(grammarCheckResult)
    }

    @Operation(summary = "Анализ диалога: Базовая грамматика, орфография, пунктуация (Бесплатно)",
        description = "Проверяет все сообщения пользователя в диалоге на базовые ошибки GSP.")
    @PostMapping("/{conversationId}/analyze/basic-grammar")
    fun analyzeBasicGrammar(@PathVariable conversationId: String): ResponseEntity<List<TextAnalysisResult>> {
        return analyzeConversation(conversationId, AnalysisType.BASIC_GRAMMAR)
    }

    @Operation(summary = "Анализ диалога: Распространенные ошибки новичков (Бесплатно)",
        description = "Проверяет все сообщения пользователя в диалоге на типичные ошибки начинающих.")
    @PostMapping("/{conversationId}/analyze/beginner-errors")
    fun analyzeBeginnerErrors(@PathVariable conversationId: String): ResponseEntity<List<TextAnalysisResult>> {
        return analyzeConversation(conversationId, AnalysisType.BEGINNER_ERRORS)
    }

    @Operation(summary = "Анализ диалога: Структура предложения и ясность изложения (Платно)",
        description = "Проверяет все сообщения пользователя в диалоге, предлагая улучшения структуры и ясности текста. Требуется платная подписка.")
    @PostMapping("/{conversationId}/analyze/clarity-style")
    fun analyzeClarityStyle(@PathVariable conversationId: String): ResponseEntity<List<TextAnalysisResult>> {
        return performPaidAnalysis(conversationId, AnalysisType.CLARITY_STYLE)
    }

    @Operation(summary = "Анализ диалога: Словарный запас и естественные выражения (Платно)",
        description = "Проверяет все сообщения пользователя в диалоге, предлагая улучшения словарного запаса и более естественные выражения. Требуется платная подписка.")
    @PostMapping("/{conversationId}/analyze/vocabulary-phrasing")
    fun analyzeVocabularyPhrasing(@PathVariable conversationId: String): ResponseEntity<List<TextAnalysisResult>> {
        return performPaidAnalysis(conversationId, AnalysisType.VOCABULARY_PHRASING)
    }

    private fun analyzeConversation(conversationId: String, analysisType: AnalysisType): ResponseEntity<List<TextAnalysisResult>> {
        try {
            val conversationUuid = UUID.fromString(conversationId)
            val analysisResults = when (analysisType) {
                AnalysisType.BASIC_GRAMMAR -> chatService.analyzeConversationBasicGrammar(conversationUuid)
                AnalysisType.BEGINNER_ERRORS -> chatService.analyzeConversationCommonErrors(conversationUuid)
                AnalysisType.CLARITY_STYLE -> throw IllegalStateException("This should not be called directly for clarity style.")
                AnalysisType.VOCABULARY_PHRASING -> throw IllegalStateException("This should not be called directly for vocabulary phrasing.")
            }
            return ResponseEntity.ok(analysisResults)
        } catch (e: IllegalArgumentException) {
            throw InvalidRequestException("Invalid conversation ID format.")
        }
    }

    private fun performPaidAnalysis(conversationId: String, analysisType: AnalysisType): ResponseEntity<List<TextAnalysisResult>> {
        try {
            val conversationUuid = UUID.fromString(conversationId)
            val analysisResults = when (analysisType) {
                AnalysisType.CLARITY_STYLE -> {
                    userService.changeCoins(-10)
                    chatService.analyzeConversationClarityStyle(conversationUuid)
                }
                AnalysisType.VOCABULARY_PHRASING -> {
                    userService.changeCoins(-10)
                    chatService.analyzeConversationVocabulary(conversationUuid)
                }
                else -> throw IllegalArgumentException("Invalid analysis type for paid analysis.")
            }
            return ResponseEntity.ok(analysisResults)
        } catch (e: IllegalArgumentException) {
            throw InvalidRequestException("Invalid conversation ID format.")
        }
    }
}