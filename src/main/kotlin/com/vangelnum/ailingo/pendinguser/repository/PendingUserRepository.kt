package com.vangelnum.ailingo.pendinguser.repository

import com.vangelnum.ailingo.pendinguser.entity.PendingUser
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface PendingUserRepository : JpaRepository<PendingUser, Long> {
    fun findByEmail(email: String): Optional<PendingUser>
}