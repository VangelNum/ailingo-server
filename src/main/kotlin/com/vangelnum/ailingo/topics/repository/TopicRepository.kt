package com.vangelnum.ailingo.topics.repository

import com.vangelnum.ailingo.topics.entity.TopicEntity
import com.vangelnum.ailingo.user.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface TopicRepository : JpaRepository<TopicEntity, Long> {
    fun deleteTopicByName(name: String)
    fun findByName(name: String): Optional<TopicEntity>
    fun findByCreatorIsNullOrCreator(creator: UserEntity): List<TopicEntity>
}