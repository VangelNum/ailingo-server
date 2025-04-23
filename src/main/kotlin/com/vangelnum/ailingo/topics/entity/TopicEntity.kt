package com.vangelnum.ailingo.topics.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table

@Entity
@Table(name = "topic")
data class TopicEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    var name: String,

    var image: String,

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
    var xpCompleteTopic: Int
)