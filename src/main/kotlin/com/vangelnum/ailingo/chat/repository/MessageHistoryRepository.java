package com.vangelnum.ailingo.chat.repository;

import com.vangelnum.ailingo.chat.dto.ConversationSummaryDto;
import com.vangelnum.ailingo.chat.entity.HistoryMessageEntity;
import com.vangelnum.ailingo.chat.model.MessageType;
import com.vangelnum.ailingo.topics.entity.TopicEntity;
import com.vangelnum.ailingo.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageHistoryRepository extends JpaRepository<HistoryMessageEntity, Long> {

    List<HistoryMessageEntity> findByConversationIdAndOwnerOrderByTimestamp(UUID id, UserEntity owner);

    @Query("SELECT NEW com.vangelnum.ailingo.chat.dto.ConversationSummaryDto(h.conversationId, t.name, MIN(h.timestamp)) " +
            "FROM HistoryMessageEntity h JOIN h.topic t " +
            "WHERE h.owner = :owner " +
            "GROUP BY h.conversationId, t.name " +
            "ORDER BY MIN(h.timestamp) DESC")
    List<ConversationSummaryDto> findAllByOwner(UserEntity owner);

    Boolean existsByTopicAndOwnerAndType(TopicEntity topic, UserEntity owner, MessageType type);
}