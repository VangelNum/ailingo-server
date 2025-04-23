package com.vangelnum.ailingo.dictionary.serviceimpl

import com.vangelnum.ailingo.dictionary.model.CombinedDictionaryResponse
import com.vangelnum.ailingo.dictionary.model.DictionaryResponse
import com.vangelnum.ailingo.dictionary.model.YandexDictionaryResponse
import com.vangelnum.ailingo.dictionary.service.DictionaryService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import org.springframework.web.server.ResponseStatusException

@Service
class DictionaryServiceImpl(
    private val restTemplate: RestTemplate
) : DictionaryService {

    companion object {
        private const val API_URL_DICTIONARYAPI = "https://api.dictionaryapi.dev/api/v2/entries/en/{word}"
        private const val API_URL_YANDEX_DICT = "https://dictionary.yandex.net/api/v1/dicservice.json/lookup?key={key}&lang={lang}&text={word}"
    }

    @Value("\${yandex.dictionary.api.key}")
    private lateinit var yandexApiKey: String

    override fun getWordDefinition(word: String): CombinedDictionaryResponse {
        val dictionaryApiDevDefinitions = getDictionaryApiDevDefinition(word)
        val yandexDefinitions = getYandexDictionaryDefinition(word)

        return CombinedDictionaryResponse(
            dictionaryApiDevResponses = dictionaryApiDevDefinitions,
            yandexDictionaryResponse = yandexDefinitions
        )
    }

    private fun getDictionaryApiDevDefinition(word: String): List<DictionaryResponse> {
        return try {
            val response = restTemplate.getForEntity(
                API_URL_DICTIONARYAPI,
                Array<DictionaryResponse>::class.java,
                word
            )
            response.body?.toList() ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Слово '$word' не найдено в DictionaryAPI"
            )
        } catch (e: HttpClientErrorException.NotFound) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Слово '$word' не найдено в DictionaryAPI")
        } catch (e: RestClientException) {
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ошибка при получении определения слова из DictionaryAPI: ${e.message}"
            )
        }
    }

    private fun getYandexDictionaryDefinition(word: String): YandexDictionaryResponse? {
        return try {
            val response = restTemplate.getForEntity(
                API_URL_YANDEX_DICT,
                YandexDictionaryResponse::class.java,
                yandexApiKey,
                "en-ru",
                word
            )
            response.body
        } catch (e: HttpClientErrorException.NotFound) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Слово '$word' не найдено в Yandex Dictionary")
        } catch (e: RestClientException) {
            null
        }
    }
}