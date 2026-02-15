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

import kotlinx.coroutines.await
import org.mifos.mobile.core.datastore.model.OIDCConfiguration
import org.mifos.mobile.core.datastore.model.OIDCTokens
import kotlin.js.Promise

/**
 * wasmJs implementation of OIDCService using oidc-client-ts via @JsFun interop.
 *
 * This implementation uses a popup-based authentication flow which provides
 * a better user experience on web as the user stays in the app while
 * authenticating in a popup window.
 *
 * Features:
 * - Popup-based login (no full page redirect)
 * - Automatic token refresh via silent renew
 * - PKCE support for security
 * - Secure token storage managed by oidc-client-ts
 */
class OIDCServiceWasmJs(
    override val configuration: OIDCConfiguration,
) : OIDCService {

    // UserManager is initialized asynchronously since oidc-client-ts is loaded via dynamic import
    private var userManagerInstance: JsAny? = null

    /**
     * Get or create the UserManager instance.
     * Uses dynamic import() to load oidc-client-ts which webpack bundles correctly.
     */
    private suspend fun getUserManager(): JsAny {
        userManagerInstance?.let { return it }

        val settings = createUserManagerSettings(
            authority = configuration.issuerUrl,
            clientId = configuration.clientId,
            redirectUri = configuration.redirectUri,
            popupRedirectUri = configuration.popupCallbackUri,
            silentRedirectUri = configuration.silentRefreshUri,
            postLogoutRedirectUri = configuration.postLogoutRedirectUri,
            scope = configuration.scopesString(),
        )

        val promise: Promise<JsAny> = createUserManagerAsync(settings)
        val manager: JsAny = promise.await<JsAny>()
        userManagerInstance = manager
        consoleLog(toJsString("UserManager initialized successfully"))
        return manager
    }

    override suspend fun isAuthenticated(): Boolean {
        return try {
            val manager = getUserManager()
            val promise: Promise<JsAny?> = userManagerGetUser(manager)
            val user: JsAny? = promise.await<JsAny?>()
            user != null && !userGetExpired(user)
        } catch (e: Throwable) {
            consoleError(toJsString("Error checking authentication status: ${e.message}"))
            false
        }
    }

    override suspend fun login(): OIDCTokens {
        return try {
            consoleLog(toJsString("Initiating popup login"))
            val manager = getUserManager()
            val promise: Promise<JsAny> = userManagerSigninPopup(manager)
            val user: JsAny = promise.await<JsAny>()
            consoleLog(toJsString("Login successful"))
            user.toOIDCTokens()
        } catch (e: Throwable) {
            consoleError(toJsString("Login failed: ${e.message}"))
            val errorMessage = e.message ?: "Login failed"
            val errorCode = when {
                errorMessage.contains("popup", ignoreCase = true) -> OIDCException.ERROR_POPUP_BLOCKED
                errorMessage.contains("cancelled", ignoreCase = true) -> OIDCException.ERROR_USER_CANCELLED
                errorMessage.contains("network", ignoreCase = true) -> OIDCException.ERROR_NETWORK
                else -> OIDCException.ERROR_LOGIN_FAILED
            }
            throw OIDCException(errorMessage, errorCode, e)
        }
    }

    override suspend fun silentLogin(): OIDCTokens? {
        return try {
            consoleLog(toJsString("Attempting silent login"))
            val manager = getUserManager()
            val promise: Promise<JsAny> = userManagerSigninSilent(manager)
            val user: JsAny = promise.await<JsAny>()
            consoleLog(toJsString("Silent login successful"))
            user.toOIDCTokens()
        } catch (e: Throwable) {
            consoleWarn(toJsString("Silent login failed (this is often expected): ${e.message}"))
            null
        }
    }

    override suspend fun refreshTokens(): OIDCTokens {
        return try {
            consoleLog(toJsString("Refreshing tokens"))
            val manager = getUserManager()
            val promise: Promise<JsAny> = userManagerSigninSilent(manager)
            val user: JsAny = promise.await<JsAny>()
            consoleLog(toJsString("Token refresh successful"))
            user.toOIDCTokens()
        } catch (e: Throwable) {
            consoleError(toJsString("Token refresh failed: ${e.message}"))
            throw OIDCException(
                message = e.message ?: "Token refresh failed",
                errorCode = OIDCException.ERROR_REFRESH_FAILED,
                cause = e,
            )
        }
    }

    override suspend fun getStoredTokens(): OIDCTokens? {
        return try {
            val manager = getUserManager()
            val promise: Promise<JsAny?> = userManagerGetUser(manager)
            val user: JsAny? = promise.await<JsAny?>()
            user?.toOIDCTokens()
        } catch (e: Throwable) {
            consoleError(toJsString("Error getting stored tokens: ${e.message}"))
            null
        }
    }

    override suspend fun logout() {
        try {
            consoleLog(toJsString("Logging out"))
            val manager = getUserManager()
            // Remove user from local storage
            val promise: Promise<JsAny?> = userManagerRemoveUser(manager)
            promise.await<JsAny?>()
            // Optionally redirect to IDP logout (for single sign-out)
            // Uncomment if you want full SSO logout:
            // userManagerSignoutRedirect(manager).await()
            consoleLog(toJsString("Logout successful"))
        } catch (e: Throwable) {
            consoleError(toJsString("Logout error (continuing anyway): ${e.message}"))
            // Still try to remove user even if signout fails
            try {
                val manager = getUserManager()
                val promise: Promise<JsAny?> = userManagerRemoveUser(manager)
                promise.await<JsAny?>()
            } catch (_: Throwable) {
                // Ignore
            }
        }
    }

    override suspend fun getValidAccessToken(): String? {
        return try {
            val manager = getUserManager()
            // First try to get current user
            val getUserPromise: Promise<JsAny?> = userManagerGetUser(manager)
            var user: JsAny? = getUserPromise.await<JsAny?>()

            // If no user or expired, try silent refresh
            if (user == null || userGetExpired(user)) {
                consoleLog(toJsString("Token expired or missing, attempting silent refresh"))
                val silentPromise: Promise<JsAny> = userManagerSigninSilent(manager)
                user = silentPromise.await<JsAny>()
            }

            userGetAccessToken(user!!)
        } catch (e: Throwable) {
            consoleWarn(toJsString("Could not get valid access token: ${e.message}"))
            null
        }
    }

    companion object {
        /**
         * Create an instance from the OIDC configuration.
         */
        fun create(configuration: OIDCConfiguration): OIDCServiceWasmJs {
            return OIDCServiceWasmJs(configuration)
        }
    }
}

/**
 * Extension function to convert oidc-client-ts User (JsAny) to our OIDCTokens model.
 * Extracts tenant and fineract_client_id from user profile claims.
 */
private fun JsAny.toOIDCTokens(): OIDCTokens {
    val tenant = userGetTenant(this)
    val fineractClientId = userGetFineractClientId(this)

    consoleLog(toJsString("Extracted tenant from JWT: $tenant"))
    if (fineractClientId != null) {
        consoleLog(toJsString("Extracted fineract_client_id from JWT: $fineractClientId"))
    }

    return OIDCTokens(
        accessToken = userGetAccessToken(this),
        refreshToken = userGetRefreshToken(this),
        idToken = userGetIdToken(this),
        expiresAt = userGetExpiresAt(this).toLong(),
        tokenType = userGetTokenType(this),
        scope = userGetScope(this),
        tenant = tenant,
        fineractClientId = fineractClientId,
    )
}
