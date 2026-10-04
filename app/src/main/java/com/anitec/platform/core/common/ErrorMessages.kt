package com.anitec.platform.core.common

import androidx.annotation.StringRes
import com.anitec.platform.R

/**
 * Maps an [AppError] to a localized string resource for the UI.
 *
 * The primary message always comes from `strings.xml` (English / es-419), never from
 * free-form server text. Optional server hints on [AppError.Validation] or
 * [AppError.Conflict] may be shown separately as secondary detail if a screen chooses to.
 */
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
