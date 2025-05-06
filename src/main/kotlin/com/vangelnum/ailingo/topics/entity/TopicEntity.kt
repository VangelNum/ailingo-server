package com.vangelnum.ailingo.topics.entity

import com.vangelnum.ailingo.user.entity.UserEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import kotlin.math.ceil

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

    @Column(name = "welcome_prompt", columnDefinition = "TEXT")
    var welcomePrompt: String,

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    var systemPrompt: String,

    @Column(name = "message_limit")
    var messageLimit: Int,

    @Column(name = "xp_complete_topic")
    var xpCompleteTopic: Int,

    @Column(name = "coin_complete_topic")
    var coinCompleteTopic: Int = calculateCoinCompleteTopic(price),

    @ManyToOne
    @JoinColumn(name = "creator_id")
    var creator: UserEntity? = null
) {
    companion object {
        fun calculateCoinCompleteTopic(price: Int): Int {
            return ceil(price.toDouble() / 2).toInt()
        }
    }

    fun updateCoinCompleteTopic() {
        coinCompleteTopic = calculateCoinCompleteTopic(price)
    }
}