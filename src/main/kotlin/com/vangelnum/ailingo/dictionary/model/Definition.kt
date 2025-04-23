package com.vangelnum.ailingo.dictionary.model

data class Definition(
    val definition: String,
    val synonyms: List<String>? = null,
    val antonyms: List<String>? = null,
    val example: String?
)