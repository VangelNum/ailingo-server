package com.vangelnum.ailingo.dictionary.model

data class YandexDictionaryResponse(
    val def: List<YandexDef>? = null,
    val nmt_code: Int? = null,
    val code: Int? = null
)