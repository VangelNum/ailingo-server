package com.vangelnum.ailingo.achievement.repository

import com.vangelnum.ailingo.achievement.entity.AchievementEntity
import com.vangelnum.ailingo.achievement.model.AchievementType
import com.vangelnum.ailingo.user.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AchievementRepository : JpaRepository<AchievementEntity, Long> {
    fun existsByUserAndType(user: UserEntity, type: AchievementType): Boolean
    fun findByUserAndType(user: UserEntity, type: AchievementType): AchievementEntity?
}