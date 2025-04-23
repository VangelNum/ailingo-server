package com.vangelnum.ailingo.topics.service

import com.vangelnum.ailingo.topics.dto.CreateTopicDTO
import com.vangelnum.ailingo.topics.dto.TopicResponseDTO
import com.vangelnum.ailingo.topics.dto.UpdateTopicDTO
import com.vangelnum.ailingo.topics.entity.TopicEntity

interface TopicService {
    fun getTopics(): List<TopicResponseDTO>

    fun addTopic(createTopicDto: CreateTopicDTO): TopicEntity

    fun addTopics(createTopicDTOs: List<CreateTopicDTO>): List<TopicEntity>

    fun updateTopic(id: Long, updateTopicDto: UpdateTopicDTO): TopicEntity

    fun deleteTopicByName(name: String)

    fun deleteTopicById(id: Long)
    fun deleteAllTopic()
}