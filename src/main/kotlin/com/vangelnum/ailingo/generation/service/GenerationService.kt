package com.vangelnum.ailingo.generation.service

interface GenerationService {
    suspend fun generateImage(
        prompt: String,
        model: String?,
        seed: Int?,
        width: Int?,
        height: Int?,
        nologo: Boolean?,
        private: Boolean?,
        enhance: Boolean?,
        safe: Boolean?
    ): ByteArray

    suspend fun getImageModels(): List<String>

    suspend fun generateText(
        prompt: String,
        model: String?,
        seed: Int?,
        json: Boolean?,
        system: String?
    ): String

    suspend fun getTextModels(): List<String>
}