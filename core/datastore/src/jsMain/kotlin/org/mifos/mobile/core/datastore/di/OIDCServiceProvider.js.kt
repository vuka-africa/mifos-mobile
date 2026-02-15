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

import kotlinx.browser.window
import org.mifos.mobile.core.datastore.OIDCService
import org.mifos.mobile.core.datastore.OIDCServiceJs
import org.mifos.mobile.core.datastore.model.OIDCConfiguration

/**
 * JavaScript implementation of OIDCService provider.
 * Returns OIDCServiceJs which uses oidc-client-ts via @JsModule.
 */
actual fun provideOIDCService(configuration: OIDCConfiguration): OIDCService? {
    return OIDCServiceJs.create(configuration)
}

/**
 * Check if the browser is running on localhost.
 * Uses window.location.hostname to detect local development environment.
 */
actual fun isRunningOnLocalhost(): Boolean {
    val hostname = window.location.hostname
    return hostname == "localhost" || hostname == "127.0.0.1"
}

/**
 * Get the current origin (scheme + host + port) from window.location.
 * This allows automatic detection of the dev server port (8080, 3000, etc.)
 */
actual fun getLocalhostBaseUrl(): String = window.location.origin
