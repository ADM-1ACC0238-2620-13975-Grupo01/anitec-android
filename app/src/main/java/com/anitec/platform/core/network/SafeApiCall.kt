package com.anitec.platform.core.network

import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.HttpException
import java.io.IOException

private val errorJson = Json { ignoreUnknownKeys = true }

/**
 * Runs an API call and converts every failure into [AppError].
 *
 * The backend answers errors with four different shapes (ProblemDetails, `{message}`,
 * `{message, errors[]}` and validation dictionaries), so only the HTTP status drives behavior;
 * any text found in the body is kept as a hint.
 */
suspend fun <T> safeApiCall(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    AppResult.Failure(e.toAppError())
} catch (e: IOException) {
    AppResult.Failure(AppError.Network)
} catch (e: Exception) {
    AppResult.Failure(AppError.Unknown(e.message))
}

internal fun HttpException.toAppError(): AppError {
    val messages = parseErrorMessages(response()?.errorBody()?.string())
    return when (code()) {
        400 -> AppError.Validation(messages)
        401 -> AppError.Unauthorized
        403 -> AppError.Forbidden
        404 -> AppError.NotFound
        409 -> AppError.Conflict(messages)
        in 500..599 -> AppError.Server(code())
        else -> AppError.Unknown(messages.firstOrNull())
    }
}

internal fun parseErrorMessages(body: String?): List<String> {
    if (body.isNullOrBlank()) return emptyList()
    val root = runCatching { errorJson.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return emptyList()
    val messages = mutableListOf<String>()
    (root["message"] as? JsonPrimitive)?.contentOrNull?.let(messages::add)
    (root["detail"] as? JsonPrimitive)?.contentOrNull?.let(messages::add)
    root["errors"]?.let { collectStrings(it, messages) }
    return messages.distinct()
}

private fun collectStrings(element: JsonElement, out: MutableList<String>) {
    when (element) {
        is JsonPrimitive -> element.contentOrNull?.let(out::add)
        is JsonArray -> element.forEach { collectStrings(it, out) }
        is JsonObject -> element.values.forEach { collectStrings(it, out) }
    }
}
