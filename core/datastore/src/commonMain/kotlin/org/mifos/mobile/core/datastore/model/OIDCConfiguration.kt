/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mobile-mobile/blob/master/LICENSE.md
 */
package org.mifos.mobile.core.datastore.model

import kotlinx.serialization.Serializable

/**
 * Configuration for OIDC authentication with Zitadel.
 *
 * This configuration is used to set up the OIDC client for authentication flows.
 * For web targets, this integrates with oidc-client-ts library.
 *
 * @property issuerUrl The OIDC issuer URL (e.g., "https://auth.sandbox.neobnk.tech")
 * @property clientId The OIDC client ID registered in Zitadel
 * @property redirectUri The URI to redirect to after authentication
 * @property popupCallbackUri The URI for popup callback (web only)
 * @property silentRefreshUri The URI for silent token refresh (web only)
 * @property postLogoutRedirectUri The URI to redirect to after logout
 * @property scopes The OIDC scopes to request
 */
@Serializable
data class OIDCConfiguration(
    val issuerUrl: String,
    val clientId: String,
    val redirectUri: String,
    val popupCallbackUri: String = "$redirectUri/popup-callback.html",
    val silentRefreshUri: String = "$redirectUri/silent-refresh.html",
    val postLogoutRedirectUri: String = redirectUri,
    val scopes: List<String> = DEFAULT_SCOPES,
) {
    companion object {
        val DEFAULT_SCOPES = listOf(
            "openid",
            "profile",
            "email",
            "offline_access",
        )

        /**
         * Create configuration from environment variables or build config.
         * This is typically called during app initialization.
         */
        fun fromEnvironment(
            issuerUrl: String,
            clientId: String,
            baseUrl: String,
        ): OIDCConfiguration = OIDCConfiguration(
            issuerUrl = issuerUrl,
            clientId = clientId,
            redirectUri = baseUrl,
            popupCallbackUri = "$baseUrl/popup-callback.html",
            silentRefreshUri = "$baseUrl/silent-refresh.html",
            postLogoutRedirectUri = baseUrl,
        )
    }

    /**
     * Get scopes as a space-separated string for OIDC requests.
     */
    fun scopesString(): String = scopes.joinToString(" ")

    /**
     * Build the authorization URL for initiating the OIDC flow.
     */
    fun buildAuthorizationUrl(
        state: String,
        codeChallenge: String,
        codeChallengeMethod: String = "S256",
    ): String {
        return buildString {
            append(issuerUrl)
            if (!issuerUrl.endsWith("/")) append("/")
            append("oauth/v2/authorize")
            append("?client_id=").append(clientId)
            append("&redirect_uri=").append(redirectUri)
            append("&response_type=code")
            append("&scope=").append(scopesString())
            append("&state=").append(state)
            append("&code_challenge=").append(codeChallenge)
            append("&code_challenge_method=").append(codeChallengeMethod)
        }
    }
}
