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

/**
 * JavaScript/Web implementation of OIDCService using oidc-client-ts.
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
class OIDCServiceJs(
    override val configuration: OIDCConfiguration,
) : OIDCService {

    private val userManager: UserManager by lazy {
        val settings = createUserManagerSettings(
            authority = configuration.issuerUrl,
            clientId = configuration.clientId,
            redirectUri = configuration.redirectUri,
            popupRedirectUri = configuration.popupCallbackUri,
            silentRedirectUri = configuration.silentRefreshUri,
            postLogoutRedirectUri = configuration.postLogoutRedirectUri,
            scope = configuration.scopesString(),
        )
        UserManager(settings)
    }

    override suspend fun isAuthenticated(): Boolean {
        return try {
            val user = userManager.getUser().await()
            user != null && user.expired != true
        } catch (e: Throwable) {
            console.error("OIDC: Error checking authentication status", e)
            false
        }
    }

    override suspend fun login(): OIDCTokens {
        return try {
            console.log("OIDC: Initiating popup login")
            val user = userManager.signinPopup().await()
            console.log("OIDC: Login successful")
            user.toOIDCTokens()
        } catch (e: Throwable) {
            console.error("OIDC: Login failed", e)
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
            console.log("OIDC: Attempting silent login")
            val user = userManager.signinSilent().await()
            console.log("OIDC: Silent login successful")
            user.toOIDCTokens()
        } catch (e: Throwable) {
            console.warn("OIDC: Silent login failed (this is often expected)", e)
            null
        }
    }

    override suspend fun refreshTokens(): OIDCTokens {
        return try {
            console.log("OIDC: Refreshing tokens")
            val user = userManager.signinSilent().await()
            console.log("OIDC: Token refresh successful")
            user.toOIDCTokens()
        } catch (e: Throwable) {
            console.error("OIDC: Token refresh failed", e)
            throw OIDCException(
                message = e.message ?: "Token refresh failed",
                errorCode = OIDCException.ERROR_REFRESH_FAILED,
                cause = e,
            )
        }
    }

    override suspend fun getStoredTokens(): OIDCTokens? {
        return try {
            val user = userManager.getUser().await()
            user?.toOIDCTokens()
        } catch (e: Throwable) {
            console.error("OIDC: Error getting stored tokens", e)
            null
        }
    }

    override suspend fun logout() {
        try {
            console.log("OIDC: Logging out")
            // Remove user from local storage
            userManager.removeUser().await()
            // Optionally redirect to IDP logout (for single sign-out)
            // Uncomment if you want full SSO logout:
            // userManager.signoutRedirect().await()
            console.log("OIDC: Logout successful")
        } catch (e: Throwable) {
            console.error("OIDC: Logout error (continuing anyway)", e)
            // Still try to remove user even if signout fails
            try {
                userManager.removeUser().await()
            } catch (_: Throwable) {
                // Ignore
            }
        }
    }

    override suspend fun getValidAccessToken(): String? {
        return try {
            // First try to get current user
            var user = userManager.getUser().await()

            // If no user or expired, try silent refresh
            if (user == null || user.expired == true) {
                console.log("OIDC: Token expired or missing, attempting silent refresh")
                user = userManager.signinSilent().await()
            }

            user.access_token
        } catch (e: Throwable) {
            console.warn("OIDC: Could not get valid access token", e)
            null
        }
    }

    /**
     * Handle the popup callback - call this from popup-callback.html
     */
    suspend fun handlePopupCallback() {
        try {
            userManager.signinPopupCallback().await()
        } catch (e: Throwable) {
            console.error("OIDC: Popup callback error", e)
            throw e
        }
    }

    /**
     * Handle the silent refresh callback - call this from silent-refresh.html
     */
    suspend fun handleSilentCallback() {
        try {
            userManager.signinSilentCallback().await()
        } catch (e: Throwable) {
            console.error("OIDC: Silent callback error", e)
            throw e
        }
    }

    companion object {
        /**
         * Create an instance from the global OIDC configuration.
         * Configuration should be provided via environment/build config.
         */
        fun create(configuration: OIDCConfiguration): OIDCServiceJs {
            return OIDCServiceJs(configuration)
        }
    }
}

/**
 * Extension function to convert oidc-client-ts User to our OIDCTokens model.
 * Extracts tenant and fineract_client_id from user profile claims.
 */
private fun User.toOIDCTokens(): OIDCTokens {
    val tenant = extractTenant()
    val fineractClientId = extractFineractClientId()

    console.log("OIDC: Extracted tenant from JWT: $tenant")
    if (fineractClientId != null) {
        console.log("OIDC: Extracted fineract_client_id from JWT: $fineractClientId")
    }

    return OIDCTokens(
        accessToken = access_token,
        refreshToken = refresh_token,
        idToken = id_token,
        expiresAt = expires_at?.toLong() ?: 0L,
        tokenType = token_type,
        scope = scope,
        tenant = tenant,
        fineractClientId = fineractClientId,
    )
}

/**
 * Extract tenant from user profile claims.
 * The tenant claim is set by the Zitadel addTenantClaim action.
 */
private fun User.extractTenant(): String {
    val p = profile.asDynamic()

    // Debug: log profile
    console.log("OIDC: Profile keys:", js("Object.keys(p)"))

    // 1. Explicit tenant claim (set by Zitadel addTenantClaim action)
    val tenant = p.tenant
    if (tenant != null && tenant != undefined) {
        console.log("OIDC: Found tenant claim:", tenant)
        return tenant.toString()
    }

    // 2. Fallback to org name
    val orgName = p["urn:zitadel:iam:user:resourceowner:name"]
    if (orgName != null && orgName != undefined) {
        val normalized = orgName.toString().replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
        console.log("OIDC: Using org name as tenant:", orgName, "->", normalized)
        return normalized
    }

    // 3. Fallback
    console.log("OIDC: No tenant found, using default")
    return "default"
}

/**
 * Extract Fineract client ID from user profile claims.
 */
private fun User.extractFineractClientId(): String? {
    val p = profile.asDynamic()

    // 1. Explicit fineract_client_id claim
    val clientId = p.fineract_client_id
    if (clientId != null && clientId != undefined) return clientId.toString()

    // 2. Zitadel user metadata
    val metaClientId = p["urn:zitadel:iam:user:metadata:x:fineract_client_id"]
    if (metaClientId != null && metaClientId != undefined) return metaClientId.toString()

    return null
}

/**
 * Console logging helpers for debugging.
 */
private external object console {
    fun log(vararg args: Any?)
    fun warn(vararg args: Any?)
    fun error(vararg args: Any?)
}
