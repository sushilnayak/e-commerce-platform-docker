package com.nayak.productservice.mapper

import com.nayak.productservice.dto.ProductDTO
import com.nayak.productservice.model.Product

fun Product.toDTO(): ProductDTO =
    ProductDTO(
        id = this.id,
        name = this.name,
        description = this.description,
        price = this.price,
        stockQuantity = this.stockQuantity,
        categoryId = this.categoryId,
        imageUrls = this.imageUrls,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        active = this.active
    )

fun ProductDTO.toEntity(): Product =
    Product(
        id = this.id,
        name = this.name,
        description = this.description,
        price = this.price,
        stockQuantity = this.stockQuantity,
        categoryId = this.categoryId,
        imageUrls = this.imageUrls,
        // createdAt is handled by Product default or service logic if needed
        // updatedAt is handled by service logic during updates
        active = this.active
        // Let Product constructor handle default createdAt/updatedAt on initial creation
        // Service layer explicitly sets updatedAt on updates.
    )