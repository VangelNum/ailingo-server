package com.vangelnum.ailingo.generation.controller

import com.vangelnum.ailingo.generation.service.GenerationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Генерация")
@RestController
@RequestMapping("/api/v1/generate")
class GenerationController(
    private val generationService: GenerationService
) {
    @GetMapping("/image/{prompt}")
    @Operation(summary = "Сгенерировать изображение на основе запроса")
    suspend fun generateImage(
        @PathVariable prompt: String,
        @RequestParam(required = false) model: String?,
        @RequestParam(required = false) seed: Int?,
        @RequestParam(required = false) width: Int?,
        @RequestParam(required = false) height: Int?,
        @RequestParam(required = false) nologo: Boolean?,
        @RequestParam(required = false) private: Boolean?,
        @RequestParam(required = false) enhance: Boolean?,
        @RequestParam(required = false) safe: Boolean?
    ): ResponseEntity<ByteArrayResource> {
        val imageBytes =
            generationService.generateImage(prompt, model, seed, width, height, nologo, private, enhance, safe)
        val resource = ByteArrayResource(imageBytes)
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .body(resource)
    }

    @GetMapping("/image/models")
    @Operation(summary = "Получить список доступных моделей для генерации изображений")
    suspend fun getListOfModels(): ResponseEntity<List<String>> {
        val models = generationService.getImageModels()
        return ResponseEntity.ok(models)
    }

    @GetMapping("/text/{prompt}")
    @Operation(summary = "Сгенерировать текст на основе запроса")
    suspend fun generateText(
        @PathVariable prompt: String,
        @RequestParam(required = false) model: String?,
        @RequestParam(required = false) seed: Int?,
        @RequestParam(required = false) json: Boolean?,
        @RequestParam(required = false) system: String?
    ): ResponseEntity<String> {
        val generatedText = generationService.generateText(prompt, model, seed, json, system)
        return ResponseEntity.ok(generatedText)
    }

    @GetMapping("/text/models")
    @Operation(summary = "Получить список доступных моделей для генерации текста")
    suspend fun getListOfTextModels(): ResponseEntity<List<String>> {
        val models = generationService.getTextModels()
        return ResponseEntity.ok(models)
    }
}