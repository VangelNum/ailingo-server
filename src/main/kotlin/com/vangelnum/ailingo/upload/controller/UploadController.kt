package com.vangelnum.ailingo.upload.controller

import com.vangelnum.ailingo.upload.model.ImageUploadRequest
import com.vangelnum.ailingo.upload.model.ImgbbResponse
import com.vangelnum.ailingo.upload.service.UploadService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Загрузка на сервер")
@RestController
@RequestMapping("/api/v1/upload")
class UploadController(
    private val uploadService: UploadService
) {
    @Operation(summary = "Изображение")
    @PostMapping("/image")
    suspend fun uploadImage(@RequestBody request: ImageUploadRequest): ResponseEntity<ImgbbResponse> {
        return try {
            val imageUrl = uploadService.uploadImage(request)
            ResponseEntity.ok(imageUrl)
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }
}