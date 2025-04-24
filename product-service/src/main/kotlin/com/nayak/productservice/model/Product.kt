package com.nayak.productservice.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal
import java.time.LocalDateTime

@Document(collection = "products")
data class Product(
    @Id
    val id: String? = null, // Corrected type

    @field:NotBlank(message = "Product Name cannot is required")
    val name: String,
    val description: String? = null,

    @field:Positive(message = "Product Price must be positive")
    val price: BigDecimal,

    @field:PositiveOrZero(message = "Stock quantity cannot be negative")
    val stockQuantity: Int,
    val categoryId: String? = null,

    val imageUrls: List<String> = emptyList(),

    // Default values handled by data class constructor if not provided
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    val active: Boolean = true
)