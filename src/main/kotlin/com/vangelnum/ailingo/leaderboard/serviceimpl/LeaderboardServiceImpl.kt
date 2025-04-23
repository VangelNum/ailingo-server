package com.vangelnum.ailingo.leaderboard.serviceimpl

import com.vangelnum.ailingo.leaderboard.dto.LeaderboardDto
import com.vangelnum.ailingo.leaderboard.service.LeaderboardService
import com.vangelnum.ailingo.user.entity.UserEntity
import com.vangelnum.ailingo.user.repository.UserRepository
import org.springframework.stereotype.Service

@Service
class LeaderboardServiceImpl(
    private val userRepository: UserRepository
) : LeaderboardService {

    override fun getLeaderboard(): List<LeaderboardDto> {
        return userRepository.findTop100ByOrderByStreakDesc().map { mapToLeaderboardDto(it) }
    }

    fun mapToLeaderboardDto(userEntity: UserEntity): LeaderboardDto {
        return LeaderboardDto(
            userEntity.coins,
            userEntity.streak,
            userEntity.avatar,
            userEntity.name,
        )
    }
}
