package com.anitec.platform.scanner.domain

import com.anitec.platform.livestock.domain.Animal

/** Turns the text read from a QR code or barcode into one of the user's animals, by its tag. */
object TagResolver {

    /**
     * Texts worth matching against the tags: the whole code, and the part after the last `/` or `:` so that a
     * code such as `https://host/animals/AN-001` or `anitec:animal:AN-001` still finds `AN-001`.
     */
    fun candidates(raw: String): List<String> {
        val code = raw.trim()
        if (code.isEmpty()) return emptyList()
        val fromQuery = code.substringAfter("tag=", "").substringBefore('&')
        return listOf(code, code.substringAfterLast('/'), code.substringAfterLast(':'), fromQuery)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    fun resolve(raw: String, animals: List<Animal>): Animal? {
        for (candidate in candidates(raw)) {
            animals.firstOrNull { it.tag.equals(candidate, ignoreCase = true) }?.let { return it }
        }
        return null
    }
}
