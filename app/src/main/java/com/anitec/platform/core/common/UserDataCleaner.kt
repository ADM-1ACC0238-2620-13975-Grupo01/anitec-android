package com.anitec.platform.core.common

/**
 * Contract for wiping local caches that belong to the signed-in user.
 *
 * Implemented by Room-backed repositories (livestock, sanitary, etc.).
 * [SessionCleanup] invokes every registered cleaner when the session ends
 * (explicit sign-out or HTTP 401), so the next user never sees the previous one's data.
 */
interface UserDataCleaner {
    /** Deletes every locally cached record for the previous user. */
    suspend fun clear()
}
