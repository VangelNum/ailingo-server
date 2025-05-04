package com.vangelnum.ailingo.achievement.model

import java.time.LocalDateTime

data class AchievementResponse(
    val type: AchievementType,
    val coins: Int,
    val xp: Int,
    val claimed: Boolean,
    val isAvailable: Boolean,
    val achievementId: Long?,
    val description: String,
    val imageUrl: String,
    val claimDate: LocalDateTime?
)