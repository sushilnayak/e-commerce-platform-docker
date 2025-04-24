package com.nayak.productservice.service

import arrow.core.Either
import arrow.core.Option
import arrow.core.raise.either
import com.nayak.productservice.dto.CategoryDTO
import com.nayak.productservice.exception.CategoryInUseException
import com.nayak.productservice.exception.CategoryServiceException
import com.nayak.productservice.exception.DuplicateCategoryNameException
import com.nayak.productservice.exception.ServiceError
import com.nayak.productservice.mapper.toDTO
import com.nayak.productservice.mapper.toEntity
import com.nayak.productservice.repository.CategoryRepository
import com.nayak.productservice.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CategoryServiceImpl(
    private val categoryRepository: CategoryRepository,
    private val productRepository: ProductRepository,
) : CategoryService {

    private val log = LoggerFactory.getLogger(CategoryServiceImpl::class.java)

    override suspend fun createCategory(categoryDTO: CategoryDTO): Either<CategoryServiceException, CategoryDTO> =
        Either.catch {

             val existing = categoryRepository.findByNameIgnoreCase(categoryDTO.name).awaitFirstOrNull()
             if (existing != null) throw DuplicateCategoryNameException(categoryDTO.name)

            val category = categoryDTO.toEntity()
            categoryRepository.save(category.copy(id = null)) // Ensure ID is null
                .awaitSingle()
                .toDTO()
        }.mapLeft { throwable ->
            log.error("Error creating category: {}", throwable.message, throwable)
            when(throwable){
                is DuplicateCategoryNameException -> CategoryServiceException(ServiceError.Validation("duplicate category", throwable.message ?: "unknown"))
                else -> {
                    CategoryServiceException(ServiceError.Database("create category", throwable.message ?: "unknown"))
                }
            }
        }

    override suspend fun getCategoryById(id: String): Either<CategoryServiceException, CategoryDTO> =
        Either.catch {
            categoryRepository.findById(id)
                .awaitFirstOrNull()
                ?.toDTO()
                ?: throw NoSuchElementException("Category not found") // Internal signal
        }.mapLeft { throwable ->
            when (throwable) {
                is NoSuchElementException -> CategoryServiceException(ServiceError.NotFound("Category", id))
                else -> {
                    log.error("Error getting category by id {}: {}", id, throwable.message, throwable)
                    CategoryServiceException(
                        ServiceError.Database(
                            "find category by id",
                            throwable.message ?: "unknown"
                        )
                    )
                }
            }
        }

    override suspend fun getAllCategories(): Flow<CategoryDTO> =
        categoryRepository.findAll()
            .asFlow()
            .map { it.toDTO() }

    override suspend fun updateCategory(
        id: String,
        categoryDTO: CategoryDTO
    ): Either<CategoryServiceException, CategoryDTO> =
        either<ServiceError, CategoryDTO> {
            val existingCategory = Option.fromNullable( // Convert Product? to Either<Unit, Product>
                categoryRepository.findById(id).awaitFirstOrNull()
            )
                .toEither { ServiceError.NotFound("Category", id) }
                .bind()

            // Optional: Check for name conflicts if name is changing
            // if (existingCategory.name != categoryDTO.name) { ... check findByNameIgnoreCase ... }

            val updatedCategory = existingCategory.copy(
                name = categoryDTO.name,
                description = categoryDTO.description,
                parentCategoryId = categoryDTO.parentCategoryId
                // ID, createdAt etc. remain unchanged from existingCategory
            )

            Either.catch {
                categoryRepository.save(updatedCategory).awaitSingle()
            }.mapLeft {
                log.error("Error updating category id {}: {}", id, it.message, it)
                ServiceError.Database("update category", it.message ?: "unknown error")
            }
                .bind()
                .toDTO()
        }.mapLeft { CategoryServiceException(it) }


    override suspend fun deleteCategory(id: String): Either<CategoryServiceException, Unit> =
        Either.catch {
            val exists = categoryRepository.existsById(id).awaitSingle()
            if (!exists) {
                throw NoSuchElementException("Category not found for deletion") // Internal signal
            }
            // Optional: Check if any product uses this category before deleting
            val productCount = productRepository.countByCategoryId(id).awaitSingle()
            if (productCount > 0) throw CategoryInUseException(id)

            categoryRepository.deleteById(id).awaitSingleOrNull() // null on success
            Unit
        }.mapLeft { throwable ->
            when (throwable) {
                is NoSuchElementException -> CategoryServiceException(ServiceError.NotFound("Category", id))
                is CategoryInUseException -> CategoryServiceException(ServiceError.NotFound("Category in use", id))
                else -> {
                    log.error("Error deleting category id {}: {}", id, throwable.message, throwable)
                    CategoryServiceException(ServiceError.Database("delete category", throwable.message ?: "unknown"))
                }
            }
        }
}
