package com.vangelnum.ailingo.speechanalysis

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@Tag(name = "Проверка произношения")
@RequestMapping("/api/v1/voice")
class VoiceController(private val voiceAnalysisService: VoiceAnalysisService) {

    @Operation(summary = "Evaluates a voice recording for speech analysis")
    @PostMapping(value = ["/evaluate"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun evaluateVoice(
        @Parameter(description = "Audio file to evaluate (WAV or MPEG)", required = true)
        @RequestPart("audio") audioFile: MultipartFile
    ): ResponseEntity<VoiceEvaluationResult> {

        if (audioFile.isEmpty) {
            return ResponseEntity.badRequest().build()
        }

        if (audioFile.contentType != "audio/wav" && audioFile.contentType != "audio/mpeg") {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build()
        }

        val result = voiceAnalysisService.evaluateVoice(audioFile)
        return ResponseEntity.ok(result)
    }

    @Operation(summary = "Evaluates text for grammar and spelling errors")
    @PostMapping(value = ["/evaluateText"], consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun evaluateText(
        @Parameter(description = "Text to evaluate", required = true)
        @RequestBody textToEvaluate: TextToEvaluate
    ): ResponseEntity<VoiceEvaluationResult> {
        if (textToEvaluate.text.isBlank()) {
            return ResponseEntity.badRequest().body(VoiceEvaluationResult(null, null, null, emptyList(), null))
        }
        val result = voiceAnalysisService.evaluateText(textToEvaluate.text)
        return ResponseEntity.ok(result)
    }
}

data class TextToEvaluate(val text: String)