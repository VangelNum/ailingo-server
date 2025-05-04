package com.vangelnum.ailingo.achievement.controller

import com.vangelnum.ailingo.achievement.model.AchievementResponse
import com.vangelnum.ailingo.user.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Достижения пользователя")
@RestController
@RequestMapping("/api/v1/user/achievements")
class AchievementController(
    private val userService: UserService
) {
    @Operation(summary = "Получить список доступных достижений")
    @GetMapping
    fun getAvailableAchievements(): ResponseEntity<List<AchievementResponse>> {
        return ResponseEntity.ok(userService.getAvailableAchievements())
    }

    @Operation(summary = "Забрать достижение")
    @PostMapping("/{achievementId}/claim")
    fun claimAchievement(@PathVariable achievementId: Long): ResponseEntity<Boolean> {
        val response = userService.claimAchievement(achievementId)
        return ResponseEntity.ok(response)
    }
}