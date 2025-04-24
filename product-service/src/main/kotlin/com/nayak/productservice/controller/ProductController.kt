package com.nayak.productservice.controller

import com.nayak.productservice.dto.ProductDTO
import com.nayak.productservice.exception.ProductServiceException
import com.nayak.productservice.exception.ServiceError
import com.nayak.productservice.service.ProductService
import jakarta.validation.Valid
import kotlinx.coroutines.flow.Flow
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal

@RestController
@RequestMapping("/api/v1/products")
class ProductController(private val productService: ProductService) {

    private val log = LoggerFactory.getLogger(ProductController::class.java)

    @PostMapping
    suspend fun createProduct(
        @Valid @RequestBody productDto: ProductDTO // Added @Valid
    ): ResponseEntity<ProductDTO> =
        productService.createProduct(productDto)
            .fold(
                ifLeft = { exception -> throw handleServiceError(exception, "createProduct") },
                ifRight = { ResponseEntity.status(HttpStatus.CREATED).body(it) }
            )

    @GetMapping("/{id}")
    suspend fun getProductById(@PathVariable id: String): ResponseEntity<ProductDTO> =
        productService.getProductById(id)
            .fold(
                ifLeft = { exception -> throw handleServiceError(exception, "getProductById", id) },
                ifRight = { ResponseEntity.ok(it) }
            )

    @GetMapping // Enhanced endpoint
    suspend fun getAllProducts(
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) categoryId: String?,
        @RequestParam(required = false) minPrice: BigDecimal?,
        @RequestParam(required = false) maxPrice: BigDecimal?,
        @RequestParam(required = false) sortBy: String?,
        @RequestParam(required = false, defaultValue = "ASC") direction: String
    ): Flow<ProductDTO> {

        val sortDirection = try {
            Sort.Direction.fromString(direction.uppercase())
        } catch (e: IllegalArgumentException) {
            Sort.Direction.ASC
        }

        return productService.getAllProducts(
            name = name,
            categoryId = categoryId,
            minPrice = minPrice,
            maxPrice = maxPrice,
            sortBy = sortBy,
            direction = sortDirection
        )
    }
//    @GetMapping
//    suspend fun getAllProducts(): Flow<ProductDTO> =
//        productService.getAllProducts()

    @PutMapping("/{id}")
    suspend fun updateProduct(
        @PathVariable id: String,
        @Valid @RequestBody productDTO: ProductDTO
    ): ResponseEntity<ProductDTO> =
        productService.updateProduct(id, productDTO)
            .fold(
                ifLeft = { exception -> throw handleServiceError(exception, "updateProduct", id) },
                ifRight = { ResponseEntity.ok(it) }
            )

    @DeleteMapping("/{id}")
    suspend fun deleteProduct(@PathVariable id: String): ResponseEntity<Unit> =
        productService.deleteProduct(id)
            .fold(
                ifLeft = { exception -> throw handleServiceError(exception, "deleteProduct", id) },
                ifRight = { ResponseEntity.noContent().build() }
            )

    @GetMapping("/search")
    suspend fun searchProducts(@RequestParam query: String): Flow<ProductDTO> =
        productService.searchProducts(query)

    @GetMapping("/category/{categoryId}")
    suspend fun getProductsByCategory(@PathVariable categoryId: String): Flow<ProductDTO> =
        productService.getProductsByCategory(categoryId)

    @GetMapping("/price-range")
    suspend fun getProductsByPriceRange(
        @RequestParam minPrice: BigDecimal,
        @RequestParam maxPrice: BigDecimal
    ): Flow<ProductDTO> =
        productService.getProductsByPriceRange(minPrice, maxPrice)


    private fun handleServiceError(
        exception: ProductServiceException,
        operation: String,
        resourceId: String? = null
    ): ResponseStatusException {
        val resourceInfo = resourceId?.let { " for resource $it" } ?: ""
        log.warn(
            "Service error during operation '{}'{} : {}",
            operation,
            resourceInfo,
            exception.serviceError,
            exception
        )

        val (status, message) = when (val error = exception.serviceError) {
            is ServiceError.NotFound -> HttpStatus.NOT_FOUND to error.message
            is ServiceError.Validation -> HttpStatus.BAD_REQUEST to error.message
            is ServiceError.BusinessRule -> HttpStatus.BAD_REQUEST to error.message
            is ServiceError.Database -> HttpStatus.INTERNAL_SERVER_ERROR to "A database error occurred."
            is ServiceError.ExternalService -> HttpStatus.SERVICE_UNAVAILABLE to "A required external service is currently unavailable. Please try again later." // Or 500/502 depending on policy
            is ServiceError.Unknown -> HttpStatus.INTERNAL_SERVER_ERROR to "An unexpected error occurred."
        }
        // Throw ResponseStatusException which Spring Boot handles automatically
        return ResponseStatusException(status, message)
    }
}