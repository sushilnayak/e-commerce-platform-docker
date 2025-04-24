package com.nayak.productservice.repository

import com.nayak.productservice.model.Category
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Mono

interface CategoryRepository : ReactiveMongoRepository<Category, String> {
    fun findByNameIgnoreCase(name: String): Mono<Category>
}