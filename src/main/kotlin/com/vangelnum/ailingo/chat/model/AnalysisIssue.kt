package com.vangelnum.ailingo.chat.model

data class AnalysisIssue(
    val type: String, // Тип проблемы (например, "grammar", "spelling", "punctuation", "clarity", "vocabulary")
    val text: String, // Сегмент текста, содержащий проблему
    val description: String, // Объяснение проблемы
    val suggestion: String?, // Предложение по исправлению (может быть null)
    val startOffset: Int, // Начальный индекс проблемного сегмента в originalText
    val endOffset: Int // Конечный индекс проблемного сегмента в originalText
)