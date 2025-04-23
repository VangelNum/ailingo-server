package com.vangelnum.ailingo.translate.model

import com.fasterxml.jackson.annotation.JsonProperty

data class TranslationResponse(
    @JsonProperty("responseData")
    val responseData: ResponseData?,
    @JsonProperty("quotaFinished")
    val quotaFinished: Boolean?,
    @JsonProperty("responseDetails")
    val responseDetails: String?,
    @JsonProperty("responseStatus")
    val responseStatus: Int?,
    @JsonProperty("matches")
    val matches: List<Match>?
)