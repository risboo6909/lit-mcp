package com.github.risboo6909.utils

import org.slf4j.Logger

/**
 * Logs an error message and adds it to a collection of errors.
 * Useful for collecting errors during batch operations while also logging them.
 *
 * @param logger SLF4J logger instance to use for logging
 * @param errors Mutable list to which the error message will be added
 * @param message Error message to log and collect
 * @param throwable Optional throwable/exception to include in the log and error message
 */
fun logAndCollectError(logger: Logger, errors: MutableList<String>, message: String, throwable: Throwable? = null) {
    if (throwable != null) {
        logger.error(message, throwable)
    } else {
        logger.error(message)
    }
    val errorMsg = if (throwable != null) {
        "$message: ${throwable.message}"
    } else {
        message
    }
    errors.add(errorMsg)
}
