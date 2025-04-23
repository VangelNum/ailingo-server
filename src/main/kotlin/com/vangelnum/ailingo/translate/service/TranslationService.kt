package com.vangelnum.ailingo.translate.service

interface TranslationService {
    fun translate(text: String, langpair: String): String?
}