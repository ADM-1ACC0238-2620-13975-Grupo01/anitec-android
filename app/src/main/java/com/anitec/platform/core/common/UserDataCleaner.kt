package com.anitec.platform.core.common

/** Removes every locally cached record of the previous user. Called whenever the session ends. */
interface UserDataCleaner {
    suspend fun clear()
}
