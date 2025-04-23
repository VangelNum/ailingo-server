package com.vangelnum.ailingo.upload.model

data class ImageUploadRequest(
    val image: String,
    val name: String? = null,
    val expiration: Int? = null
)