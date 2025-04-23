package com.vangelnum.ailingo.upload.service

import com.vangelnum.ailingo.upload.model.ImageUploadRequest
import com.vangelnum.ailingo.upload.model.ImgbbResponse

interface UploadService {
    suspend fun uploadImage(request: ImageUploadRequest): ImgbbResponse
}