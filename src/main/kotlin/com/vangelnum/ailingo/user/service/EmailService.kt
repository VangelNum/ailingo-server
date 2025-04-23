package com.vangelnum.ailingo.user.service


interface EmailService {
    fun sendVerificationEmail(to: String, verificationCode: String)
}