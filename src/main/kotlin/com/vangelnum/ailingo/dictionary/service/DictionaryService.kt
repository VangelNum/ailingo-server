package com.vangelnum.ailingo.dictionary.service

import com.vangelnum.ailingo.dictionary.model.CombinedDictionaryResponse

interface DictionaryService {
    fun getWordDefinition(word: String): CombinedDictionaryResponse
}