package com.vangelnum.ailingo.user.serviceimpl

import com.vangelnum.ailingo.core.GlobalExceptionHandler
import com.vangelnum.ailingo.core.enums.Role
import com.vangelnum.ailingo.core.utils.getCurrentUserEmail
import com.vangelnum.ailingo.core.validator.UserValidator
import com.vangelnum.ailingo.pendinguser.entity.PendingUser
import com.vangelnum.ailingo.pendinguser.repository.PendingUserRepository
import com.vangelnum.ailingo.user.entity.UserEntity
import com.vangelnum.ailingo.user.model.RegistrationRequest
import com.vangelnum.ailingo.user.model.UpdateProfileRequest
import com.vangelnum.ailingo.user.repository.UserRepository
import com.vangelnum.ailingo.user.service.EmailService
import com.vangelnum.ailingo.user.service.UserService
import jakarta.persistence.EntityNotFoundException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.MalformedURLException
import java.net.URL
import java.time.LocalDateTime
import java.util.Random

@Service
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val pendingUserRepository: PendingUserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val userValidator: UserValidator,
    private val emailService: EmailService
) : UserService {

    @Transactional
    override fun registerUser(registrationRequest: RegistrationRequest): String {
        val checkStatus = userValidator.checkUser(registrationRequest)
        if (!checkStatus.isSuccess) {
            throw IllegalArgumentException(checkStatus.message)
        }

        if (userRepository.findByEmail(registrationRequest.email).isPresent) {
            throw IllegalArgumentException(GlobalExceptionHandler.USER_ALREADY_EXISTS_MESSAGE)
        }

        val existingPendingUserOptional = pendingUserRepository.findByEmail(registrationRequest.email)

        if (existingPendingUserOptional.isPresent) {
            val existingPendingUser = existingPendingUserOptional.get()
            val verificationCode = generateVerificationCode()
            existingPendingUser.verificationCode = verificationCode
            pendingUserRepository.save(existingPendingUser)

            try {
                emailService.sendVerificationEmail(registrationRequest.email, verificationCode)
            } catch (e: Exception) {
                throw IllegalStateException(GlobalExceptionHandler.EMAIL_SENDING_FAILED_MESSAGE)
            }

            return "Код повторно отправлен. Проверьте вашу почту."
        } else {
            val verificationCode = generateVerificationCode()

            val pendingUser = PendingUser(
                name = registrationRequest.name,
                password = passwordEncoder.encode(registrationRequest.password),
                email = registrationRequest.email,
                role = Role.USER,
                verificationCode = verificationCode
            )

            pendingUserRepository.save(pendingUser)

            try {
                emailService.sendVerificationEmail(registrationRequest.email, verificationCode)
            } catch (e: Exception) {
                pendingUserRepository.delete(pendingUser)
                throw IllegalStateException(GlobalExceptionHandler.EMAIL_SENDING_FAILED_MESSAGE)
            }

            return "Код отправлен. Проверьте вашу почту."
        }
    }

    @Transactional
    override fun verifyEmail(email: String, verificationCode: String): UserEntity {
        val pendingUserOptional = pendingUserRepository.findByEmail(email)
        val pendingUser =
            pendingUserOptional.orElseThrow { NoSuchElementException("Не найден запрос на регистрацию или пользователь уже существует $email") }

        if (pendingUser.verificationCode == verificationCode) {
            val newUser = UserEntity(
                name = pendingUser.name,
                password = pendingUser.password,
                email = pendingUser.email,
                role = pendingUser.role,
                avatar = null,
                coins = 100,
                isEmailVerified = true,
                verificationCode = null,
                lastLoginTime = LocalDateTime.now(),
                registrationTime = LocalDateTime.now(),
                streak = 0,
                xp = 0
            )
            val savedUser = userRepository.save(newUser)
            pendingUserRepository.delete(pendingUser)
            return savedUser
        } else {
            throw IllegalArgumentException(GlobalExceptionHandler.INVALID_VERIFICATION_CODE_MESSAGE)
        }
    }

    override fun resendVerificationCode(email: String) {
        val pendingUserOptional = pendingUserRepository.findByEmail(email)
        if (pendingUserOptional.isPresent) {
            val pendingUser = pendingUserOptional.get()
            val newVerificationCode = generateVerificationCode()
            pendingUser.verificationCode = newVerificationCode
            pendingUserRepository.save(pendingUser)
            emailService.sendVerificationEmail(email, newVerificationCode)
            return
        }

        val userOptional = userRepository.findByEmail(email)
        val user =
            userOptional.orElseThrow { NoSuchElementException("Запрос на регистрацию или пользователь с email $email не найден") }

        if (user.isEmailVerified) {
            throw IllegalArgumentException(GlobalExceptionHandler.EMAIL_ALREADY_VERIFIED_MESSAGE)
        }

        val newVerificationCode = generateVerificationCode()
        user.verificationCode = newVerificationCode
        userRepository.save(user)

        emailService.sendVerificationEmail(email, newVerificationCode)
        return
    }

    override fun changeCoins(amount: Int) {
        val user = getCurrentUser()
        //TODO
    }

    private fun generateVerificationCode(): String {
        val random = Random()
        return String.format("%06d", random.nextInt(1000000))
    }

    override fun updateUserAvatar(avatar: String): UserEntity {
        try {
            URL(avatar)
        } catch (e: MalformedURLException) {
            throw IllegalArgumentException("Некорректный URL аватара")
        }
        val user = getCurrentUser()
        user.avatar = avatar
        return userRepository.save(user)
    }

    override fun getCurrentUser(): UserEntity {
        val userEmail = getCurrentUserEmail()
        return getUserByEmail(userEmail)
    }

    override fun getUserByEmail(email: String): UserEntity {
        val user = userRepository.findByEmail(email)
            .orElseThrow { NoSuchElementException("Пользователь с email $email не найден") }
        user.lastLoginTime = LocalDateTime.now()
        return userRepository.save(user)
    }

    override fun getAllUsers(): List<UserEntity> {
        return userRepository.findAll()
    }

    override fun getUserById(id: Long): UserEntity {
        return userRepository.findById(id)
            .orElseThrow { EntityNotFoundException("Пользователь с id $id не найден") }
    }

    override fun updateUser(updateProfileRequest: UpdateProfileRequest): UserEntity {
        val authentication = SecurityContextHolder.getContext().authentication
        val email = authentication.name

        val existingUser = userRepository.findByEmail(email)
            .orElseThrow { NoSuchElementException("Пользователь не найден") }

        if (!passwordEncoder.matches(updateProfileRequest.currentPassword, existingUser.password)) {
            throw IllegalArgumentException(GlobalExceptionHandler.WRONG_CURRENT_PASSWORD_MESSAGE)
        }

        updateProfileRequest.name?.let {
            if (!userValidator.checkName(it).isSuccess) {
                throw IllegalArgumentException(userValidator.checkName(it).message)
            }
        }

        updateProfileRequest.email?.let {
            if (!userValidator.checkEmailForUpdate(it, email).isSuccess) {
                throw IllegalArgumentException(userValidator.checkEmailForUpdate(it, email).message)
            }
        }

        updateProfileRequest.newPassword?.let {
            if (!userValidator.checkPassword(it).isSuccess) {
                throw IllegalArgumentException(userValidator.checkPassword(it).message)
            }
        }

        val updatedUser = existingUser.copy(
            name = updateProfileRequest.name ?: existingUser.name,
            avatar = updateProfileRequest.avatarUrl ?: existingUser.avatar,
            email = updateProfileRequest.email ?: existingUser.email,
            password = updateProfileRequest.newPassword?.let { passwordEncoder.encode(it) } ?: existingUser.password
        )
        return userRepository.save(updatedUser)
    }

    @Transactional
    override fun deleteUser(id: Long) {
        if (!userRepository.existsById(id)) {
            throw EntityNotFoundException("Пользователь с id $id не найден")
        }
        userRepository.deleteById(id)
    }
}