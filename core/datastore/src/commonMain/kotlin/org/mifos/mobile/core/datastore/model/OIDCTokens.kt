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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents OIDC tokens received from the identity provider (Zitadel).
 *
 * @property accessToken The JWT access token used for API authorization
 * @property refreshToken The refresh token for obtaining new access tokens
 * @property idToken The ID token containing user identity claims
 * @property expiresAt Unix timestamp (seconds) when the access token expires
 * @property tokenType The token type, typically "Bearer"
 * @property scope The scopes granted for this token
 */
@Serializable
data class OIDCTokens(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    @SerialName("id_token")
    val idToken: String? = null,
    @SerialName("expires_at")
    val expiresAt: Long,
    @SerialName("token_type")
    val tokenType: String = "Bearer",
    @SerialName("scope")
    val scope: String? = null,
) {
    /**
     * Check if the access token has expired.
     * Includes a 60-second buffer to account for clock skew and network latency.
     */
    fun isExpired(): Boolean {
        val currentTimeSeconds = currentTimeMillis() / 1000
        return currentTimeSeconds >= (expiresAt - 60)
    }

    /**
     * Check if we have a refresh token available for token renewal.
     */
    fun canRefresh(): Boolean = !refreshToken.isNullOrBlank()
}

/**
 * Get current time in milliseconds - implemented per platform.
 */
internal expect fun currentTimeMillis(): Long
