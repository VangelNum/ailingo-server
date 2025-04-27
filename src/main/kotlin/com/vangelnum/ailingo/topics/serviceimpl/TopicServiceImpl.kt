package com.vangelnum.ailingo.topics.serviceimpl

import com.vangelnum.ailingo.chat.model.MessageType
import com.vangelnum.ailingo.chat.repository.MessageHistoryRepository
import com.vangelnum.ailingo.topics.dto.CreateTopicDTO
import com.vangelnum.ailingo.topics.dto.TopicResponseDTO
import com.vangelnum.ailingo.topics.dto.UpdateTopicDTO
import com.vangelnum.ailingo.topics.entity.TopicEntity
import com.vangelnum.ailingo.topics.repository.TopicRepository
import com.vangelnum.ailingo.topics.service.TopicService
import com.vangelnum.ailingo.user.service.UserService
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TopicServiceImpl(
    val topicRepository: TopicRepository,
    val userService: UserService,
    val historyRepository: MessageHistoryRepository
) : TopicService {
    override fun getTopics(): List<TopicResponseDTO> {
        val currentUser = userService.getCurrentUser()

        return topicRepository.findAll().map { topicEntity ->
            val isCompleted =
                historyRepository.existsByTopicAndOwnerAndType(topicEntity, currentUser, MessageType.FINAL)

            TopicResponseDTO(
                id = topicEntity.id,
                name = topicEntity.name,
                imageUrl = topicEntity.image,
                price = topicEntity.price,
                welcomePrompt = topicEntity.welcomePrompt,
                systemPrompt = topicEntity.systemPrompt,
                messageLimit = topicEntity.messageLimit,
                isCompleted = isCompleted,
                topicXp = topicEntity.xpCompleteTopic
            )
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    override fun addTopic(createTopicDto: CreateTopicDTO): TopicEntity {
        val topic = TopicEntity(
            name = createTopicDto.name,
            image = createTopicDto.image,
            price = createTopicDto.price,
            level = createTopicDto.level,
            welcomePrompt = createTopicDto.welcomePrompt,
            systemPrompt = createTopicDto.systemPrompt,
            messageLimit = createTopicDto.messageLimit,
            xpCompleteTopic = createTopicDto.topicXp,
            id = 0
        )

        return topicRepository.save(topic)
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    override fun addTopics(createTopicDTOs: List<CreateTopicDTO>): List<TopicEntity> {
        val topics = createTopicDTOs.map { dto ->
            TopicEntity(
                name = dto.name,
                image = dto.image,
                price = dto.price,
                level = dto.level,
                welcomePrompt = dto.welcomePrompt,
                systemPrompt = dto.systemPrompt,
                messageLimit = dto.messageLimit,
                xpCompleteTopic = dto.topicXp,
                id = 0
            )
        }
        return topicRepository.saveAll(topics)
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    override fun updateTopic(id: Long, updateTopicDto: UpdateTopicDTO): TopicEntity {
        val topic = topicRepository.findByIdOrNull(id) ?: throw IllegalArgumentException("Топик с id $id не найден")

        updateTopicDto.name?.let { topic.name = it }
        updateTopicDto.image?.let { topic.image = it }
        updateTopicDto.price?.let { topic.price = it }
        updateTopicDto.welcomePrompt?.let { topic.welcomePrompt = it }
        updateTopicDto.systemPrompt?.let { topic.systemPrompt = it }
        updateTopicDto.messageLimit?.let { topic.messageLimit = it }
        updateTopicDto.topicXp?.let { topic.xpCompleteTopic = it }

        return topicRepository.save(topic)
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Transactional
    override fun deleteTopicByName(name: String) {
        val topicToDelete = topicRepository.findByName(name)
            .orElseThrow { IllegalArgumentException("Топик с именем '$name' не найден") }
        historyRepository.deleteAllByTopicId(topicToDelete.id)
        topicRepository.deleteTopicByName(name)
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Transactional
    override fun deleteTopicById(id: Long) {
        if (!topicRepository.existsById(id)) {
            throw IllegalArgumentException("Топик с id $id не найден")
        }
        historyRepository.deleteAllByTopicId(id)
        topicRepository.deleteById(id)
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Transactional
    override fun deleteAllTopic() {
        historyRepository.deleteAll()
        topicRepository.deleteAll()
    }
}