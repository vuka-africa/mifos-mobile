/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mobile-mobile/blob/master/LICENSE.md
 */
package org.mifos.mobile.core.datastore.di

import org.mifos.mobile.core.datastore.OIDCService
import org.mifos.mobile.core.datastore.model.OIDCConfiguration

/**
 * Platform-specific factory for creating OIDCService instances.
 *
 * This uses the expect/actual pattern to provide different implementations:
 * - wasmJs: Returns OIDCServiceWasmJs using oidc-client-ts
 * - js: Returns OIDCServiceJs using oidc-client-ts
 * - android: Returns null (not yet implemented)
 * - ios: Returns null (not yet implemented)
 *
 * @param configuration The OIDC configuration for the service
 * @return An OIDCService instance, or null if not supported on this platform
 */
expect fun provideOIDCService(configuration: OIDCConfiguration): OIDCService?

/**
 * Platform-specific detection of localhost environment.
 * Used to determine which base URL to use for OIDC configuration.
 *
 * - wasmJs: Checks window.location.hostname for 'localhost' or '127.0.0.1'
 * - android: Always returns false (native apps don't run on localhost)
 * - ios: Always returns false
 */
expect fun isRunningOnLocalhost(): Boolean

/**
 * Create an OIDCConfiguration with automatic localhost detection.
 * When running on localhost (e.g., webpack dev server), uses localhost base URL.
 * Otherwise, uses production base URL.
 */
fun provideOIDCConfiguration(
    issuerUrl: String = "https://auth.sandbox.neobnk.tech",
    clientId: String = "359386148658809622",
): OIDCConfiguration {
    val baseUrl = if (isRunningOnLocalhost()) {
        // Localhost development - detect port from environment
        getLocalhostBaseUrl()
    } else {
        // Production
        "https://app.sandbox.neobnk.tech"
    }

    return OIDCConfiguration.fromEnvironment(
        issuerUrl = issuerUrl,
        clientId = clientId,
        baseUrl = baseUrl,
    )
}

/**
 * Get the localhost base URL, typically from window.location.origin on web.
 */
expect fun getLocalhostBaseUrl(): String
