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
 * Android implementation of OIDCService provider.
 * Returns null as OIDC is not yet implemented for Android.
 *
 * Future: Implement using AppAuth-Android library.
 */
actual fun provideOIDCService(configuration: OIDCConfiguration): OIDCService? {
    // OIDC not yet implemented for Android
    // Future: return OIDCServiceAndroid(configuration)
    return null
}

/**
 * Android apps don't run on localhost in the web sense.
 * Always returns false.
 */
actual fun isRunningOnLocalhost(): Boolean = false

/**
 * Android apps use production URL.
 * This is a fallback that should never be called since isRunningOnLocalhost() returns false.
 */
actual fun getLocalhostBaseUrl(): String = "https://app.sandbox.neobnk.tech"
