package com.vangelnum.ailingo.shop.repository

import com.vangelnum.ailingo.shop.entity.ShopItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ShopItemRepository : JpaRepository<ShopItem, Long>