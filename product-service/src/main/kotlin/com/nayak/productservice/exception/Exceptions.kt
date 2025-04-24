package com.nayak.productservice.exception

class ProductNotFoundException(val id: String) : RuntimeException(id)
class CategoryNotFoundException(val id: String) : RuntimeException(id)
class CategoryInUseException(val id: String) : RuntimeException(id)
class DuplicateCategoryNameException(val id: String) : RuntimeException(id)