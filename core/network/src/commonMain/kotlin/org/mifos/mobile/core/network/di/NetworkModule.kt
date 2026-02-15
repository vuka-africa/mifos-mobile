/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mobile-mobile/blob/master/LICENSE.md
 */
package org.mifos.mobile.core.network.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.Auth
import org.koin.dsl.module
import org.mifos.mobile.core.datastore.OIDCService
import org.mifos.mobile.core.datastore.UserPreferencesRepository
import org.mifos.mobile.core.network.DataManager
import org.mifos.mobile.core.network.KtorfitClient
import org.mifos.mobile.core.network.ktorHttpClient
import org.mifos.mobile.core.network.utils.AuthMode
import org.mifos.mobile.core.network.utils.BaseURL
import org.mifos.mobile.core.network.utils.KtorOidcInterceptor

val NetworkModule = module {

    single<HttpClient>(KtorClient) {
        val preferencesRepository = get<UserPreferencesRepository>()
        val oidcService = getOrNull<OIDCService>()

        ktorHttpClient.config {
            install(Auth)
            install(KtorOidcInterceptor) {
                // Get OIDC access token if available
                getAccessToken = {
                    preferencesRepository.oidcTokens.value?.accessToken
                }

                // Fallback to Basic auth token
                getBasicToken = {
                    preferencesRepository.token.value
                }

                // Determine auth mode based on whether OIDC is enabled
                authMode = {
                    if (preferencesRepository.isOidcEnabled.value && oidcService != null) {
                        AuthMode.BEARER
                    } else {
                        AuthMode.BASIC
                    }
                }

                // Get tenant from OIDC tokens (extracted from JWT claims)
                getTenant = {
                    preferencesRepository.oidcTokens.value?.tenant ?: "default"
                }

                onUnauthorized = suspend {
                    preferencesRepository.logOut()
                }
            }
        }
    }

    single<KtorfitClient>(MifosClient) {
        KtorfitClient.builder()
            .httpClient(get(KtorClient))
            .baseURL(BaseURL().url)
            .build()
    }

    single {
        DataManager(ktorfitClient = get(MifosClient))
    }
}
