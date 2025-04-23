package com.vangelnum.ailingo.pendinguser.entity

import com.vangelnum.ailingo.core.enums.Role
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "pending_users")
data class PendingUser(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val name: String,
    val password: String,
    @Column(unique = true)
    val email: String,
    @Enumerated(EnumType.STRING)
    val role: Role,
    var verificationCode: String?
)