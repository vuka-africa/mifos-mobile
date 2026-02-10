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
 * Android implementation of OIDCService.
 *
 * TODO: Implement using AppAuth-Android library for native OIDC support.
 * For now, this is a stub that throws NotImplementedError.
 *
 * Future implementation will use:
 * - AppAuth-Android for the OIDC flow
 * - Custom tabs for the authorization flow
 * - EncryptedSharedPreferences for token storage
 *
 * @see https://github.com/openid/AppAuth-Android
 */
class OIDCServiceAndroid(
    override val configuration: OIDCConfiguration,
) : OIDCService {

    override suspend fun isAuthenticated(): Boolean {
        // TODO: Check stored tokens
        throw NotImplementedError("Android OIDC not yet implemented. Use AppAuth-Android.")
    }

    override suspend fun login(): OIDCTokens {
        // TODO: Implement using AppAuth-Android
        throw NotImplementedError("Android OIDC not yet implemented. Use AppAuth-Android.")
    }

    override suspend fun silentLogin(): OIDCTokens? {
        // TODO: Implement token refresh
        return null
    }

    override suspend fun refreshTokens(): OIDCTokens {
        // TODO: Implement using stored refresh token
        throw NotImplementedError("Android OIDC not yet implemented. Use AppAuth-Android.")
    }

    override suspend fun getStoredTokens(): OIDCTokens? {
        // TODO: Read from EncryptedSharedPreferences
        return null
    }

    override suspend fun logout() {
        // TODO: Clear stored tokens and optionally call end session endpoint
    }

    override suspend fun getValidAccessToken(): String? {
        // TODO: Get token, refresh if needed
        return null
    }

    companion object {
        fun create(configuration: OIDCConfiguration): OIDCServiceAndroid {
            return OIDCServiceAndroid(configuration)
        }
    }
}
