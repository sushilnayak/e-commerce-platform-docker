package com.nayak.productservice.service

import arrow.core.Either
import arrow.core.Option
import arrow.core.raise.either
import com.nayak.productservice.dto.InventoryStatusDTO
import com.nayak.productservice.dto.LowStockNotificationDTO
import com.nayak.productservice.dto.ProductDTO
import com.nayak.productservice.exception.ProductNotFoundException
import com.nayak.productservice.exception.ProductServiceException
import com.nayak.productservice.exception.ServiceError
import com.nayak.productservice.mapper.toDTO
import com.nayak.productservice.mapper.toEntity
import com.nayak.productservice.model.Product
import com.nayak.productservice.repository.ProductRepository
import io.netty.handler.timeout.ReadTimeoutException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoOperations
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.net.ConnectException
import java.time.LocalDateTime

@Service
class ProductServiceImpl(
    private val productRepository: ProductRepository,
    private val mongoOperations: ReactiveMongoOperations,
    webClientBuilder: WebClient.Builder, // Inject the builder
    @Value("\${app.services.notification.url}") private val notificationServiceUrl: String,
    @Value("\${app.services.inventory.url}") private val inventoryServiceUrl: String
) : ProductService {
    private val log = LoggerFactory.getLogger(ProductServiceImpl::class.java)

    private val notificationWebClient: WebClient = webClientBuilder.baseUrl(notificationServiceUrl).build()
    private val inventoryWebClient: WebClient = webClientBuilder.baseUrl(inventoryServiceUrl).build()

    private val lowStockThreshold = 10 // Example threshold

    @Transactional
    override suspend fun createProduct(productDTO: ProductDTO): Either<ProductServiceException, ProductDTO> =
        Either.catch {
            val product = productDTO.toEntity()
            productRepository.save(product.copy(id = null))
                .awaitSingle()
                .toDTO()
        }.mapLeft { throwable ->
            log.error("Error creating product: {}", throwable.message, throwable)
            ProductServiceException(ServiceError.Database("create", throwable.message ?: "unknown error"))
        }

    @Cacheable(cacheNames = ["products"], key = "#id")
    override suspend fun getProductById(id: String): Either<ProductServiceException, ProductDTO> =
        Either.catch {
            productRepository.findById(id)
                .awaitFirstOrNull()
                ?.toDTO()
                ?: throw ProductNotFoundException(id)
        }.mapLeft { handleGetError(it, id) }
//            .mapLeft { throwable ->
//            when (throwable) {
//                is ProductNotFoundException -> ProductServiceException(ServiceError.NotFound("Product", throwable.id))
//                else -> {
//                    log.error("Error getting product by id {}: {}", id, throwable.message, throwable)
//                    ProductServiceException(ServiceError.Database("findById", throwable.message ?: "unknown error"))
//                }
//            }
//        }

    @Cacheable(cacheNames = ["products"], key = "#id") // Cache the combined result
    override suspend fun getProductWithInventory(id: String): Either<ProductServiceException, ProductDTO> =
        either<ServiceError, ProductDTO> {
            // 1. Get base product details (leverages existing method's cache/logic)
            val productDTO = getProductById(id).mapLeft { it.serviceError }.bind() // Re-wrap error

            // 2. Call Inventory Service reactively
            log.debug("Fetching inventory status for product ID: {}", id)
            val inventoryStatus = Either.catch {
                inventoryWebClient.get()
                    .uri("/{productId}", id) // Use path variable substitution
                    .retrieve()
                    .onStatus(
                        { status -> status.isError && status != HttpStatus.NOT_FOUND }, // Handle 4xx/5xx except 404 specifically
                        { clientResponse ->
                            clientResponse.bodyToMono<String>().flatMap { errorBody ->
                                Mono.error(
                                    WebClientResponseException(
                                    "Inventory service error: ${clientResponse.statusCode()} - $errorBody",
                                    clientResponse.statusCode().value(),
                                    errorBody,
                                    clientResponse.headers().asHttpHeaders(),
                                    null, null
                                )
                                )
                            }
                        }
                    )
                    .bodyToMono<InventoryStatusDTO>() // Deserialize response body
                    .awaitSingleOrNull() // Use awaitSingleOrNull to handle potential 404 gracefully
            }.mapLeft { throwable ->
                // Map WebClient specific exceptions to our ServiceError
                mapWebClientError(throwable, "inventory-service", "get status for product $id")
            }.bind() // Extract InventoryStatusDTO or shift Left<ServiceError>

            // 3. Combine results - Update DTO with real-time stock
            // If inventoryStatus is null (e.g., 404 from inventory service), keep original stock? Or error out?
            // Let's keep original stock quantity from product service if inventory not found.
            val finalStock = inventoryStatus?.quantityOnHand ?: productDTO.stockQuantity

            productDTO.copy(stockQuantity = finalStock) // Return updated DTO

        }.mapLeft { ProductServiceException(it) } // Wrap final error


//    override suspend fun getAllProducts(): Flow<ProductDTO> =
//        productRepository.findAll()
//            .asFlow()
//            .map { it.toDTO() }

    override suspend fun getAllProducts(
        name: String?,
        categoryId: String?,
        minPrice: BigDecimal?,
        maxPrice: BigDecimal?,
        sortBy: String?,
        direction: Sort.Direction?
    ): Flow<ProductDTO> {
        val query = Query()
        val criteriaList = ArrayList<Criteria>()

        // Add criteria only if the corresponding parameter is not null/blank
        name?.takeIf { it.isNotBlank() }?.let {
            // Case-insensitive regex search
            criteriaList.add(Criteria.where("name").regex(it, "i"))
        }
        categoryId?.takeIf { it.isNotBlank() }?.let {
            criteriaList.add(Criteria.where("categoryId").`is`(it))
        }
        // Price range criteria
        val priceCriteria = Criteria.where("price")
        var priceCriteriaAdded = false
        minPrice?.let {
            priceCriteria.gte(it)
            priceCriteriaAdded = true
        }
        maxPrice?.let {
            priceCriteria.lte(it)
            priceCriteriaAdded = true
        }
        if (priceCriteriaAdded) {
            criteriaList.add(priceCriteria)
        }

        // Always filter for active products
         criteriaList.add(Criteria.where("active").`is`(true))

        // Combine all criteria using AND logic
        if (criteriaList.isNotEmpty()) {
            query.addCriteria(Criteria().andOperator(*criteriaList.toTypedArray()))
        }

        // Apply sorting if sortBy is provided
        sortBy?.takeIf { it.isNotBlank() }?.let { field ->
            // Use direction; default direction handled in controller or here (e.g., direction ?: Sort.Direction.ASC)
            val sortDir = direction ?: Sort.Direction.ASC
            query.with(Sort.by(sortDir, field))
        }

        log.debug("Executing product query: {}", query) // Log the built query

        // Execute the query using ReactiveMongoOperations
        return mongoOperations.find(query, Product::class.java) // Specify collection/document type
            .asFlow()
            .map { it.toDTO() } // Map results to DTOs
    }
    // Evict from cache when updating
    // Key is the 'id' parameter. Evicts BEFORE method execution potentially?
    // Consider condition = "#result.isRight()" to only evict on success,
    // but need careful testing with suspend Either return types.
    // Safest for now might be unconditional eviction.
    @Transactional
    @CacheEvict(cacheNames = ["products"], key = "#id")
    suspend fun updateProductAlternative(
        id: String,
        productDTO: ProductDTO
    ): Either<ProductServiceException, ProductDTO> =

        either<ServiceError, ProductDTO> {

            val existingProduct = Option.fromNullable( // Convert Product? to Either<Unit, Product>
                productRepository.findById(id).awaitFirstOrNull()
            )
                .toEither { ServiceError.NotFound("Product", id) }
                .bind()


            val updatedProductEntity = existingProduct.copy(
                name = productDTO.name,
                description = productDTO.description,
                price = productDTO.price,
                stockQuantity = productDTO.stockQuantity, // Update stock here
                categoryId = productDTO.categoryId,
                imageUrls = productDTO.imageUrls,
                active = productDTO.active,
                updatedAt = java.time.LocalDateTime.now()
            )

            val savedProduct = Either.catch {
                productRepository.save(updatedProductEntity).awaitSingle()
            }.mapLeft {
                log.error("Error updating product id {}: {}", id, it.message, it)
                ServiceError.Database("update product", it.message ?: "unknown")
            }.bind()

            val savedProductDTO = savedProduct.toDTO()

            // Check for low stock and notify *after* successful save
            if (savedProductDTO.stockQuantity < lowStockThreshold && existingProduct.stockQuantity >= lowStockThreshold) {
                log.info("Stock for product {} ({}) dropped below threshold ({}), notifying...", id, savedProductDTO.name, lowStockThreshold)
                // Call notification service - fire and forget for now, but capture potential error
                notifyLowStock(savedProductDTO)
                    .mapLeft { notifyError ->
                        // Log the notification error but don't fail the main update operation
                        log.error(
                            "Failed to send low stock notification for product {}: {}",
                            id, notifyError.serviceError.message
                        )
                        // Return the original error if needed, but here we just log
                    }
                // We don't .bind() here as we don't want notification failure to fail the updateProduct call
            }

            savedProductDTO // Return the successfully updated product DTO
        }.mapLeft { ProductServiceException(it) }

    override suspend fun notifyLowStock(product: ProductDTO): Either<ProductServiceException, Unit> =
        Either.catch {
            val notificationPayload = LowStockNotificationDTO(
                productId = product.id ?: "UNKNOWN", // Handle null ID case?
                productName = product.name,
                currentStock = product.stockQuantity
            )

            notificationWebClient.post()
                .uri("/low-stock") // Assuming this is the endpoint on notification-service
                .bodyValue(notificationPayload) // Set the request body
                .retrieve()
                // Handle non-2xx responses as errors
                .onStatus(
                    { status -> status.isError },
                    { clientResponse ->
                        clientResponse.bodyToMono<String>().flatMap { errorBody ->
                            Mono.error(WebClientResponseException(
                                "Notification service error: ${clientResponse.statusCode()} - $errorBody",
                                clientResponse.statusCode().value(),
                                errorBody,
                                clientResponse.headers().asHttpHeaders(),
                                null, null
                            ))
                        }
                    }
                )
                .toBodilessEntity()
                .awaitSingle()

            Unit
        }
            .mapLeft { throwable ->
            ProductServiceException(
                mapWebClientError(throwable, "notification-service", "send low stock notification for product ${product.id}")
            )
        }

    // Evict from cache when updating
    // Key is the 'id' parameter. Evicts BEFORE method execution potentially?
    // Consider condition = "#result.isRight()" to only evict on success,
    // but need careful testing with suspend Either return types.
    // Safest for now might be unconditional eviction.
    @Transactional
    @CacheEvict(cacheNames = ["products"], key = "#id")
    override suspend fun updateProduct(
        id: String,
        productDTO: ProductDTO
    ): Either<ProductServiceException, ProductDTO> =
        Either.catch {

            val existingProduct = productRepository.findById(id)
                .awaitFirstOrNull()
                ?: throw ProductNotFoundException(id)


            val updatedProduct = existingProduct.copy(
                name = productDTO.name,
                description = productDTO.description,
                price = productDTO.price,
                stockQuantity = productDTO.stockQuantity,
                categoryId = productDTO.categoryId,
                imageUrls = productDTO.imageUrls,
                active = productDTO.active,
                updatedAt = LocalDateTime.now()
            )

            productRepository.save(updatedProduct)
                .awaitSingle()
                .toDTO()
        }.mapLeft { handleGetError(it, id) }
//            .mapLeft { throwable ->
//
//            when (throwable) {
//                is ProductNotFoundException -> ProductServiceException(ServiceError.NotFound("Product", throwable.id))
//                else -> ProductServiceException(ServiceError.Database("update", throwable.message ?: "unknown error"))
//            }
//        }

    // Evict from cache when deleting
    // Key is the 'id' parameter
    @CacheEvict(cacheNames = ["products"], key = "#id")
    @Transactional
    override suspend fun deleteProduct(id: String): Either<ProductServiceException, Unit> =
        Either.catch {
            val exists = productRepository.existsById(id)
                .awaitSingle()
            if (!exists) {
                throw ProductNotFoundException(id)
            }
            productRepository.deleteById(id).awaitSingleOrNull()
            Unit
        }
            .mapLeft { handleGetError(it, id) } // Reuse error handler
//            .mapLeft { throwable ->
//            when (throwable) {
//                is ProductNotFoundException -> ProductServiceException(ServiceError.NotFound("Product", throwable.id))
//                else -> {
//                    log.error("Error deleting product id {}: {}", id, throwable.message, throwable)
//                    ProductServiceException(ServiceError.Database("delete", throwable.message ?: "unknown error"))
//                }
//            }
//        }


    override suspend fun searchProducts(query: String): Flow<ProductDTO> =
        productRepository.findByNameContainingIgnoreCaseAndActive(query, true)
            .asFlow()
            .map { it.toDTO() }


    override suspend fun getProductsByCategory(categoryId: String): Flow<ProductDTO> =
        productRepository.findByCategoryIdAndActive(categoryId, true)
            .asFlow()
            .map { it.toDTO() }

    override suspend fun getProductsByPriceRange(minPrice: BigDecimal, maxPrice: BigDecimal): Flow<ProductDTO> =
        productRepository.findByPriceBetweenAndActive(minPrice, maxPrice, true)
            .asFlow()
            .map { it.toDTO() }

    override suspend fun getAllActiveProducts(): Flow<ProductDTO> =
        productRepository.findByActive(true)
            .asFlow()
            .map { it.toDTO() }

    override suspend fun getLowStockProducts(minStock: Int): Flow<ProductDTO> =
        productRepository.findByStockQuantityLessThanAndActive(minStock, true)
            .asFlow()
            .map { it.toDTO() }

    private fun handleGetError(throwable: Throwable, id: String): ProductServiceException {
        return ProductServiceException(
            when (throwable) {
                is ProductNotFoundException -> ServiceError.NotFound("Product", throwable.id)
                else -> {
                    log.error("Error getting product by id {}: {}", id, throwable.message)//, throwable) // Optional trace
                    ServiceError.Database("findById", throwable.message ?: "unknown error")
                }
            }
        )
    }

    private fun mapWebClientError(throwable: Throwable, serviceName: String, operation: String): ServiceError {
        log.warn("Error calling {} during operation '{}': {}", serviceName, operation, throwable.message)
        return when (throwable) {
            is WebClientResponseException -> {
                // Handle 404 from external service as potentially "NotFound" or just ExternalService error
                if (throwable.statusCode == HttpStatus.NOT_FOUND) {
                    // Decide: Is external 404 a "NotFound" in *our* domain, or just an external issue?
                    // Often better to treat as external service error unless contract guarantees NotFound mapping.
                    ServiceError.ExternalService(serviceName, operation, "Received 404 Not Found - ${throwable.responseBodyAsString}")
                } else {
                    ServiceError.ExternalService(serviceName, operation, "Received status ${throwable.statusCode} - ${throwable.responseBodyAsString}")
                }
            }
            is WebClientRequestException -> {
                // Network-level issues before response received
                if (throwable.cause is ConnectException) {
                    ServiceError.ExternalService(serviceName, operation, "Connection refused or network issue")
                } else if (throwable.cause is ReadTimeoutException) {
                    ServiceError.ExternalService(serviceName, operation, "Read timeout")
                } else {
                    ServiceError.ExternalService(serviceName, operation, "Request error: ${throwable.message}")
                }
            }
            is ReadTimeoutException -> ServiceError.ExternalService(serviceName, operation, "Read timeout") // Can also be thrown directly
            // Add other specific Netty/IO exceptions if needed
            else -> ServiceError.ExternalService(serviceName, operation, "Unknown communication error: ${throwable.message}")
        }
    }

}