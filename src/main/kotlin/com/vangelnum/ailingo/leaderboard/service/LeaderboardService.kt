package com.vangelnum.ailingo.leaderboard.service

import com.vangelnum.ailingo.leaderboard.dto.LeaderboardDto

interface LeaderboardService {
    fun getLeaderboard(): List<LeaderboardDto>
}
