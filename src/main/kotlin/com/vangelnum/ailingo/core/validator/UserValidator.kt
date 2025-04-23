package com.vangelnum.ailingo.core.validator

import com.vangelnum.ailingo.user.model.RegistrationRequest
import com.vangelnum.ailingo.user.repository.UserRepository
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils
import java.util.stream.Stream

@Component
class UserValidator(
    private val userRepository: UserRepository
) {
    companion object {
        private val EMAIL_VALIDATION_REGEX = Regex("^[a-zA-Z0-9_!#\$%&’*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+\$")
        private val AVATAR_URL_VALIDATION_REGEX =
            Regex("^(https?|ftp)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]\$")
    }

    fun checkUser(registrationRequest: RegistrationRequest): Status {
        return Stream.of(
            checkName(registrationRequest.name),
            checkPassword(registrationRequest.password),
            checkEmail(registrationRequest.email),
        )
            .filter { !it.isSuccess }
            .findFirst()
            .orElse(Status(true, "Данные валидны"))
    }

    fun checkAvatarUrl(avatarUrl: String): Status {
        if (!AVATAR_URL_VALIDATION_REGEX.matches(avatarUrl)) {
            return Status(false, "Неверный формат ссылки")
        }
        return Status(true)
    }

    fun checkName(name: String): Status {
        if (!StringUtils.hasText(name)) {
            return Status(false, "Имя не может быть пустым")
        }
        if (name.length < 2 || name.length > 30) {
            return Status(false, "Имя должно содержать не менее 2 и не более 30 символов")
        }
        return Status(true)
    }

    fun checkPassword(password: String): Status {
        if (!StringUtils.hasText(password)) {
            return Status(false, "Пароль не может быть пустым")
        }
        if (password.length < 8 || password.length > 30) {
            return Status(false, "Пароль должен содержать не менее 8 и не более 30 символов")
        }
        return Status(true)
    }

    fun checkEmail(email: String): Status {
        if (!StringUtils.hasText(email)) {
            return Status(false, "Почта не может быть пустой")
        }
        if (!EMAIL_VALIDATION_REGEX.matches(email)) {
            return Status(false, "Почта имеет неверный формат")
        }
        if (userRepository.findByEmail(email).isPresent) {
            return Status(false, "Пользователь с таким e-mail уже существует")
        }
        return Status(true)
    }

    fun checkEmailForUpdate(email: String, currentEmail: String): Status {
        if (!StringUtils.hasText(email)) {
            return Status(false, "Почта не может быть пустой")
        }
        if (!EMAIL_VALIDATION_REGEX.matches(email)) {
            return Status(false, "Почта имеет неверный формат")
        }
        if (email != currentEmail && userRepository.findByEmail(email).isPresent) {
            return Status(false, "Пользователь с таким e-mail уже существует")
        }
        return Status(true)
    }
}