package com.nayak.productservice.service

import arrow.core.Either
import com.nayak.productservice.dto.CategoryDTO
import com.nayak.productservice.exception.CategoryServiceException
import kotlinx.coroutines.flow.Flow

interface CategoryService {
    suspend fun createCategory(categoryDTO: CategoryDTO): Either<CategoryServiceException, CategoryDTO>
    suspend fun getCategoryById(id: String): Either<CategoryServiceException, CategoryDTO>
    suspend fun getAllCategories(): Flow<CategoryDTO>
    suspend fun updateCategory(id: String, categoryDTO: CategoryDTO): Either<CategoryServiceException, CategoryDTO>
    suspend fun deleteCategory(id: String): Either<CategoryServiceException, Unit>
}