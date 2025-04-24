package com.nayak.productservice.category.controller

import com.nayak.productservice.dto.CategoryDTO
import com.nayak.productservice.exception.CategoryServiceException
import com.nayak.productservice.exception.ServiceError
import com.nayak.productservice.service.CategoryService
import jakarta.validation.Valid
import kotlinx.coroutines.flow.Flow
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/v1/categories")
class CategoryController(private val categoryService: CategoryService) {

    private val log = LoggerFactory.getLogger(CategoryController::class.java)

    @PostMapping
    suspend fun createCategory(@Valid @RequestBody categoryDTO: CategoryDTO): ResponseEntity<CategoryDTO> =
        categoryService.createCategory(categoryDTO).fold(
            ifLeft = { throw handleCategoryServiceError(it, "createCategory") },
            ifRight = { ResponseEntity.status(HttpStatus.CREATED).body(it) }
        )

    @GetMapping("/{id}")
    suspend fun getCategoryById(@PathVariable id: String): ResponseEntity<CategoryDTO> =
        categoryService.getCategoryById(id).fold(
            ifLeft = { throw handleCategoryServiceError(it, "getCategoryById", id) },
            ifRight = { ResponseEntity.ok(it) }
        )

    @GetMapping
    suspend fun getAllCategories(): Flow<CategoryDTO> =
        categoryService.getAllCategories()

    @PutMapping("/{id}")
    suspend fun updateCategory(
        @PathVariable id: String,
        @Valid @RequestBody categoryDTO: CategoryDTO
    ): ResponseEntity<CategoryDTO> =
        categoryService.updateCategory(id, categoryDTO).fold(
            ifLeft = { throw handleCategoryServiceError(it, "updateCategory", id) },
            ifRight = { ResponseEntity.ok(it) }
        )

    @DeleteMapping("/{id}")
    suspend fun deleteCategory(@PathVariable id: String): ResponseEntity<Unit> =
        categoryService.deleteCategory(id).fold(
            ifLeft = { throw handleCategoryServiceError(it, "deleteCategory", id) },
            ifRight = { ResponseEntity.noContent().build() }
        )

    // Helper similar to ProductController's one
    private fun handleCategoryServiceError(
        exception: CategoryServiceException,
        operation: String,
        resourceId: String? = null
    ): ResponseStatusException {
        val resourceInfo = resourceId?.let { " for resource $it" } ?: ""
        log.warn(
            "Service error during category operation '{}'{} : {}",
            operation,
            resourceInfo,
            exception.serviceError
        ) // Don't log exception trace here generally

        // Map ServiceError to appropriate HTTP Status
        val (status, message) = when (val error = exception.serviceError) {
            is ServiceError.NotFound -> HttpStatus.NOT_FOUND to error.message
            is ServiceError.Validation -> HttpStatus.BAD_REQUEST to error.message
            is ServiceError.BusinessRule -> HttpStatus.BAD_REQUEST to error.message // e.g., Duplicate name, CategoryInUse
            is ServiceError.Database -> HttpStatus.INTERNAL_SERVER_ERROR to "A database error occurred processing the category."
            // Add other specific mappings if needed
            else -> HttpStatus.INTERNAL_SERVER_ERROR to "An unexpected error occurred processing the category."
        }
        return ResponseStatusException(status, message)
    }
}