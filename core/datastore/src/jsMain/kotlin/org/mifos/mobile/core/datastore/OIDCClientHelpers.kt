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

/**
 * Helper function to create UserManagerSettings object for oidc-client-ts.
 *
 * This creates a JavaScript object that conforms to the UserManagerSettings interface
 * expected by the oidc-client-ts library.
 */
fun createUserManagerSettings(
    authority: String,
    clientId: String,
    redirectUri: String,
    popupRedirectUri: String?,
    silentRedirectUri: String?,
    postLogoutRedirectUri: String?,
    scope: String,
): UserManagerSettings {
    val settings: UserManagerSettings = js("({})").unsafeCast<UserManagerSettings>()
    settings.authority = authority
    settings.client_id = clientId
    settings.redirect_uri = redirectUri
    settings.popup_redirect_uri = popupRedirectUri
    settings.silent_redirect_uri = silentRedirectUri
    settings.post_logout_redirect_uri = postLogoutRedirectUri
    settings.response_type = "code"
    settings.scope = scope
    settings.automaticSilentRenew = true
    settings.includeIdTokenInSilentRenew = true
    settings.loadUserInfo = true
    return settings
}
