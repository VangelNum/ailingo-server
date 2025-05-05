package com.vangelnum.ailingo.topics.dto

data class TopicResponseDTO(
    val id: Long,
    val name: String,
    val imageUrl: String?,
    val price: Int,
    val level: Int,
    val welcomePrompt: String,
    val systemPrompt: String,
    val messageLimit: Int,
    val isCompleted: Boolean = false,
    val topicXp: Int
)
