package com.nayak.productservice.dto

/**
 * DTO used as the request body when notifying the
 * notification-service about a low stock event.
 */
data class LowStockNotificationDTO(
    // The ID of the product with low stock
    val productId: String,

    // The name of the product (useful for notification text)
    val productName: String,

    // The stock quantity that triggered the notification
    val currentStock: Int
)