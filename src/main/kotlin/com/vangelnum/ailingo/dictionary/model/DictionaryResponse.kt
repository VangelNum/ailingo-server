package com.vangelnum.ailingo.dictionary.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class DictionaryResponse(
    val word: String,
    val phonetics: List<Phonetic>,
    val meanings: List<Meaning>
)