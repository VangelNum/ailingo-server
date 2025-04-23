package com.vangelnum.ailingo.translate.serviceimpl

import com.vangelnum.ailingo.translate.model.TranslationResponse
import com.vangelnum.ailingo.translate.service.TranslationService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

@Service
class TranslationServiceImpl(
    private val restTemplate: RestTemplate
) : TranslationService {

    companion object {
        const val BASE_URL = "https://api.mymemory.translated.net"
    }

    private val logger = LoggerFactory.getLogger(TranslationServiceImpl::class.java)

    override fun translate(text: String, langpair: String): String? {
        val uri = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/get")
            .queryParam("q", text)
            .queryParam("langpair", langpair)
            .build()
            .toUri()

        return try {
            val response = restTemplate.getForEntity(uri, TranslationResponse::class.java)
            if (response.statusCode.is2xxSuccessful) {
                response.body?.responseData?.translatedText
            } else {
                logger.info("Error response from translation API: ${response.statusCode}")
                null
            }
        } catch (e: Exception) {
            logger.info("Exception during translation: ${e.message}")
            null
        }
    }
}