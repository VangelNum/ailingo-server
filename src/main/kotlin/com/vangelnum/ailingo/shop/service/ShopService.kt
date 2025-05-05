package com.vangelnum.ailingo.shop.service

import com.vangelnum.ailingo.shop.entity.ShopItem

interface ShopService {
    fun getAvailableItems(): List<ShopItem>
    fun purchaseCoins(itemId: Long)
    fun createShopItem(shopItem: ShopItem): ShopItem
}