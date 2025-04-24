package com.nayak.productservice.service

import arrow.core.Either
import com.nayak.productservice.dto.ProductDTO
import com.nayak.productservice.exception.ProductServiceException
import kotlinx.coroutines.flow.Flow
import org.springframework.data.domain.Sort
import java.math.BigDecimal

interface ProductService {
    suspend fun createProduct(productDTO: ProductDTO): Either<ProductServiceException, ProductDTO>

    suspend fun getProductById(id: String): Either<ProductServiceException, ProductDTO> // Changed to String

    //    suspend fun getAllProducts(): Flow<ProductDTO>
    suspend fun getAllProducts(
        name: String?,
        categoryId: String?,
        minPrice: BigDecimal?,
        maxPrice: BigDecimal?,
        sortBy: String?,
        direction: Sort.Direction?
    ): Flow<ProductDTO>

    suspend fun updateProduct(
        id: String,
        productDTO: ProductDTO
    ): Either<ProductServiceException, ProductDTO> // Changed to String

    suspend fun deleteProduct(id: String): Either<ProductServiceException, Unit> // Changed to String

    suspend fun searchProducts(query: String): Flow<ProductDTO>

    suspend fun getProductsByCategory(categoryId: String): Flow<ProductDTO>

    suspend fun getProductsByPriceRange(minPrice: BigDecimal, maxPrice: BigDecimal): Flow<ProductDTO>

    suspend fun getAllActiveProducts(): Flow<ProductDTO>

    suspend fun getLowStockProducts(minStock: Int): Flow<ProductDTO>

    // Added for inventory check - could also be integrated into getProductById directly
    suspend fun getProductWithInventory(id: String): Either<ProductServiceException, ProductDTO>

    // Internal helper or potentially public if needed elsewhere
    suspend fun notifyLowStock(product: ProductDTO): Either<ProductServiceException, Unit> // Takes DTO for simplicity
}