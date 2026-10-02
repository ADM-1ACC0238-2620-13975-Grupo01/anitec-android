package com.anitec.platform.core.common

/** Failures the UI can react to. Decided from the HTTP status, never from server message text. */
sealed interface AppError {
    /** No connection, timeout or DNS failure. */
    data object Network : AppError

    /** HTTP 401: missing, invalid or expired token. */
    data object Unauthorized : AppError

    /** HTTP 403. */
    data object Forbidden : AppError

    /** HTTP 404. */
    data object NotFound : AppError

    /** HTTP 409: duplicate resource. */
    data class Conflict(val messages: List<String> = emptyList()) : AppError

    /** HTTP 400: the server rejected the input. Messages are server text, shown only as a hint. */
    data class Validation(val messages: List<String> = emptyList()) : AppError

    /** HTTP 5xx. */
    data class Server(val code: Int) : AppError

    data class Unknown(val message: String? = null) : AppError
}

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(value)
    return this
}

inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) action(error)
    return this
}
