package com.vangelnum.ailingo.upload.model

data class ImageData(
    val id: String,
    val title: String,
    val url_viewer: String,
    val url: String,
    val display_url: String,
    val width: String,
    val height: String,
    val size: String,
    val time: String,
    val expiration: String,
    val image: ImageInfo,
    val thumb: ImageInfo,
    val medium: ImageInfo?,
    val delete_url: String
)