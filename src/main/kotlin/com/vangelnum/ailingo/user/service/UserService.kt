package com.vangelnum.ailingo.user.service

import com.vangelnum.ailingo.user.entity.UserEntity
import com.vangelnum.ailingo.user.model.RegistrationRequest
import com.vangelnum.ailingo.user.model.UpdateProfileRequest

interface UserService {
    fun registerUser(registrationRequest: RegistrationRequest): String
    fun updateUserAvatar(avatar: String): UserEntity
    fun getCurrentUser(): UserEntity
    fun getUserByEmail(email: String): UserEntity
    fun getAllUsers(): List<UserEntity>
    fun getUserById(id: Long): UserEntity
    fun updateUser(updateProfileRequest: UpdateProfileRequest): UserEntity
    fun deleteUser(id: Long)
    fun verifyEmail(email: String, verificationCode: String): UserEntity
    fun resendVerificationCode(email: String)
    fun changeCoins(amount: Int)
}