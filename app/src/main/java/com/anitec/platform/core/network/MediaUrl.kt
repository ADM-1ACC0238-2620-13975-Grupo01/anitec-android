package com.anitec.platform.core.network

import com.anitec.platform.BuildConfig

/**
 * Turns a server-relative media path into a full URL Coil (or any image loader) can fetch.
 *
 * The API stores image paths relative to the server root (`/uploads/animals/x.jpg`), not under
 * `/api/v1`. [serverUrl] defaults to [BuildConfig.SERVER_URL] (debug emulator host or the
 * hosted release backend). Absolute `http`/`https` URLs are returned unchanged; blank or null
 * paths yield null so callers can show a placeholder.
 */
fun resolveMediaUrl(path: String?, serverUrl: String = BuildConfig.SERVER_URL): String? {
    if (path.isNullOrBlank()) return null
    if (path.startsWith("http://") || path.startsWith("https://")) return path
    return serverUrl.trimEnd('/') + "/" + path.trimStart('/')
}
