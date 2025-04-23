package com.vangelnum.ailingo.core

import jakarta.persistence.EntityNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.WebRequest

@ControllerAdvice
class GlobalExceptionHandler {

    companion object {
        const val INVALID_REQUEST_MESSAGE = "Некорректный запрос"
        const val UNKNOWN_ERROR_MESSAGE = "Произошла неизвестная ошибка"
        const val ACCESS_DENIED_MESSAGE = "Доступ запрещен"
        const val RESOURCE_NOT_FOUND_MESSAGE = "Запрашиваемый ресурс не найден"
        const val EMAIL_ALREADY_VERIFIED_MESSAGE = "Email уже подтвержден"
        const val INVALID_VERIFICATION_CODE_MESSAGE = "Неверный код подтверждения"
        const val USER_ALREADY_EXISTS_MESSAGE = "Пользователь уже существует"
        const val EMAIL_SENDING_FAILED_MESSAGE = "Проблема отправки сообщения на почту. Попробуйте использовать другую почту."
        const val WRONG_CURRENT_PASSWORD_MESSAGE = "Неверный текущий пароль."
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(
        ex: IllegalArgumentException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            message = ex.message ?: INVALID_REQUEST_MESSAGE,
            status = HttpStatus.BAD_REQUEST.value()
        )
        return ResponseEntity.badRequest().body(errorResponse)
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNoSuchElementException(
        ex: NoSuchElementException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            message = ex.message ?: RESOURCE_NOT_FOUND_MESSAGE,
            status = HttpStatus.NOT_FOUND.value()
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse)
    }

    @ExceptionHandler(EntityNotFoundException::class)
    fun handleEntityNotFoundException(ex: EntityNotFoundException, request: WebRequest): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            message = ex.message ?: RESOURCE_NOT_FOUND_MESSAGE,
            status = HttpStatus.NOT_FOUND.value()
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(ex: AccessDeniedException, request: WebRequest): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            message = ACCESS_DENIED_MESSAGE,
            status = HttpStatus.FORBIDDEN.value()
        )
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse)
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneralException(
        ex: Exception,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            message = ex.message ?: UNKNOWN_ERROR_MESSAGE,
            status = HttpStatus.INTERNAL_SERVER_ERROR.value()
        )
        return ResponseEntity.internalServerError().body(errorResponse)
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalStateException(
        ex: IllegalStateException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            message = ex.message ?: UNKNOWN_ERROR_MESSAGE,
            status = HttpStatus.BAD_REQUEST.value()
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

}

data class ErrorResponse(
    val message: String,
    val status: Int
)

class InvalidRequestException(message: String) : RuntimeException(message)