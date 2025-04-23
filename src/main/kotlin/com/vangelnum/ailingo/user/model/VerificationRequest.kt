package com.vangelnum.ailingo.user.model

data class VerificationRequest(
    val email: String,
    val verificationCode: String
)