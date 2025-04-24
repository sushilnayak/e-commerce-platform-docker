package com.nayak.productservice.dto

import java.time.Instant

/**
 * DTO representing the response from the inventory-service
 * when querying the stock level for a specific product.
 */
data class InventoryStatusDTO(
    // The product ID this status refers to
    val productId: String,

    // The current quantity available in inventory
    val quantityOnHand: Int,

    // Optional: Timestamp indicating when this inventory level was last updated
    val lastUpdatedAt: Instant? = null
)