package com.anitec.platform.core.common

/**
 * Plausibility check for the optional e-mail of a registration. It mirrors the backend rule
 * (something@something.tld, no spaces, at most 254 characters) so the server rarely has to refuse it.
 */
object EmailValidator {
    private const val MAX_LENGTH = 254
    private val pattern = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")

    /** Trimmed address, or null when the field was left blank. */
    fun normalize(raw: String): String? = raw.trim().ifEmpty { null }

    /** True when [raw] is blank (the e-mail is optional) or a plausible address. */
    fun isValidOrBlank(raw: String): Boolean {
        val email = normalize(raw) ?: return true
        return email.length <= MAX_LENGTH && pattern.matches(email)
    }
}
