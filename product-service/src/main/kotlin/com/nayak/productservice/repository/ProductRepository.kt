package com.nayak.productservice.repository

import com.nayak.productservice.model.Product
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal

@Repository
interface ProductRepository : ReactiveMongoRepository<Product, String> {

    fun findByActive(active: Boolean): Flux<Product>

    fun findByNameContainingIgnoreCaseAndActive(name: String, active: Boolean): Flux<Product>

    fun findByCategoryIdAndActive(categoryId: String, active: Boolean): Flux<Product>

    fun findByPriceBetweenAndActive(minPrice: BigDecimal, maxPrice: BigDecimal, active: Boolean): Flux<Product>

    fun findByStockQuantityLessThanAndActive(minStock: Int, active: Boolean): Flux<Product>

    fun countByCategoryId(categoryId: String): Mono<Long>
}