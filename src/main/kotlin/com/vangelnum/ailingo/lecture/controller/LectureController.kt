package com.vangelnum.ailingo.lecture.controller

import com.vangelnum.ailingo.lecture.service.LectureContentService
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Лекции")
@RestController
@RequestMapping("/api/v1/lecture")
class LectureController(
    private val lectureContentService: LectureContentService
) {
    @GetMapping("/content")
    fun getLectureContent(@RequestParam url: String): ResponseEntity<String> {
        val htmlContent = lectureContentService.getLectureContent(url)

        return if (htmlContent != null) {
            ResponseEntity.ok(htmlContent)
        } else {
            ResponseEntity.internalServerError().body("Ошибка при получении контента.")
        }
    }
}