package com.nayak.productservice.model

import jakarta.validation.constraints.NotBlank
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "categories")
data class Category(
    @Id
    val id: String? = null,
    @field:NotBlank(message = "Category name cannot be blank")
    val name: String,
    val description: String,
    val parentCategoryId : String? = null,
    val active: Boolean = true,
)

