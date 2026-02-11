/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mobile-mobile/blob/master/LICENSE.md
 */
package org.mifos.mobile.core.network.utils

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.http.HttpStatusCode
import io.ktor.util.AttributeKey
import org.mifos.mobile.core.datastore.UserPreferencesRepository

class KtorInterceptor(
    private val getToken: () -> String?,
    private val onUnauthorized: (suspend () -> Unit)?,
) {
    companion object Plugin : HttpClientPlugin<Config, KtorInterceptor> {
        private const val HEADER_TENANT = "Fineract-Platform-TenantId"
        private const val HEADER_AUTH = "Authorization"
        private const val DEFAULT = "default"
        private const val CONTENT_TYPE = "Content-Type"
        override val key: AttributeKey<KtorInterceptor> = AttributeKey("KtorInterceptor")

        override fun install(plugin: KtorInterceptor, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.State) {
                context.header(CONTENT_TYPE, "application/json")
                context.header("Accept", "application/json")
                context.header("Accept", "*/*")
                context.header(HEADER_TENANT, DEFAULT)

                plugin.getToken()?.let { token ->
                    if (token.isNotEmpty()) {
                        context.headers[HEADER_AUTH] = "Basic $token"
                    }
                }
            }
            scope.responsePipeline.intercept(HttpResponsePipeline.After) {
                if (context.response.status == HttpStatusCode.Unauthorized) {
                    runCatching {
                        plugin.onUnauthorized?.invoke()
                    }.onFailure { throwable ->
                        throwable.printStackTrace()
                    }
                }
                proceed()
            }
        }

        override fun prepare(block: Config.() -> Unit): KtorInterceptor {
            val config = Config().apply(block)
            return KtorInterceptor(
                getToken = config.getToken,
                onUnauthorized = config.onUnauthorized,
            )
        }
    }
}

class Config {
    lateinit var getToken: () -> String?
    var onUnauthorized: (suspend () -> Unit)? = null
}

class KtorInterceptorRe(
    private val repository: UserPreferencesRepository,
) {
    companion object Plugin : HttpClientPlugin<ConfigRe, KtorInterceptorRe> {
        private const val HEADER_TENANT = "Fineract-Platform-TenantId"
        private const val HEADER_AUTH = "Authorization"
        private const val DEFAULT = "venus"
        private const val CONTENT_TYPE = "Content-Type"
        override val key: AttributeKey<KtorInterceptorRe> = AttributeKey("KtorInterceptorRe")

        override fun install(plugin: KtorInterceptorRe, scope: HttpClient) {
            val token = plugin.repository.token.value

            scope.requestPipeline.intercept(HttpRequestPipeline.State) {
                context.header(CONTENT_TYPE, "application/json")
                context.header("Accept", "application/json")
                context.header(HEADER_TENANT, DEFAULT)

                token?.let { token ->
                    if (token.isNotEmpty()) {
                        context.headers[HEADER_AUTH] = "Basic $token"
                    }
                }
            }
        }

        override fun prepare(block: ConfigRe.() -> Unit): KtorInterceptorRe {
            val config = ConfigRe().apply(block)
            return KtorInterceptorRe(config.repository)
        }
    }
}

class ConfigRe {
    lateinit var repository: UserPreferencesRepository
}

/**
 * Authentication mode for the HTTP client.
 */
enum class AuthMode {
    /** Traditional Basic authentication using base64-encoded username:password */
    BASIC,
    /** OAuth2/OIDC Bearer token authentication using JWT access tokens */
    BEARER,
}

/**
 * OIDC-aware Ktor HTTP interceptor that supports both Basic and Bearer authentication.
 *
 * This interceptor can operate in two modes:
 * - BASIC: Uses base64-encoded credentials (legacy mode)
 * - BEARER: Uses JWT access tokens from OIDC authentication (recommended)
 *
 * When using BEARER mode with Zitadel OIDC, the JWT contains claims that can be
 * used to extract tenant information for multi-tenant deployments.
 *
 * Note: getAccessToken should return a cached token value. Token refresh should be
 * handled separately (e.g., by OIDCService with automatic silent refresh).
 *
 * @param getAccessToken Lambda that returns the current cached OIDC access token
 * @param getBasicToken Lambda that returns the Basic auth token (for Basic mode, fallback)
 * @param authMode The authentication mode to use
 * @param getTenant Lambda that returns the tenant identifier
 * @param onUnauthorized Callback invoked when a 401 response is received
 */
class KtorOidcInterceptor(
    private val getAccessToken: () -> String?,
    private val getBasicToken: () -> String?,
    private val authMode: () -> AuthMode,
    private val getTenant: () -> String,
    private val onUnauthorized: (suspend () -> Unit)?,
) {
    companion object Plugin : HttpClientPlugin<OidcConfig, KtorOidcInterceptor> {
        private const val HEADER_TENANT = "Fineract-Platform-TenantId"
        private const val HEADER_AUTH = "Authorization"
        private const val CONTENT_TYPE = "Content-Type"

        override val key: AttributeKey<KtorOidcInterceptor> = AttributeKey("KtorOidcInterceptor")

        override fun install(plugin: KtorOidcInterceptor, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.State) {
                // Set common headers
                context.header(CONTENT_TYPE, "application/json")
                context.header("Accept", "application/json")
                context.header("Accept", "*/*")

                // Set tenant header
                context.header(HEADER_TENANT, plugin.getTenant())

                // Set authentication header based on mode
                when (plugin.authMode()) {
                    AuthMode.BEARER -> {
                        // Use OIDC JWT access token (cached value from OIDCService)
                        val token = plugin.getAccessToken()
                        if (!token.isNullOrEmpty()) {
                            context.headers[HEADER_AUTH] = "Bearer $token"
                        }
                    }
                    AuthMode.BASIC -> {
                        // Fallback to Basic auth
                        plugin.getBasicToken()?.let { token ->
                            if (token.isNotEmpty()) {
                                context.headers[HEADER_AUTH] = "Basic $token"
                            }
                        }
                    }
                }
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.After) {
                if (context.response.status == HttpStatusCode.Unauthorized) {
                    runCatching {
                        plugin.onUnauthorized?.invoke()
                    }.onFailure { throwable ->
                        throwable.printStackTrace()
                    }
                }
                proceed()
            }
        }

        override fun prepare(block: OidcConfig.() -> Unit): KtorOidcInterceptor {
            val config = OidcConfig().apply(block)
            return KtorOidcInterceptor(
                getAccessToken = config.getAccessToken,
                getBasicToken = config.getBasicToken,
                authMode = config.authMode,
                getTenant = config.getTenant,
                onUnauthorized = config.onUnauthorized,
            )
        }
    }
}

/**
 * Configuration for KtorOidcInterceptor.
 */
class OidcConfig {
    /** Lambda to get the cached OIDC access token for Bearer authentication */
    var getAccessToken: () -> String? = { null }

    /** Lambda to get the Basic auth token (fallback) */
    var getBasicToken: () -> String? = { null }

    /** Lambda to determine the authentication mode */
    var authMode: () -> AuthMode = { AuthMode.BASIC }

    /** Lambda to get the tenant identifier */
    var getTenant: () -> String = { "default" }

    /** Callback when 401 Unauthorized is received */
    var onUnauthorized: (suspend () -> Unit)? = null
}
