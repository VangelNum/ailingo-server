package com.vangelnum.ailingo.speechanalysis

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import javazoom.jl.converter.Converter
import javazoom.jl.decoder.JavaLayerException
import org.languagetool.JLanguageTool
import org.languagetool.rules.RuleMatch
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.UnsupportedAudioFileException

data class VoiceEvaluationResult(
    val transcribedText: String?,
    val confidence: Double?,
    val wordConfidences: List<WordConfidence>?,
    val grammarErrors: List<GrammarErrorDetail>,
    val speechRateWPM: Double?
)

data class WordConfidence(
    val word: String,
    val confidence: Double,
    val start: Double,
    val end: Double
)

data class GrammarErrorDetail(
    val message: String,
    val shortMessage: String?,
    val ruleId: String,
    val offset: Int,
    val length: Int,
    val suggestedReplacements: List<String>,
    val erroneousText: String?
)

private data class GptGrammarErrorResponse(
    val message: String,
    val shortMessage: String?,
    val ruleId: String? = "GPT-Grammar",
    val offset: Int?,
    val length: Int?,
    val suggestedReplacements: List<String>?,
    val erroneousText: String?
)

@Service
class VoiceAnalysisService(
    private val languageTool: JLanguageTool,
    private val chatClient: ChatClient,
    private val objectMapper: ObjectMapper,
    @Value("\${vosk.model.path}") private val voskModelPath: String
) {

    private val voskModel: Model by lazy {
        LibVosk.setLogLevel(LogLevel.INFO)
        Model(voskModelPath)
    }

    fun evaluateVoice(audioFile: MultipartFile): VoiceEvaluationResult {
        var transcribedText: String? = null
        var overallConfidence: Double? = null
        var wordConfidences: List<WordConfidence>? = null
        var speechRate: Double? = null

        try {
            Recognizer(voskModel, 16000f).use { recognizer ->
                recognizer.setWords(true)

                val rawInputStream: InputStream = BufferedInputStream(audioFile.inputStream)
                var processedStream: AudioInputStream

                println("Content Type: ${audioFile.contentType}")

                when (audioFile.contentType) {
                    "audio/mpeg" -> {
                        println("Detected MP3 file, converting to WAV...")
                        try {
                            processedStream = convertMp3ToWav(rawInputStream)
                            println("MP3 conversion successful. Format: ${processedStream.format}")
                        } catch (e: Exception) {
                            println("MP3 to WAV conversion failed: ${e.message}")
                            throw RuntimeException("Failed to convert MP3 to WAV", e)
                        }
                    }
                    "audio/wav", "audio/x-wav" -> {
                        println("Detected WAV file.")
                        try {
                            processedStream = AudioSystem.getAudioInputStream(rawInputStream)
                            println("Original WAV format: ${processedStream.format}")
                        } catch (e: UnsupportedAudioFileException) {
                            println("Unsupported WAV format: ${e.message}")
                            throw e
                        }
                    }
                    else -> {
                        println("Attempting to process unknown content type: ${audioFile.contentType}")
                        try {
                            processedStream = AudioSystem.getAudioInputStream(rawInputStream)
                            println("Processed unknown type. Format: ${processedStream.format}")
                        } catch (e: UnsupportedAudioFileException) {
                            println("Stream of unsupported format for content type ${audioFile.contentType}: ${e.message}")
                            throw e
                        }
                    }
                }

                val voskRequiredFormat = AudioFormat(
                    16000f,
                    16,
                    1,
                    true,
                    false
                )

                if (!isFormatCompatible(processedStream.format, voskRequiredFormat)) {
                    println("Audio conversion to Vosk format is required.")
                    println("Converting from: ${processedStream.format} to $voskRequiredFormat")
                    try {
                        val convertedStream = AudioSystem.getAudioInputStream(voskRequiredFormat, processedStream)
                        processedStream = convertedStream
                        println("Conversion to Vosk format successful. New format: ${processedStream.format}")
                    } catch (e: Exception) {
                        println("Conversion to Vosk format failed: ${e.message}")
                        try {
                            processedStream.close()
                        } catch (ioe: IOException) { /* ignore */
                        }
                        throw RuntimeException(
                            "Failed to convert audio to required Vosk format (16kHz, 16-bit, Mono)",
                            e
                        )
                    }
                } else {
                    println("Audio format is already compatible with Vosk requirements.")
                }

                processAudio(recognizer, processedStream)

                val finalResultJson = recognizer.finalResult
                println("Vosk Result: $finalResultJson")

                if (finalResultJson != null && finalResultJson.contains("\"text\"")) {
                    transcribedText = extractTextFromJson(finalResultJson)
                    wordConfidences = extractWordConfidences(finalResultJson)

                    if (!wordConfidences.isNullOrEmpty()) {
                        wordConfidences?.let { wordConfidences->

                            val validConfidences = wordConfidences.mapNotNull { it.confidence.takeIf { c -> !c.isNaN() } }
                            if (validConfidences.isNotEmpty()) {
                                overallConfidence = validConfidences.average()
                            }
                            if (wordConfidences.size > 1) {
                                val firstWordStart = wordConfidences.first().start
                                val lastWordEnd = wordConfidences.last().end
                                val durationSeconds = lastWordEnd - firstWordStart
                                if (durationSeconds > 0) {
                                    speechRate = (wordConfidences.size / durationSeconds) * 60
                                }
                            } else if (wordConfidences.size == 1) {
                                val wordDuration = wordConfidences.first().end - wordConfidences.first().start
                                if (wordDuration > 0) {
                                    speechRate = (1 / wordDuration) * 60
                                }
                            }
                        }

                    }
                }
            }
        } catch (e: UnsupportedAudioFileException) {
            println("Vosk recognition error (UnsupportedAudioFileException): ${e.message}")
            e.printStackTrace()
            throw e
        } catch (e: Exception) {
            println("Vosk recognition error: ${e.message}")
            e.printStackTrace()
            throw e
        }

        val grammarErrors = mutableListOf<GrammarErrorDetail>()
        if (!transcribedText.isNullOrBlank()) {
            val languageToolErrors = analyzeGrammarWithLanguageTool(transcribedText!!)
            grammarErrors.addAll(languageToolErrors)

            if (grammarErrors.isEmpty()) {
                println("LanguageTool found no errors for transcribed text. Trying ChatGPT for grammar analysis.")
                val gptGrammarErrors = analyzeGrammarWithChatGpt(transcribedText!!)
                grammarErrors.addAll(gptGrammarErrors)
            }
        }

        return VoiceEvaluationResult(
            transcribedText = transcribedText,
            confidence = overallConfidence,
            wordConfidences = wordConfidences,
            grammarErrors = grammarErrors,
            speechRateWPM = speechRate
        )
    }

    fun evaluateText(text: String): VoiceEvaluationResult {
        val grammarErrors = analyzeGrammarWithLanguageTool(text).toMutableList()

        if (grammarErrors.isEmpty() && text.isNotBlank()) {
            println("LanguageTool found no errors for input text. Trying ChatGPT for grammar analysis.")
            val gptGrammarErrors = analyzeGrammarWithChatGpt(text)
            grammarErrors.addAll(gptGrammarErrors)
        }

        return VoiceEvaluationResult(
            transcribedText = text,
            confidence = null,
            wordConfidences = null,
            grammarErrors = grammarErrors,
            speechRateWPM = null
        )
    }

    private fun analyzeGrammarWithLanguageTool(text: String): List<GrammarErrorDetail> {
        val errors = mutableListOf<GrammarErrorDetail>()
        try {
            val matches: List<RuleMatch> = languageTool.check(text)
            matches.forEach { match ->
                val erroneousText = text.substring(match.fromPos, match.toPos)
                errors.add(
                    GrammarErrorDetail(
                        message = match.message,
                        shortMessage = match.shortMessage.takeIf { !it.isNullOrBlank() },
                        ruleId = match.rule.id,
                        offset = match.fromPos,
                        length = match.toPos - match.fromPos,
                        suggestedReplacements = match.suggestedReplacements,
                        erroneousText =erroneousText
                    )
                )
            }
        } catch (e: Exception) {
            println("LanguageTool analysis error: ${e.message}")
        }
        return errors
    }

    private fun analyzeGrammarWithChatGpt(text: String): List<GrammarErrorDetail> {
        if (text.isBlank()) return emptyList()

        val prompt = """
        You are an expert English grammar, spelling, **style, and clarity** checker.
        Analyze the following text for grammatical and spelling errors, **as well as issues related to clarity and style**. **Exclude standard capitalization issues, such as the first letter of a sentence, proper nouns (like names and places), or the pronoun 'I'. Furthermore, ignore errors related to comma usage.** Focus on other grammatical structures, word usage, punctuation (excluding basic sentence start capitalization and comma usage), spelling, **clarity (e.g., wordiness, awkward phrasing, ambiguity, run-on sentences if not stylistically justified), and style (e.g., inappropriate tone or level of formality for general communication, repetitiveness, poor word choice, overuse of passive voice where active would be stronger, or clichés).**
        Text:
        "$text"
        
        For each error or issue found, provide the following information in a JSON list format.
        Each object in the list should have these fields:
        - "message": (string) Detailed explanation of the error or issue.
        - "shortMessage": (string, optional) Brief error type (e.g., 'Spelling', 'Verb tense', 'Clarity', 'Style', 'Wordiness', 'Awkward Phrasing'). If not applicable, this can be omitted or null.
        - "ruleId": (string) A generic identifier for the error type, e.g., "GPT-Grammar", "GPT-Spelling", "GPT-Style", "GPT-Clarity", "GPT-WordChoice".
        - "offset": (integer) The starting character position of the *problematic segment* in the original text provided above. This is 0-indexed. For style/clarity issues that span a whole sentence, you can mark the beginning of the sentence.
        - "length": (integer) The length of the *problematic segment* in the original text. For style/clarity issues that span a whole sentence, this can be the length of the sentence.
        - "erroneousText": (string) The exact text segment that is considered problematic.
        - "suggestedReplacements": (list of strings) One or more suggestions for correction. If no direct replacement is suitable, especially for stylistic or clarity issues, provide an empty list or a descriptive suggestion (e.g., 'Consider rephrasing for conciseness', 'This sentence could be more active', 'Rephrase to avoid ambiguity').
        
        Example of an erroneous segment: In "He go to school.", "go" is the erroneous segment.
        If the text is "My freind is good.", the error "freind" starts at offset 3 and has length 6.
        A stylistic issue like "The report was completed by the team in a timely manner" might identify "The report was completed by the team" as problematic, suggesting "The team completed the report" for better active voice.
        
        If there are absolutely no errors or issues found, return an empty JSON list: [].
        VERY IMPORTANT: Only output the JSON list. Do not include any other text, introductory sentences, explanations, or markdown formatting like ```json ... ``` before or after the JSON list.
        Your entire response should be parsable as a JSON list.
        """.trimIndent()

        try {
            println("Sending text to ChatGPT for grammar analysis (length: ${text.length})")
            val responseContent = chatClient.prompt()
                .user(prompt)
                .call()
                .content()

            if (responseContent.isNullOrBlank()) {
                println("ChatGPT returned an empty or null response for grammar analysis.")
                return emptyList()
            }
            println("ChatGPT raw response for grammar: $responseContent")

            val gptErrorsRaw: List<GptGrammarErrorResponse> = try {
                objectMapper.readValue(responseContent, object : TypeReference<List<GptGrammarErrorResponse>>() {})
            } catch (e: Exception) {
                println("Failed to parse ChatGPT JSON response directly: ${e.message}. Trying to extract from markdown.")
                // Attempt to extract JSON if it's embedded in markdown (e.g., ```json ... ```)
                val jsonRegex = """```(?:json)?\s*([\s\S]*?)\s*```""".toRegex()
                val match = jsonRegex.find(responseContent)
                if (match != null && match.groupValues.size > 1) {
                    val extractedJson = match.groupValues[1]
                    println("Extracted JSON from markdown: $extractedJson")
                    try {
                        objectMapper.readValue(extractedJson, object : TypeReference<List<GptGrammarErrorResponse>>() {})
                    } catch (e2: Exception) {
                        println("Failed to parse extracted ChatGPT JSON response: ${e2.message}.")
                        return emptyList()
                    }
                } else {
                    println("No JSON block found in markdown, and direct parsing failed.")
                    return emptyList()
                }
            }

            return gptErrorsRaw.mapNotNull { gptError ->
                if (gptError.offset == null || gptError.length == null || gptError.offset < 0 || gptError.length <= 0 || gptError.offset + gptError.length > text.length) {
                    println("Skipping GPT error due to missing/invalid offset/length or out of bounds: '${gptError.message}' (Offset: ${gptError.offset}, Length: ${gptError.length}, TextLength: ${text.length})")
                    null // Skip if offset/length are missing, invalid, or out of bounds
                } else {
                    GrammarErrorDetail(
                        message = gptError.message,
                        shortMessage = gptError.shortMessage,
                        ruleId = gptError.ruleId ?: "GPT-UnknownRule",
                        offset = gptError.offset,
                        length = gptError.length,
                        suggestedReplacements = gptError.suggestedReplacements ?: emptyList(),
                        erroneousText = gptError.erroneousText
                    )
                }
            }

        } catch (e: Exception) {
            println("Error during ChatGPT grammar analysis call: ${e.message}")
            e.printStackTrace() // Log stack trace for detailed debugging
            return emptyList()
        }
    }

    private fun isFormatCompatible(sourceFormat: AudioFormat, targetFormat: AudioFormat): Boolean {
        return sourceFormat.encoding == targetFormat.encoding &&
                sourceFormat.sampleRate == targetFormat.sampleRate &&
                sourceFormat.sampleSizeInBits == targetFormat.sampleSizeInBits &&
                sourceFormat.channels == targetFormat.channels
    }


    private fun processAudio(recognizer: Recognizer, audioInputStream: AudioInputStream) {
        val buffer = ByteArray(4096)
        var nbytes: Int

        try {
            while (audioInputStream.read(buffer).also { nbytes = it } >= 0) {
                if (recognizer.acceptWaveForm(buffer, nbytes)) {
                    val partial = recognizer.partialResult
                    println("Partial result: $partial")
                } else {
                    val partial = recognizer.partialResult
                    println("Partial result (not accepted): $partial")
                }
            }
        } catch (e: IOException) {
            println("Error reading audio stream during Vosk processing: ${e.message}")
        } finally {
            try {
                audioInputStream.close()
            } catch (e: IOException) {
                println("Error closing audio stream: ${e.message}")
            }
        }
    }

    private fun extractTextFromJson(jsonResult: String): String? {
        return try {
            objectMapper.readTree(jsonResult).get("text")?.asText()
        } catch (e: Exception) {
            println("Error extracting 'text' from JSON: ${e.message}")
            "\"text\"\\s*:\\s*\"(.*?)\"".toRegex().find(jsonResult)?.groups?.get(1)?.value
        }
    }

    private fun extractWordConfidences(jsonResult: String): List<WordConfidence>? {
        return try {
            val node = objectMapper.readTree(jsonResult)
            val resultArray = node.get("result")
            if (resultArray != null && resultArray.isArray) {
                resultArray.map { wordNode ->
                    WordConfidence(
                        word = wordNode.get("word").asText(),
                        confidence = wordNode.get("conf").asDouble(),
                        start = wordNode.get("start").asDouble(),
                        end = wordNode.get("end").asDouble()
                    )
                }
            } else {
                null
            }
        } catch (e: Exception) {
            println("Error parsing word confidences from JSON: ${e.message}")
            null
        }
    }

    private fun convertMp3ToWav(mp3Stream: InputStream): AudioInputStream {
        val tempMp3File = File.createTempFile("temp_input_", ".mp3")
        val tempWavFile = File.createTempFile("temp_output_", ".wav")
        tempMp3File.deleteOnExit()
        tempWavFile.deleteOnExit()

        try {
            FileOutputStream(tempMp3File).use { out ->
                mp3Stream.copyTo(out)
            }
            mp3Stream.close() // Close the input stream once copied

            val converter = Converter()
            println("Converting MP3 file ${tempMp3File.absolutePath} to WAV file ${tempWavFile.absolutePath}")
            converter.convert(tempMp3File.absolutePath, tempWavFile.absolutePath)
            println("Conversion finished. Resulting WAV file size: ${tempWavFile.length()} bytes")

            if (!tempWavFile.exists() || tempWavFile.length() == 0L) {
                throw IOException("WAV file was not created or is empty after conversion from MP3.")
            }
            return AudioSystem.getAudioInputStream(BufferedInputStream(tempWavFile.inputStream()))
        } catch (e: JavaLayerException) {
            println("JLayerException during MP3 to WAV conversion: ${e.message}")
            throw RuntimeException("Error converting MP3 to WAV using JLayer", e)
        } catch (e: IOException) {
            println("IOException during MP3 to WAV conversion: ${e.message}")
            throw RuntimeException("I/O error during MP3 to WAV conversion", e)
        } catch (e: UnsupportedAudioFileException) {
            println("UnsupportedAudioFileException after MP3 to WAV conversion: ${e.message}")
            throw RuntimeException("Converted WAV file is unsupported", e)
        } finally {
            // Ensure temporary files are deleted
            if (!tempMp3File.delete()) {
                println("Warning: Failed to delete temporary MP3 file: ${tempMp3File.absolutePath}")
            }
            if (!tempWavFile.delete()) {
                println("Warning: Failed to delete temporary WAV file: ${tempWavFile.absolutePath}")
            }
        }
    }
}