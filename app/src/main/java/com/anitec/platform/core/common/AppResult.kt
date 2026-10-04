package com.anitec.platform.core.common

/**
 * Failures the UI can react to.
 *
 * Decided from the HTTP status (or transport failure), never by parsing free-form server
 * message text. ViewModels map these cases to string resources via [ErrorMessages].
 */
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

    /** Unexpected client-side or unmapped failure. */
    data class Unknown(val message: String? = null) : AppError
}

/**
 * Outcome of a use case or repository call: either a value or a typed [AppError].
 * Prefer this over throwing across layer boundaries so the UI can branch on [Failure].
 */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

/** Transforms the success value; failures are left unchanged. */
inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

/** Runs [action] when this is a success; returns the same result for chaining. */
inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(value)
    return this
}

/** Runs [action] when this is a failure; returns the same result for chaining. */
inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) action(error)
    return this
}
