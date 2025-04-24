package com.nayak.productservice.mapper

import com.nayak.productservice.dto.CategoryDTO
import com.nayak.productservice.model.Category

fun Category.toDTO(): CategoryDTO =
    CategoryDTO(
        id = this.id,
        name = this.name,
        description = this.description,
        parentCategoryId = this.parentCategoryId,
        active = this.active
    )

fun CategoryDTO.toEntity(): Category =
    Category(
        id = this.id,
        name = this.name,
        description = this.description,
        parentCategoryId = this.parentCategoryId,
        active = this.active
    )