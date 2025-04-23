package com.vangelnum.ailingo.translate.model

import com.fasterxml.jackson.annotation.JsonProperty

data class ResponseData(
    @JsonProperty("translatedText")
    val translatedText: String?,
    @JsonProperty("match")
    val match: Double?
)