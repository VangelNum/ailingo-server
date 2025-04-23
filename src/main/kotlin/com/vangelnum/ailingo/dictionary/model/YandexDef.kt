package com.vangelnum.ailingo.dictionary.model

data class YandexDef(
    val text: String? = null,
    val pos: String? = null,
    val ts: String? = null,
    val tr: List<YandexTranslation>? = null
)