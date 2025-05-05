package com.vangelnum.ailingo.shop.serviceimpl

import com.vangelnum.ailingo.shop.entity.ShopItem
import com.vangelnum.ailingo.shop.repository.ShopItemRepository
import com.vangelnum.ailingo.shop.service.ShopService
import com.vangelnum.ailingo.user.service.UserService
import jakarta.persistence.EntityNotFoundException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ShopServiceImpl(
    private val shopItemRepository: ShopItemRepository,
    private val userService: UserService
) : ShopService {

    override fun getAvailableItems(): List<ShopItem> {
        return shopItemRepository.findAll()
    }

    @Transactional
    override fun purchaseCoins(itemId: Long) {
        val item = shopItemRepository.findById(itemId)
            .orElseThrow { EntityNotFoundException("Товар с id $itemId не найден") }

        userService.changeCoins(item.coinsToGive)
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    override fun createShopItem(shopItem: ShopItem): ShopItem {
        return shopItemRepository.save(shopItem)
    }
}