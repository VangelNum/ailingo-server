package com.vangelnum.ailingo.user.serviceimpl

import com.vangelnum.ailingo.achievement.entity.AchievementEntity
import com.vangelnum.ailingo.achievement.model.AchievementResponse
import com.vangelnum.ailingo.achievement.model.AchievementType
import com.vangelnum.ailingo.achievement.repository.AchievementRepository
import com.vangelnum.ailingo.chat.model.MessageType
import com.vangelnum.ailingo.chat.repository.MessageHistoryRepository
import com.vangelnum.ailingo.core.GlobalExceptionHandler
import com.vangelnum.ailingo.core.InsufficientFundsException
import com.vangelnum.ailingo.core.enums.Role
import com.vangelnum.ailingo.core.utils.getCurrentUserEmail
import com.vangelnum.ailingo.core.validator.UserValidator
import com.vangelnum.ailingo.favouritewords.repository.FavoriteWordsRepository
import com.vangelnum.ailingo.pendinguser.entity.PendingUser
import com.vangelnum.ailingo.pendinguser.repository.PendingUserRepository
import com.vangelnum.ailingo.topics.repository.TopicRepository
import com.vangelnum.ailingo.user.entity.UserEntity
import com.vangelnum.ailingo.user.model.DailyLoginResponse
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
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Random

@Service
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val pendingUserRepository: PendingUserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val userValidator: UserValidator,
    private val emailService: EmailService,
    private val messageHistoryRepository: MessageHistoryRepository,
    private val favoriteWordsRepository: FavoriteWordsRepository,
    private val achievementRepository: AchievementRepository,
    private val topicRepository: TopicRepository
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

            // Проверка и выдача достижения за первый логин
            checkAndGrantFirstLoginAchievement(savedUser)

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
        val newBalance = user.coins + amount

        if (newBalance < 0 && amount < 0) {
            throw InsufficientFundsException("Insufficient funds.  Cannot deduct $amount coins.  Current balance: ${user.coins}")
        }

        user.coins = newBalance
        userRepository.save(user)
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
        val user = userRepository.findById(id).orElseThrow { EntityNotFoundException("Пользователь с id $id не найден") }
        messageHistoryRepository.deleteAllByOwnerId(id)
        favoriteWordsRepository.deleteAllByUserId(id)
        achievementRepository.deleteAllByUserId(id)
        topicRepository.deleteByCreator(user)
        userRepository.deleteById(id)
    }

    override fun addXp(xp: Int) {
        val user = getCurrentUser()
        user.xp += xp
        userRepository.save(user)
    }

    override fun claimDailyLoginReward(): DailyLoginResponse {
        val user = getCurrentUser()
        val now = LocalDateTime.now()

        val (isAvailable, remainingTime) = isDailyRewardAvailable(user, now)

        if (!isAvailable) {
            return DailyLoginResponse(
                streak = user.streak,
                coinsRewarded = 0,
                message = "Награда уже получена.",
                totalRemainingTimeSeconds = remainingTime,
                isAvailable = false
            )
        }

        if (user.lastDailyLogin != null && ChronoUnit.DAYS.between(user.lastDailyLogin, now) > 1) {
            user.streak = 0
        }

        // Keep streak at a maximum of 10
        user.streak = minOf(user.streak + 1, 10)

        val coinsRewarded = calculateDailyReward(user.streak)

        user.coins += coinsRewarded
        user.lastDailyLogin = now
        userRepository.save(user)

        checkAndGrantStreakAchievements(user)

        return DailyLoginResponse(
            streak = user.streak,
            coinsRewarded = coinsRewarded,
            message = "Получено $coinsRewarded монет за ежедневный вход. Текущий стрик: ${user.streak}.",
            totalRemainingTimeSeconds = 0,
            isAvailable = true
        )
    }

    override fun getDailyLoginStatus(): DailyLoginResponse {
        val user = getCurrentUser()
        val now = LocalDateTime.now()

        val (isAvailable, remainingTime) = isDailyRewardAvailable(user, now)

        val nextStreak = minOf(user.streak + 1, 10)
        val coinsRewarded = if (isAvailable) {
            calculateDailyReward(nextStreak)
        } else {
            0
        }
        val message = if (isAvailable) {
            "Награда доступна для получения. Вы получите $coinsRewarded монет."
        } else {
            "Награда уже получена. Вернитесь позже."
        }

        return DailyLoginResponse(
            streak = user.streak,
            coinsRewarded = coinsRewarded,
            message = message,
            totalRemainingTimeSeconds = remainingTime,
            isAvailable = isAvailable
        )
    }

    private fun isDailyRewardAvailable(user: UserEntity, now: LocalDateTime): Pair<Boolean, Long> {
        if (user.lastDailyLogin != null && ChronoUnit.DAYS.between(user.lastDailyLogin, now) == 0L) {
            val nextAvailable = user.lastDailyLogin!!.plusDays(1)
            val remainingTime = Duration.between(now, nextAvailable).toSeconds()
            return Pair(false, remainingTime)
        }
        return Pair(true, 0)
    }

    private fun calculateDailyReward(streak: Int): Int {
        return when (streak) {
            1 -> 5
            2 -> 10
            3 -> 15
            4 -> 20
            5 -> 25
            6 -> 30
            7 -> 35
            8 -> 40
            9 -> 45
            10 -> 50 // Max reward
            else -> 5 // Default, or handle edge cases
        }
    }

    private fun checkAndGrantFirstLoginAchievement(user: UserEntity) {
        val achievementType = AchievementType.FIRST_LOGIN
        if (!hasUserClaimedAchievement(user, achievementType)) {
            grantAchievement(user, achievementType, 10, 20) // Example reward: 10 coins, 20 xp
        }
    }

    private fun checkAndGrantStreakAchievements(user: UserEntity) {
        when (user.streak) {
            3 -> grantStreakAchievement(user, AchievementType.STREAK_3_DAYS, 15, 30) // 15 coins, 30 xp
            5 -> grantStreakAchievement(user, AchievementType.STREAK_5_DAYS, 20, 40) // 20 coins, 40 xp
            7 -> grantStreakAchievement(user, AchievementType.STREAK_7_DAYS, 25, 50) // 25 coins, 50 xp
        }
    }

    // No usage in current logic, consider to use in future
    override fun checkAndGrantTopicAchievements(user: UserEntity) {
        val completedTopicsCount = messageHistoryRepository.countDistinctTopicIdsByOwnerAndFinalType(user, MessageType.FINAL)

        when (completedTopicsCount) {
            3 -> grantTopicAchievement(user, AchievementType.COMPLETE_3_TOPICS, 30, 60)
            5 -> grantTopicAchievement(user, AchievementType.COMPLETE_5_TOPICS, 40, 80)
        }
    }

    private fun grantTopicAchievement(user: UserEntity, achievementType: AchievementType, coins: Int, xp: Int) {
        if (!hasUserClaimedAchievement(user, achievementType)) {
            grantAchievement(user, achievementType, coins, xp)
        }
    }

    private fun grantStreakAchievement(user: UserEntity, achievementType: AchievementType, coins: Int, xp: Int) {
        if (!hasUserClaimedAchievement(user, achievementType)) {
            grantAchievement(user, achievementType, coins, xp)
        }
    }


    fun grantAchievement(user: UserEntity, achievementType: AchievementType, coins: Int, xp: Int) {
        val achievement = AchievementEntity(user = user, type = achievementType, claimed = true, claimDate = LocalDateTime.now())
        achievementRepository.save(achievement)

        user.coins += coins
        user.xp += xp
        userRepository.save(user)
    }

    private fun hasUserClaimedAchievement(user: UserEntity, achievementType: AchievementType): Boolean {
        return achievementRepository.existsByUserAndType(user, achievementType)
    }

    @Transactional
    override fun claimAchievement(achievementId: Long): Boolean {
        val user = getCurrentUser()
        val achievement = achievementRepository.findById(achievementId)
            .orElseThrow { EntityNotFoundException("Achievement with id $achievementId not found") }

        if (achievement.user != user) {
            throw SecurityException("You do not have permission to claim this achievement")
        }

        if (achievement.claimed) {
            throw IllegalStateException("Achievement already claimed")
        }

        achievement.claimed = true
        achievement.claimDate = LocalDateTime.now()
        achievementRepository.save(achievement)

        val (coins, xp) = getAchievementReward(achievement.type)

        user.coins += coins
        user.xp += xp
        userRepository.save(user)

        return true
    }

    private fun getAchievementReward(achievementType: AchievementType): Pair<Int, Int> {
        return when (achievementType) {
            AchievementType.FIRST_LOGIN -> Pair(10, 20)
            AchievementType.STREAK_3_DAYS -> Pair(15, 30)
            AchievementType.STREAK_5_DAYS -> Pair(20, 40)
            AchievementType.STREAK_7_DAYS -> Pair(25, 50)
            AchievementType.COMPLETE_1_TOPIC -> Pair(10, 10)
            AchievementType.COMPLETE_3_TOPICS -> Pair(30, 60)
            AchievementType.COMPLETE_5_TOPICS -> Pair(40, 80)
        }
    }

    override fun getAvailableAchievements(): List<AchievementResponse> {
        val user = getCurrentUser()
        val completedTopicsCount = messageHistoryRepository.countDistinctTopicIdsByOwnerAndFinalType(user, MessageType.FINAL)

        val achievements = mutableListOf<AchievementResponse>()

        AchievementType.entries.forEach { achievementType ->
            var achievementEntity: AchievementEntity? = achievementRepository.findByUserAndType(user, achievementType)
            val claimed = achievementEntity?.claimed ?: false
            val isAvailable = !claimed && isAchievementAvailable(user, achievementType, completedTopicsCount)

            if (isAvailable && achievementEntity == null) {
                achievementEntity = AchievementEntity(user = user, type = achievementType, claimed = false)
                achievementRepository.save(achievementEntity)
            }

            val (coins, xp) = getAchievementReward(achievementType)

            achievements.add(
                AchievementResponse(
                    type = achievementType,
                    coins = coins,
                    xp = xp,
                    claimed = claimed,
                    isAvailable = isAvailable,
                    achievementId = achievementEntity?.id,
                    description = getAchievementDescription(achievementType),
                    imageUrl = getAchievementImageUrl(achievementType),
                    claimDate = achievementEntity?.claimDate
                )
            )
        }

        return achievements
    }

    private fun isAchievementAvailable(user: UserEntity, achievementType: AchievementType, completedTopicsCount: Int): Boolean {
        return when (achievementType) {
            AchievementType.FIRST_LOGIN -> user.lastLoginTime != null
            AchievementType.STREAK_3_DAYS -> user.streak >= 3
            AchievementType.STREAK_5_DAYS -> user.streak >= 5
            AchievementType.STREAK_7_DAYS -> user.streak >= 7
            AchievementType.COMPLETE_3_TOPICS -> completedTopicsCount >= 3
            AchievementType.COMPLETE_5_TOPICS -> completedTopicsCount >= 5
            AchievementType.COMPLETE_1_TOPIC -> completedTopicsCount >= 1
        }
    }

    private fun getAchievementDescription(achievementType: AchievementType): String {
        return when (achievementType) {
            AchievementType.FIRST_LOGIN -> "Войдите в приложение в первый раз."
            AchievementType.STREAK_3_DAYS -> "Сохраните стрик 3 дня подряд."
            AchievementType.STREAK_5_DAYS -> "Сохраните стрик 5 дней подряд."
            AchievementType.STREAK_7_DAYS -> "Сохраните стрик 7 дней подряд."
            AchievementType.COMPLETE_1_TOPIC -> "Пройдите первую тему"
            AchievementType.COMPLETE_3_TOPICS -> "Пройдите 3 темы."
            AchievementType.COMPLETE_5_TOPICS -> "Пройдите 5 тем."
        }
    }

    private fun getAchievementImageUrl(achievementType: AchievementType): String {
        return when (achievementType) {
            AchievementType.FIRST_LOGIN -> "https://i.ibb.co/4Z9ZPPWh/IMG-2.png"
            AchievementType.STREAK_3_DAYS -> "https://i.ibb.co/qYWX5XZS/streak.png"
            AchievementType.STREAK_5_DAYS -> "https://i.ibb.co/qYWX5XZS/streak.png"
            AchievementType.STREAK_7_DAYS -> "https://i.ibb.co/qYWX5XZS/streak.png"
            AchievementType.COMPLETE_1_TOPIC -> "https://i.ibb.co/3Y034D6n/IMG.png"
            AchievementType.COMPLETE_3_TOPICS -> "https://i.ibb.co/3Y034D6n/IMG.png"
            AchievementType.COMPLETE_5_TOPICS -> "https://i.ibb.co/3Y034D6n/IMG.png"
        }
    }
}
