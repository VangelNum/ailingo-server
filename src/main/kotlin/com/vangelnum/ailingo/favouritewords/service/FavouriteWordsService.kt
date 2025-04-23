package com.vangelnum.ailingo.favouritewords.service

interface FavouriteWordsService {
    fun getFavoriteWords(): List<String>
    fun addWordToFavorites(word: String)
    fun removeWordFromFavorites(word: String)
    fun removeAllFromFavorites()
}