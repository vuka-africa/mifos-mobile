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

import kotlin.js.Promise

/**
 * WASM JS interop for oidc-client-ts using @JsFun.
 *
 * wasmJs cannot use @JsModule imports like Kotlin/JS, so we use @JsFun
 * with inline JavaScript to access the oidc-client-ts library bundled by webpack.
 *
 * @see https://github.com/authts/oidc-client-ts
 */

// =============================================================================
// UserManager Settings Creation
// =============================================================================

/**
 * Create a UserManagerSettings object for oidc-client-ts.
 * This creates a plain JavaScript object with the OIDC configuration.
 */
@JsFun(
    """
(authority, clientId, redirectUri, popupRedirectUri, silentRedirectUri, postLogoutRedirectUri, scope) => ({
    authority: authority,
    client_id: clientId,
    redirect_uri: redirectUri,
    popup_redirect_uri: popupRedirectUri,
    silent_redirect_uri: silentRedirectUri,
    post_logout_redirect_uri: postLogoutRedirectUri,
    response_type: "code",
    scope: scope,
    automaticSilentRenew: true,
    includeIdTokenInSilentRenew: true,
    loadUserInfo: true
})
"""
)
external fun createUserManagerSettings(
    authority: String,
    clientId: String,
    redirectUri: String,
    popupRedirectUri: String?,
    silentRedirectUri: String?,
    postLogoutRedirectUri: String?,
    scope: String,
): JsAny

// =============================================================================
// UserManager Creation and Methods
// =============================================================================

/**
 * Create a UserManager instance from the oidc-client-ts library.
 * Uses dynamic import() which webpack will bundle correctly for browser use.
 * Returns a Promise that resolves to the UserManager.
 */
@JsFun(
    """
(settings) => {
    return import('oidc-client-ts').then(module => new module.UserManager(settings));
}
"""
)
external fun createUserManagerAsync(settings: JsAny): Promise<JsAny>

/**
 * Get the currently authenticated user.
 * Returns null if not authenticated.
 */
@JsFun("(um) => um.getUser()")
external fun userManagerGetUser(um: JsAny): Promise<JsAny?>

/**
 * Initiate popup-based sign-in flow.
 * Opens a popup window for authentication.
 */
@JsFun("(um) => um.signinPopup()")
external fun userManagerSigninPopup(um: JsAny): Promise<JsAny>

/**
 * Perform silent sign-in using an iframe.
 * Used for token refresh and session checking.
 */
@JsFun("(um) => um.signinSilent()")
external fun userManagerSigninSilent(um: JsAny): Promise<JsAny>

/**
 * Remove the user from local storage (local logout).
 */
@JsFun("(um) => um.removeUser()")
external fun userManagerRemoveUser(um: JsAny): Promise<JsAny?>

/**
 * Redirect to identity provider logout endpoint.
 */
@JsFun("(um) => um.signoutRedirect()")
external fun userManagerSignoutRedirect(um: JsAny): Promise<JsAny?>

// =============================================================================
// User Property Accessors
// =============================================================================

/**
 * Get the access token from a User object.
 */
@JsFun("(user) => user.access_token")
external fun userGetAccessToken(user: JsAny): String

/**
 * Get the refresh token from a User object (may be null).
 */
@JsFun("(user) => user.refresh_token || null")
external fun userGetRefreshToken(user: JsAny): String?

/**
 * Get the ID token from a User object (may be null).
 */
@JsFun("(user) => user.id_token || null")
external fun userGetIdToken(user: JsAny): String?

/**
 * Get the token type from a User object.
 */
@JsFun("(user) => user.token_type || 'Bearer'")
external fun userGetTokenType(user: JsAny): String

/**
 * Get the scope from a User object (may be null).
 */
@JsFun("(user) => user.scope || null")
external fun userGetScope(user: JsAny): String?

/**
 * Get the expiration timestamp from a User object.
 */
@JsFun("(user) => user.expires_at || 0")
external fun userGetExpiresAt(user: JsAny): Int

/**
 * Check if the user token is expired.
 */
@JsFun("(user) => user.expired === true")
external fun userGetExpired(user: JsAny): Boolean

// =============================================================================
// User Profile/Claims Accessors
// =============================================================================

/**
 * Get the user profile object containing ID token claims.
 * Returns null if no profile is available.
 */
@JsFun("(user) => user.profile || null")
external fun userGetProfile(user: JsAny): JsAny?

/**
 * Get a specific claim from the user profile.
 * @param user The User object
 * @param claimName The claim name to retrieve
 * @return The claim value as a string, or null if not found
 */
@JsFun("(user, claimName) => user.profile && user.profile[claimName] ? String(user.profile[claimName]) : null")
external fun userGetClaim(user: JsAny, claimName: String): String?

/**
 * Get the tenant identifier from the user profile.
 * Checks multiple possible claim locations in order:
 * 1. Custom "tenant" claim (explicit tenant mapping)
 * 2. Zitadel org metadata "urn:zitadel:iam:org:metadata:x:tenant"
 * 3. Zitadel org domain primary "urn:zitadel:iam:org:domain:primary" (first part)
 * 4. Falls back to "default"
 */
@JsFun(
    """
(user) => {
    if (!user || !user.profile) {
        console.log('[OIDC] No user or profile, using default tenant');
        return 'default';
    }
    const p = user.profile;

    // Debug: log all profile keys
    console.log('[OIDC] Profile keys:', Object.keys(p));
    console.log('[OIDC] Full profile:', JSON.stringify(p, null, 2));

    // 1. Explicit tenant claim (set by Zitadel addTenantClaim action)
    if (p.tenant) {
        console.log('[OIDC] Found tenant claim:', p.tenant);
        return String(p.tenant);
    }

    // 2. Zitadel org resource owner name (fallback)
    const orgName = p['urn:zitadel:iam:user:resourceowner:name'];
    if (orgName) {
        // Remove spaces and special chars, lowercase (e.g., "Acme Bank" -> "acmebank")
        const normalized = String(orgName).replace(/[^a-zA-Z0-9]/g, '').toLowerCase();
        console.log('[OIDC] Using org name as tenant:', orgName, '->', normalized);
        return normalized;
    }

    // 3. Zitadel org domain primary (e.g., "acme-bank.auth.sandbox.neobnk.tech" -> "acmebank")
    const domain = p['urn:zitadel:iam:user:resourceowner:primary_domain'];
    if (domain) {
        const parts = domain.split('.');
        if (parts.length > 0) {
            const normalized = parts[0].replace(/-/g, '').toLowerCase();
            console.log('[OIDC] Using org domain as tenant:', domain, '->', normalized);
            return normalized;
        }
    }

    // 4. Fallback
    console.log('[OIDC] No tenant info found, using default');
    return 'default';
}
"""
)
external fun userGetTenant(user: JsAny): String

/**
 * Get the Fineract client ID from the user profile.
 * Checks for:
 * 1. Custom "fineract_client_id" claim
 * 2. Zitadel org metadata "urn:zitadel:iam:user:metadata:x:fineract_client_id"
 * Returns null if not found (app should call /self/clients endpoint).
 */
@JsFun(
    """
(user) => {
    if (!user || !user.profile) return null;
    const p = user.profile;

    // 1. Explicit fineract_client_id claim
    if (p.fineract_client_id) return String(p.fineract_client_id);

    // 2. Zitadel user metadata
    if (p['urn:zitadel:iam:user:metadata:x:fineract_client_id']) {
        return String(p['urn:zitadel:iam:user:metadata:x:fineract_client_id']);
    }

    return null;
}
"""
)
external fun userGetFineractClientId(user: JsAny): String?

// =============================================================================
// Console Logging for Debugging
// =============================================================================

/**
 * Log to browser console.
 */
@JsFun("(...args) => console.log('[OIDC]', ...args)")
external fun consoleLog(vararg args: JsAny?)

/**
 * Log warning to browser console.
 */
@JsFun("(...args) => console.warn('[OIDC]', ...args)")
external fun consoleWarn(vararg args: JsAny?)

/**
 * Log error to browser console.
 */
@JsFun("(...args) => console.error('[OIDC]', ...args)")
external fun consoleError(vararg args: JsAny?)

// =============================================================================
// String Conversion Helpers
// =============================================================================

/**
 * Convert a Kotlin String to a JsAny for passing to console.log etc.
 */
@JsFun("(s) => s")
external fun toJsString(s: String): JsAny
