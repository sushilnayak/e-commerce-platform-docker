package com.nayak.productservice.dto

// Removed internal toDTO function - use ModelMapper version
import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.LocalDateTime

data class ProductDTO(
    val id: String? = null, // Corrected type

    @field:NotBlank(message = "Product name is required")
    val name: String,

    val description: String? = null,

    @field:Positive(message = "Price must be positive")
    val price: BigDecimal,

    @field:PositiveOrZero(message = "Stock quantity cannot be negative")
    val stockQuantity: Int,

    val categoryId: String? = null,

    val imageUrls: List<String> = emptyList(),

    val createdAt: LocalDateTime? = null,

    val updatedAt: LocalDateTime? = null,

    val active: Boolean = true
)
