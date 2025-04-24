package com.nayak.productservice.exception

// Ensure this exception class wrapping ServiceError exists
class ProductServiceException(
    val serviceError: ServiceError
) : RuntimeException(serviceError.message) {
    constructor(message: String, cause: Throwable) : this(ServiceError.Unknown(message)) {
        initCause(cause)
    }
}