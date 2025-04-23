package com.vangelnum.ailingo.favouritewords.serviceimpl

import com.vangelnum.ailingo.favouritewords.entity.FavouriteWordsEntity
import com.vangelnum.ailingo.favouritewords.repository.FavoriteWordsRepository
import com.vangelnum.ailingo.favouritewords.service.FavouriteWordsService
import com.vangelnum.ailingo.user.entity.UserEntity
import com.vangelnum.ailingo.user.service.UserService
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FavouriteWordsServiceImpl(
    private val favoriteWordsRepository: FavoriteWordsRepository,
    private val userService: UserService
) : FavouriteWordsService {

    override fun getFavoriteWords(): List<String> {
        val user: UserEntity = userService.getCurrentUser()
        val favoriteWordsEntities = favoriteWordsRepository.findByUserId(user.id!!)
        return favoriteWordsEntities.map { it.word }
    }

    @Transactional
    override fun addWordToFavorites(word: String) {
        val user: UserEntity = userService.getCurrentUser()
        if (favoriteWordsRepository.existsByUserIdAndWord(user.id!!, word)) {
            return
        }
        val favouriteWordsEntity = FavouriteWordsEntity(word = word, userId = user.id!!)
        favoriteWordsRepository.save(favouriteWordsEntity)
    }

    @Transactional
    override fun removeWordFromFavorites(word: String) {
        val user: UserEntity = userService.getCurrentUser()
        val favouriteWordsEntity = favoriteWordsRepository.findByUserIdAndWord(user.id!!, word)
            ?: throw EntityNotFoundException("Слово $word не найдено в избранном")
        favoriteWordsRepository.delete(favouriteWordsEntity)
    }

    @Transactional
    override fun removeAllFromFavorites() {
        val user: UserEntity = userService.getCurrentUser()
        val favouriteWordsEntities = favoriteWordsRepository.findByUserId(user.id!!)
        favoriteWordsRepository.deleteAll(favouriteWordsEntities)
    }
}
