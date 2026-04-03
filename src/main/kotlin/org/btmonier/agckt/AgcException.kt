package org.btmonier.agckt

/**
 * Base exception for all AGC-related errors.
 */
open class AgcException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Thrown when an AGC archive file cannot be found or opened.
 */
class AgcFileNotFoundException(path: String) : AgcException("AGC archive not found: $path")

/**
 * Thrown when a native AGC operation fails.
 */
class AgcNativeException(message: String) : AgcException(message)
