package com.vangelnum.ailingo.shop.controller

import com.vangelnum.ailingo.shop.entity.ShopItem
import com.vangelnum.ailingo.shop.service.ShopService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Магазин")
@RestController
@RequestMapping("/api/v1/shop")
class ShopController(
    private val shopService: ShopService
) {

    @Operation(summary = "Получить список товаров в магазине")
    @GetMapping("/items")
    fun getAvailableItems(): ResponseEntity<List<ShopItem>> {
        return ResponseEntity.ok(shopService.getAvailableItems())
    }

    @Operation(summary = "Купить монеты")
    @PostMapping("/purchase")
    fun purchaseCoins(@RequestParam itemId: Long): ResponseEntity<String> {
        return try {
            shopService.purchaseCoins(itemId)
            ResponseEntity.ok("Монеты успешно начислены")
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(e.message)
        }
    }

    @Operation(summary = "Добавить один товар в магазин")
    @PostMapping("/add-item")
    fun createShopItem(@RequestBody shopItem: ShopItem): ResponseEntity<ShopItem> {
        val createdItem = shopService.createShopItem(shopItem)
        return ResponseEntity.status(HttpStatus.CREATED).body(createdItem)
    }

    @Operation(summary = "Добавить несколько товаров в магазин")
    @PostMapping("/add-items")
    fun createShopItems(@RequestBody shopItems: List<ShopItem>): ResponseEntity<List<ShopItem>> {
        val createdItems = shopService.createShopItems(shopItems)
        return ResponseEntity.status(HttpStatus.CREATED).body(createdItems)
    }


    @Operation(summary = "Удалить товар из магазина")
    @DeleteMapping("/delete-item/{itemId}")
    fun deleteShopItem(@PathVariable itemId: Long): ResponseEntity<String> {
        return try {
            shopService.deleteShopItem(itemId)
            ResponseEntity.ok("Товар успешно удален")
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(e.message)
        }
    }

    @Operation(summary = "Удалить все товары из магазина (Только для ADMIN)")
    @DeleteMapping("/delete-all-items")
    fun deleteAllShopItems(): ResponseEntity<String> {
        shopService.deleteAllShopItems()
        return ResponseEntity.ok("Все товары успешно удалены")
    }
}