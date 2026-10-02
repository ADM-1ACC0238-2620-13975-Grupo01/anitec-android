package com.anitec.platform.core.network

import com.anitec.platform.BuildConfig

/**
 * The API stores image paths relative to the server root (`/uploads/animals/x.jpg`, not under `/api/v1`).
 * Absolute http(s) URLs are returned unchanged.
 */
fun resolveMediaUrl(path: String?, serverUrl: String = BuildConfig.SERVER_URL): String? {
    if (path.isNullOrBlank()) return null
    if (path.startsWith("http://") || path.startsWith("https://")) return path
    return serverUrl.trimEnd('/') + "/" + path.trimStart('/')
}
