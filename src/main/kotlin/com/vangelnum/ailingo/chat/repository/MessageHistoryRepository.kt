package com.vangelnum.ailingo.chat.repository

import com.vangelnum.ailingo.chat.entity.HistoryMessageEntity
import com.vangelnum.ailingo.chat.model.MessageType
import com.vangelnum.ailingo.topics.entity.TopicEntity
import com.vangelnum.ailingo.user.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface MessageHistoryRepository : JpaRepository<HistoryMessageEntity, Long> {
    fun findByConversationIdAndOwnerOrderByTimestamp(id: UUID, owner: UserEntity): List<HistoryMessageEntity>
    fun existsByTopicAndOwnerAndType(topic: TopicEntity, owner: UserEntity, type: MessageType): Boolean
    fun deleteAllByOwnerId(ownerId: Long): Int
    @Query(
        """
        SELECT h FROM HistoryMessageEntity h
        WHERE h.owner = :owner
        AND h.conversationId IN (SELECT DISTINCT h2.conversationId FROM HistoryMessageEntity h2 WHERE h2.owner = :owner)
        ORDER BY h.timestamp DESC
    """
    )
    fun findLatestMessagesByOwnerGroupedByConversationId(owner: UserEntity): List<HistoryMessageEntity>

    @Modifying
    @Query("DELETE FROM HistoryMessageEntity h WHERE h.topic.id = :topicId")
    fun deleteAllByTopicId(topicId: Long): Int
}