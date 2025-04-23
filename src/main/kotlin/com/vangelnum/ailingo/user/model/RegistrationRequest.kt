package com.vangelnum.ailingo.user.model

data class RegistrationRequest(
    val name: String,
    val password: String,
    val email: String
)