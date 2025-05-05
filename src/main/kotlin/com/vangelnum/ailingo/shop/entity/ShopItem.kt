package com.vangelnum.ailingo.shop.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "shop_items")
data class ShopItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val name: String,

    val description: String,

    val price: Int,

    val coinsToGive: Int,

    val imageUrl: String? = null
)