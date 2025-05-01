package com.vangelnum.ailingo.chat.model

data class TextAnalysisResult(
    val messageId: String, // ID сообщения, которое было проанализировано
    val originalText: String, // Оригинальный текст сообщения
    val analysisType: String, // Тип анализа, который был выполнен (например, "basic-grammar")
    val issues: List<AnalysisIssue> // Список найденных проблем
)