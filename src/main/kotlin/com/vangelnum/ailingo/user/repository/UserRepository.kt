package com.vangelnum.ailingo.user.repository

import com.vangelnum.ailingo.user.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UserRepository : JpaRepository<UserEntity, Long> {
    fun findByEmail(email: String): Optional<UserEntity>
    fun findByName(name: String): Optional<UserEntity>
    fun findTop100ByOrderByStreakDesc(): List<UserEntity>
}