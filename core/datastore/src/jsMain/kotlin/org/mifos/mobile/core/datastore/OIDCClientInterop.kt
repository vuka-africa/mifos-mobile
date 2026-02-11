/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mobile-mobile/blob/master/LICENSE.md
 */
@file:JsModule("oidc-client-ts")
@file:JsNonModule

package org.mifos.mobile.core.datastore

import kotlin.js.Promise

/**
 * Kotlin/JS interop declarations for oidc-client-ts library.
 *
 * These external declarations allow us to use the oidc-client-ts npm package
 * from Kotlin/JS code. The library handles all the complexity of OIDC flows
 * including PKCE, token storage, and silent refresh.
 *
 * @see https://github.com/authts/oidc-client-ts
 */

/**
 * UserManager is the main entry point for authentication operations.
 * It manages the user session and provides methods for login, logout,
 * and token refresh.
 */
external class UserManager(settings: UserManagerSettings) {
    /**
     * Initiate a popup-based sign-in flow.
     * Opens a popup window for the user to authenticate, then returns
     * the User object with tokens.
     */
    fun signinPopup(args: SigninPopupArgs = definedExternally): Promise<User>

    /**
     * Handle the popup callback - called in the popup window after
     * authentication completes.
     */
    fun signinPopupCallback(url: String = definedExternally): Promise<Unit>

    /**
     * Attempt silent sign-in using an iframe.
     * This is used for token refresh and session checking.
     */
    fun signinSilent(args: SigninSilentArgs = definedExternally): Promise<User>

    /**
     * Handle the silent sign-in callback - called in the iframe.
     */
    fun signinSilentCallback(url: String = definedExternally): Promise<Unit>

    /**
     * Get the currently authenticated user, or null if not authenticated.
     */
    fun getUser(): Promise<User?>

    /**
     * Remove the user from storage (local logout only).
     */
    fun removeUser(): Promise<Unit>

    /**
     * Initiate a popup-based sign-out flow.
     */
    fun signoutPopup(args: SignoutPopupArgs = definedExternally): Promise<Unit>

    /**
     * Redirect to the identity provider's logout endpoint.
     */
    fun signoutRedirect(args: SignoutRedirectArgs = definedExternally): Promise<Unit>
}

/**
 * Configuration settings for UserManager.
 */
external interface UserManagerSettings {
    var authority: String
    var client_id: String
    var redirect_uri: String
    var popup_redirect_uri: String?
    var silent_redirect_uri: String?
    var post_logout_redirect_uri: String?
    var response_type: String?
    var scope: String?
    var automaticSilentRenew: Boolean?
    var includeIdTokenInSilentRenew: Boolean?
    var loadUserInfo: Boolean?
}

/**
 * Arguments for popup sign-in.
 */
external interface SigninPopupArgs {
    var popup_redirect_uri: String?
    var extraQueryParams: dynamic
}

/**
 * Arguments for silent sign-in.
 */
external interface SigninSilentArgs {
    var silent_redirect_uri: String?
    var extraQueryParams: dynamic
}

/**
 * Arguments for popup sign-out.
 */
external interface SignoutPopupArgs {
    var popup_redirect_uri: String?
}

/**
 * Arguments for redirect sign-out.
 */
external interface SignoutRedirectArgs {
    var post_logout_redirect_uri: String?
}

/**
 * Represents an authenticated user with their tokens and profile.
 */
external interface User {
    val access_token: String
    val refresh_token: String?
    val id_token: String?
    val token_type: String
    val scope: String?
    val expires_at: Int?
    val expired: Boolean?
    val profile: UserProfile
}

/**
 * User profile information from the ID token.
 */
external interface UserProfile {
    val sub: String
    val name: String?
    val email: String?
    val email_verified: Boolean?
    val preferred_username: String?
}
