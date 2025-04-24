package com.nayak.productservice.exception

//class CategoryServiceException(
//    val serviceError: ServiceError
//) : RuntimeException(serviceError.message)

class CategoryServiceException(
    val serviceError: ServiceError
) : RuntimeException(serviceError.message) {
    constructor(message: String, cause: Throwable) : this(ServiceError.Unknown(message)) {
        initCause(cause)
    }
}