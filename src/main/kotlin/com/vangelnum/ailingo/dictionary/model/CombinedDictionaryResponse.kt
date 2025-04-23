package com.vangelnum.ailingo.dictionary.model

data class CombinedDictionaryResponse(
    val dictionaryApiDevResponses: List<DictionaryResponse>?,
    val yandexDictionaryResponse: YandexDictionaryResponse?
)