package com.vangelnum.ailingo.upload.serviceimpl

import com.vangelnum.ailingo.upload.model.ImageUploadRequest
import com.vangelnum.ailingo.upload.model.ImgbbResponse
import com.vangelnum.ailingo.upload.service.UploadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestTemplate

@Service
class UploadServiceImpl(
    private val restTemplate: RestTemplate
) : UploadService {

    companion object {
        private const val IMG_BB_API_KEY = "f90248ad8f4b1e262a5e8e7603645cc1"
        private const val IMG_BB_BASE_URL = "https://api.imgbb.com/1/upload"
    }

    override suspend fun uploadImage(request: ImageUploadRequest): ImgbbResponse {
        return withContext(Dispatchers.IO) {
            val headers = HttpHeaders()
            headers.contentType = MediaType.MULTIPART_FORM_DATA

            val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
            body.add("key", IMG_BB_API_KEY)
            body.add("image", request.image)
            body.add("name", request.name ?: "")
            body.add("expiration", request.expiration?.toString() ?: "")

            val requestEntity = HttpEntity(body, headers)

            try {
                restTemplate.postForObject(IMG_BB_BASE_URL, requestEntity, ImgbbResponse::class.java)
            } catch (e: Exception) {
                println("Error uploading image: ${e.message}")
                null
            } ?: ImgbbResponse(null, false, 500)
        }
    }
}