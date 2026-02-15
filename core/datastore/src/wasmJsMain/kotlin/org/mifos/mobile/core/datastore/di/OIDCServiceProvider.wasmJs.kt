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
import org.mifos.mobile.core.datastore.OIDCServiceWasmJs
import org.mifos.mobile.core.datastore.model.OIDCConfiguration

/**
 * wasmJs implementation of OIDCService provider.
 * Returns OIDCServiceWasmJs which uses oidc-client-ts via @JsFun interop.
 */
actual fun provideOIDCService(configuration: OIDCConfiguration): OIDCService? {
    return OIDCServiceWasmJs.create(configuration)
}

/**
 * Check if the browser is running on localhost.
 * Uses window.location.hostname to detect local development environment.
 */
@JsFun("() => window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'")
private external fun isLocalhostJs(): Boolean

actual fun isRunningOnLocalhost(): Boolean = isLocalhostJs()

/**
 * Get the current origin (scheme + host + port) from window.location.
 * This allows automatic detection of the dev server port (8080, 3000, etc.)
 */
@JsFun("() => window.location.origin")
private external fun getLocationOriginJs(): String

actual fun getLocalhostBaseUrl(): String = getLocationOriginJs()
