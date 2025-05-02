package com.vangelnum.ailingo.chat.model

data class TextAnalysisResult(
    val messageId: String,
    val originalText: String,
    val analysisType: String,
    val issues: List<AnalysisIssue>
)