package com.vangelnum.ailingo.lecture.service


interface LectureContentService {
    fun getLectureContent(url: String): String?
}