package com.anitec.platform.core.common

import androidx.annotation.StringRes
import com.anitec.platform.R

/** Localized message for an error. Server text is never shown as the main message. */
@StringRes
fun AppError.messageRes(): Int = when (this) {
    AppError.Network -> R.string.error_network
    AppError.Unauthorized -> R.string.error_unauthorized
    AppError.Forbidden -> R.string.error_forbidden
    AppError.NotFound -> R.string.error_not_found
    is AppError.Conflict -> R.string.error_conflict
    is AppError.Validation -> R.string.error_unknown
    is AppError.Server -> R.string.error_server
    is AppError.Unknown -> R.string.error_unknown
}
