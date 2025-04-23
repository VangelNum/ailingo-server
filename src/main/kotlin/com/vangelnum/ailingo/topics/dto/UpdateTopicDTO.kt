package com.vangelnum.ailingo.topics.dto

data class UpdateTopicDTO(
    val name: String?,
    val image: String?,
    val price: Int?,
    val level: Int?,
    val welcomePrompt: String?,
    val systemPrompt: String?,
    val messageLimit: Int?,
    val topicXp: Int?
)