package com.vangelnum.ailingo.chat.controller

import com.vangelnum.ailingo.chat.model.ConversationMessage
import com.vangelnum.ailingo.chat.model.ConversationSummary
import com.vangelnum.ailingo.chat.service.ChatService
import com.vangelnum.ailingo.core.InvalidRequestException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
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
    private val chatService: ChatService
) {

    @PostMapping("/{topicName}")
    fun startConversation(@PathVariable topicName: String): ConversationMessage {
        return chatService.startConversation(topicName)
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

    @PostMapping("/continue/{conversationId}")
    fun continueDialog(@PathVariable conversationId: String, @RequestBody userInput: String): ConversationMessage {
        return chatService.continueDialog(UUID.fromString(conversationId), userInput)
    }

    @Operation(summary = "Получить список чатов")
    @GetMapping("/all")
    fun getConversations(): List<ConversationSummary> {
        return chatService.getConversations()
    }

    @Operation(
        summary = "Отправка пользовательского текста",
        description = "Проверка его на грамматические ошибки"
    )

    @PostMapping
    fun gramaticTetsForText(userInput: String?): String?{
        return chatService.gramaticTetsForText(userInput)
    }
}
