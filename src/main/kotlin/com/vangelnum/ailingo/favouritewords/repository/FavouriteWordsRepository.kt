package com.vangelnum.ailingo.favouritewords.repository

import com.vangelnum.ailingo.favouritewords.entity.FavouriteWordsEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface FavoriteWordsRepository : JpaRepository<FavouriteWordsEntity, Long> {
    fun findByUserId(userId: Long): List<FavouriteWordsEntity>
    fun findByUserIdAndWord(userId: Long, word: String): FavouriteWordsEntity?
    fun existsByUserIdAndWord(userId: Long, word: String): Boolean
}