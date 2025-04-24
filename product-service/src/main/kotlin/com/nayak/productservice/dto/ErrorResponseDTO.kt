package com.nayak.productservice.dto

import java.time.Instant

/**
 * Standard error response structure for APIs.
 */
data class ErrorResponseDTO(
    val timestamp: Instant,
    val status: Int,
    val error: String,
    val message: String?,
    val path: String
)