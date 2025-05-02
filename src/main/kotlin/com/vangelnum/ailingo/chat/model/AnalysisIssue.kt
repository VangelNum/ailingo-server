package com.vangelnum.ailingo.chat.model

data class AnalysisIssue(
    val type: String,
    val text: String,
    val description: String,
    val suggestion: String?
)