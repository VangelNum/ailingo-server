package com.vangelnum.ailingo.user.model

import java.time.Instant

data class DailyLoginResponse(
    val streak: Int,
    val coinsRewarded: Int,
    val nextRewardAvailable: Instant?,
    val message: String
)