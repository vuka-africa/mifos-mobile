/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mobile-mobile/blob/master/LICENSE.md
 */
package org.mifos.mobile.core.datastore

import org.mifos.mobile.core.datastore.model.OIDCConfiguration
import org.mifos.mobile.core.datastore.model.OIDCTokens

/**
 * Service interface for OIDC authentication operations.
 *
 * This interface defines the contract for OIDC authentication across all platforms.
 * Platform-specific implementations handle the actual authentication flow:
 * - Web (JS/WASM): Uses oidc-client-ts with popup-based authentication
 * - Android: Uses AppAuth-Android (future implementation)
 * - iOS: Uses AppAuth-iOS (future implementation)
 */
interface OIDCService {
    /**
     * The OIDC configuration used by this service.
     */
    val configuration: OIDCConfiguration

    /**
     * Check if the user is currently authenticated with valid tokens.
     */
    suspend fun isAuthenticated(): Boolean

    /**
     * Initiate the OIDC login flow.
     *
     * For web targets, this opens a popup window for authentication.
     * The user authenticates in the popup and is returned to the app
     * without a full page redirect.
     *
     * @return The OIDC tokens received from the identity provider
     * @throws OIDCException if authentication fails
     */
    suspend fun login(): OIDCTokens

    /**
     * Perform a silent login attempt using stored credentials or refresh tokens.
     *
     * @return The OIDC tokens if silent login succeeds, null otherwise
     */
    suspend fun silentLogin(): OIDCTokens?

    /**
     * Refresh the access token using the refresh token.
     *
     * @return The new OIDC tokens
     * @throws OIDCException if refresh fails
     */
    suspend fun refreshTokens(): OIDCTokens

    /**
     * Get the currently stored tokens.
     *
     * @return The stored tokens, or null if not authenticated
     */
    suspend fun getStoredTokens(): OIDCTokens?

    /**
     * Log out the user and clear all stored tokens.
     *
     * For web targets, this may also redirect to the identity provider's
     * logout endpoint to perform single sign-out.
     */
    suspend fun logout()

    /**
     * Get the current access token, refreshing if necessary.
     *
     * This is the primary method for obtaining a token for API calls.
     * It will automatically refresh the token if it's expired or about to expire.
     *
     * @return The current valid access token, or null if not authenticated
     */
    suspend fun getValidAccessToken(): String?
}

/**
 * Exception thrown when OIDC operations fail.
 */
class OIDCException(
    message: String,
    val errorCode: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    companion object {
        const val ERROR_LOGIN_FAILED = "login_failed"
        const val ERROR_REFRESH_FAILED = "refresh_failed"
        const val ERROR_USER_CANCELLED = "user_cancelled"
        const val ERROR_POPUP_BLOCKED = "popup_blocked"
        const val ERROR_NETWORK = "network_error"
        const val ERROR_INVALID_CONFIG = "invalid_config"
    }
}
