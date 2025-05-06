package com.vangelnum.ailingo.lecture.serviceimpl

import com.vangelnum.ailingo.lecture.service.LectureContentService
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.IOException

@Service
class LectureContentServiceImpl : LectureContentService {

    private val logger = LoggerFactory.getLogger(LectureContentServiceImpl::class.java)

    override fun getLectureContent(url: String): String? {
        return try {
            Jsoup.connect(url).get().outerHtml()
        } catch (e: IOException) {
            logger.error("Ошибка при получении HTML с $url: ${e.message}")
            null
        }
    }
}