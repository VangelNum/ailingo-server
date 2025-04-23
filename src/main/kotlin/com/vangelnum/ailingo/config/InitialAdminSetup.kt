package com.vangelnum.ailingo.config

import com.vangelnum.ailingo.core.enums.Role
import com.vangelnum.ailingo.user.entity.UserEntity
import com.vangelnum.ailingo.user.repository.UserRepository
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDateTime

@Configuration
class InitialAdminSetup(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${ADMIN_USERNAME}") private val adminUsername: String,
    @Value("\${ADMIN_PASSWORD}") private val adminPassword: String,
    @Value("\${ADMIN_EMAIL}") private val adminEmail: String
) {
    @PostConstruct
    fun createInitialAdmin() {
        if (userRepository.count() == 0L) {
            val adminUser = UserEntity(
                name = adminUsername,
                password = passwordEncoder.encode(adminPassword),
                email = adminEmail,
                role = Role.ADMIN,
                coins = 1000,
                isEmailVerified = true,
                verificationCode = null,
                registrationTime = LocalDateTime.now(),
                lastLoginTime = null,
                streak = 0,
                xp = 0
            )
            userRepository.save(adminUser)
        }
    }
}