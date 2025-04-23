package com.vangelnum.ailingo.dictionary.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class Phonetic(
    val text: String? = null,
    val audio: String? = null
)