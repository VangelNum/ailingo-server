package com.vangelnum.ailingo.topics.entity

import com.vangelnum.ailingo.user.entity.UserEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Lob
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "topic")
data class TopicEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    var name: String,

    @Column(columnDefinition = "TEXT")
    var image: String?,

    var price: Int,

    var level: Int,

    @Lob
    @Column(name = "welcome_prompt")
    var welcomePrompt: String,

    @Lob
    @Column(name = "system_prompt")
    var systemPrompt: String,

    @Column(name = "message_limit")
    var messageLimit: Int,

    @Column(name = "xp_complete_topic")
    var xpCompleteTopic: Int,

    @ManyToOne
    @JoinColumn(name = "creator_id")
    var creator: UserEntity? = null
)