package com.vangelnum.ailingo.user.model

data class DailyLoginResponse(
    val streak: Int,
    val coinsRewarded: Int,
    val message: String,
    val totalRemainingTimeSeconds: Long,
    val isAvailable: Boolean
)