package com.vangelnum.ailingo.favouritewords.controller

import com.vangelnum.ailingo.favouritewords.service.FavouriteWordsService
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Избранные слова")
@RequestMapping("/api/v1/words/favorites")
class FavouriteWordsController(
    private val favouriteWordsService: FavouriteWordsService
) {

    @GetMapping
    fun getFavoriteWords(): ResponseEntity<List<String>> {
        val favoriteWords = favouriteWordsService.getFavoriteWords()
        return ResponseEntity.ok(favoriteWords)
    }

    @PostMapping("/{word}")
    fun addWordToFavorites(@PathVariable word: String): ResponseEntity<Void> {
        favouriteWordsService.addWordToFavorites(word)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @DeleteMapping("/{word}")
    fun removeWordFromFavorites(@PathVariable word: String): ResponseEntity<Void> {
        favouriteWordsService.removeWordFromFavorites(word)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping
    fun removeAllFavouriteWords(): ResponseEntity<Void> {
        favouriteWordsService.removeAllFromFavorites()
        return ResponseEntity.noContent().build()
    }
}
