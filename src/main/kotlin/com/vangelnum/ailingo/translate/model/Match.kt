package com.vangelnum.ailingo.translate.model

import com.fasterxml.jackson.annotation.JsonProperty

data class Match(
    @JsonProperty("id")
    val id: String?,
    @JsonProperty("segment")
    val segment: String?,
    @JsonProperty("translation")
    val translation: String?,
    @JsonProperty("source")
    val source: String?,
    @JsonProperty("target")
    val target: String?,
    @JsonProperty("quality")
    val quality: String?,
    @JsonProperty("usage-count")
    val usageCount: Int?,
    @JsonProperty("subject")
    val subject: String?,
    @JsonProperty("created-by")
    val createdBy: String?,
    @JsonProperty("last-updated-by")
    val lastUpdatedBy: String?,
    @JsonProperty("create-date")
    val createDate: String?,
    @JsonProperty("last-update-date")
    val lastUpdateDate: String?,
    @JsonProperty("match")
    val match: Double?,
    @JsonProperty("penalty")
    val penalty: Int?
)