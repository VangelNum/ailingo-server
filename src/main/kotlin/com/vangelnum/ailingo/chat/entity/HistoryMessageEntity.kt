package com.vangelnum.ailingo.chat.entity

import com.vangelnum.ailingo.chat.model.MessageType
import com.vangelnum.ailingo.topics.entity.TopicEntity
import com.vangelnum.ailingo.user.entity.UserEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "message_history")
data class HistoryMessageEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne
    @JoinColumn(name = "conversation_owner", nullable = false)
    var owner: UserEntity,

    @ManyToOne
    @JoinColumn(name = "topic_id", nullable = false)
    var topic: TopicEntity,

    @Column(nullable = false)
    var conversationId: UUID,

    @Enumerated(EnumType.STRING)
    var type: MessageType,

    @Column(nullable = false)
    var content: String? = null,

    @Column(nullable = false)
    var timestamp: Instant
)