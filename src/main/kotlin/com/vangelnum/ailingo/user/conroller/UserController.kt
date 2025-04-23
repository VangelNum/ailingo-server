package com.vangelnum.ailingo.user.conroller

import com.vangelnum.ailingo.core.utils.getCurrentUserEmail
import com.vangelnum.ailingo.user.entity.UserEntity
import com.vangelnum.ailingo.user.model.RegistrationRequest
import com.vangelnum.ailingo.user.model.ResendVerificationCodeRequest
import com.vangelnum.ailingo.user.model.UpdateAvatarRequest
import com.vangelnum.ailingo.user.model.UpdateProfileRequest
import com.vangelnum.ailingo.user.model.VerificationRequest
import com.vangelnum.ailingo.user.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Пользователь")
@RestController
@RequestMapping("/api/v1/user")
class UserController(
    private val userService: UserService,
) {
    @Operation(summary = "Регистрация пользователя")
    @PostMapping("/register")
    fun registerUser(@RequestBody registrationRequest: RegistrationRequest): ResponseEntity<String> {
        val message = userService.registerUser(registrationRequest)
        return ResponseEntity.status(HttpStatus.CREATED).body(message)
    }

    @Operation(summary = "Верификация email пользователя")
    @PostMapping("/verify-email")
    fun verifyEmail(@RequestBody verificationRequest: VerificationRequest): ResponseEntity<UserEntity> {
        val user = userService.verifyEmail(verificationRequest.email, verificationRequest.verificationCode)
        return ResponseEntity.ok(user)
    }

    @Operation(summary = "Переотправка кода верификации email")
    @PostMapping("/resend-verification-code")
    fun resendVerificationCode(@RequestBody resendVerificationCodeRequest: ResendVerificationCodeRequest): ResponseEntity<String> {
        userService.resendVerificationCode(resendVerificationCodeRequest.email)
        return ResponseEntity.ok("Новый код верификации отправлен на ваш email")
    }

    @Operation(summary = "Информация о текущем пользователе")
    @GetMapping("/me")
    fun getCurrentUserInfo(): ResponseEntity<UserEntity> {
        val email = getCurrentUserEmail()
        return userService.getUserByEmail(email).let { ResponseEntity.ok(it) }
    }

    @Operation(summary = "Получение списка пользователей")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun getAllUsers(): ResponseEntity<List<UserEntity>> {
        return ResponseEntity.ok(userService.getAllUsers())
    }

    @Operation(summary = "Получение пользователя по ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun getUserById(@PathVariable id: Long): ResponseEntity<UserEntity> {
        return userService.getUserById(id).let { ResponseEntity.ok(it) }
    }

    @Operation(summary = "Обновление данных пользователя")
    @PutMapping
    fun updateUser(
        @RequestBody updateProfileRequest: UpdateProfileRequest
    ): ResponseEntity<UserEntity> {
        val updatedUser = userService.updateUser(updateProfileRequest)
        return ResponseEntity.ok(updatedUser)
    }

    @Operation(summary = "Обновление аватара пользователя")
    @PutMapping("/avatar")
    fun updateAvatar(@RequestBody updateAvatarRequest: UpdateAvatarRequest): ResponseEntity<UserEntity> {
        val updatedUser = userService.updateUserAvatar(updateAvatarRequest.avatarUrl)
        return ResponseEntity.ok(updatedUser)
    }

    @Operation(summary = "Удаление пользователя по ID")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun deleteUser(@PathVariable id: Long): ResponseEntity<Void> {
        userService.deleteUser(id)
        return ResponseEntity.noContent().build()
    }
}