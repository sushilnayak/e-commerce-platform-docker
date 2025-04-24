package com.nayak.productservice.dto

import jakarta.validation.constraints.NotBlank

data class CategoryDTO(
    val id: String? = null,

    @field:NotBlank(message = "Category name is required")
    val name: String,

    val description: String,

    val parentCategoryId: String? = null,

    val active: Boolean = true
)