package com.vangelnum.ailingo.textanalysis.service

import com.vangelnum.ailingo.chat.model.AnalysisType
import com.vangelnum.ailingo.chat.model.TextAnalysisResult
import java.util.UUID

interface TextAnalysisService {
    fun analyzeConversation(conversationId: UUID, analysisType: AnalysisType): List<TextAnalysisResult>
    fun singleMessageCheck(userInput: String): String?
}