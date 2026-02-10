/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mobile-mobile/blob/master/LICENSE.md
 */
package org.mifos.mobile.feature.auth.login

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mifos_mobile.feature.auth.generated.resources.Res
import mifos_mobile.feature.auth.generated.resources.feature_sign_in_password_error
import mifos_mobile.feature.auth.generated.resources.feature_sign_in_username_error
import org.jetbrains.compose.resources.StringResource
import org.mifos.mobile.core.common.DataState
import org.mifos.mobile.core.data.repository.UserAuthRepository
import org.mifos.mobile.core.datastore.OIDCException
import org.mifos.mobile.core.datastore.OIDCService
import org.mifos.mobile.core.datastore.UserPreferencesRepository
import org.mifos.mobile.core.datastore.model.UserData
import org.mifos.mobile.core.model.entity.User
import org.mifos.mobile.core.ui.utils.BaseViewModel
import org.mifos.mobile.core.ui.utils.ScreenUiState

class LoginViewModel(
    private val userAuthRepositoryImpl: UserAuthRepository,
    private val userPreferencesRepositoryImpl: UserPreferencesRepository,
    private val oidcService: OIDCService? = null, // Optional - null when OIDC not configured
    savedStateHandle: SavedStateHandle,
) : BaseViewModel<LoginState, LoginEvent, LoginAction>(
    initialState = LoginState(
        uiState = ScreenUiState.Success,
        isOidcAvailable = false, // Will be updated in init
    ),
) {

    private var loginJob: Job? = null

    init {
        savedStateHandle.get<String>("username")?.let {
            trySendAction(LoginAction.UsernameChanged(it))
        }

        // Check if OIDC is available
        updateState { it.copy(isOidcAvailable = oidcService != null) }

        // Try silent OIDC login if service is available
        if (oidcService != null) {
            viewModelScope.launch {
                trySilentOidcLogin()
            }
        }
    }

    /**
     * Attempt silent OIDC login using stored tokens or session.
     */
    private suspend fun trySilentOidcLogin() {
        if (oidcService == null) return

        try {
            if (oidcService.isAuthenticated()) {
                // Already authenticated, navigate to main screen
                userPreferencesRepositoryImpl.setIsAuthenticated(true)
                userPreferencesRepositoryImpl.setOidcEnabled(true)
                sendEvent(LoginEvent.NavigateToPasscode)
            } else {
                // Try silent refresh
                val tokens = oidcService.silentLogin()
                if (tokens != null) {
                    userPreferencesRepositoryImpl.storeOidcTokens(tokens)
                    userPreferencesRepositoryImpl.setIsAuthenticated(true)
                    userPreferencesRepositoryImpl.setOidcEnabled(true)
                    sendEvent(LoginEvent.NavigateToPasscode)
                }
            }
        } catch (e: Exception) {
            // Silent login failed - user needs to login manually
            // This is expected if not previously authenticated
        }
    }

    private fun updateState(update: (LoginState) -> LoginState) {
        mutableStateFlow.update(update)
    }

    override fun handleAction(action: LoginAction) {
        when (action) {
            is LoginAction.UsernameChanged -> {
                updateState {
                    it.copy(
                        isError = false,
                        username = action.username,
                        userNameError = null,
                    )
                }
            }

            is LoginAction.PasswordChanged -> {
                updateState {
                    it.copy(
                        isError = false,
                        password = action.password,
                        passwordError = null,
                    )
                }
            }

            is LoginAction.TogglePasswordVisibility -> {
                updateState { it.copy(isPasswordVisible = !it.isPasswordVisible) }
            }

            is LoginAction.LoginClicked -> loginUser(state.username, state.password)

            is LoginAction.Internal.ReceiveLoginResult -> handleLoginResult(action)

            is LoginAction.SignupClicked -> sendEvent(LoginEvent.NavigateToSignup)

            is LoginAction.NavigateToForgotPassword -> sendEvent(LoginEvent.NavigateToForgotPassword)

            is LoginAction.ErrorDialogDismiss -> {
                updateState { it.copy(dialogState = null) }
            }

            is LoginAction.OidcLoginClicked -> loginWithOidc()

            is LoginAction.Internal.ReceiveOidcLoginResult -> handleOidcLoginResult(action)
        }
    }

    /**
     * Login using OIDC popup flow (Zitadel).
     */
    private fun loginWithOidc() {
        if (oidcService == null) {
            updateState {
                it.copy(
                    dialogState = LoginState.DialogState.Error("OIDC not configured"),
                )
            }
            return
        }

        loginJob?.cancel()
        updateState { it.copy(showOverlay = true, isOidcLoginInProgress = true) }

        loginJob = viewModelScope.launch {
            try {
                // Opens popup for OIDC authentication
                val tokens = oidcService.login()

                // Store tokens
                userPreferencesRepositoryImpl.storeOidcTokens(tokens)
                userPreferencesRepositoryImpl.setOidcEnabled(true)

                sendAction(
                    LoginAction.Internal.ReceiveOidcLoginResult(
                        DataState.Success(tokens.accessToken),
                    ),
                )
            } catch (e: OIDCException) {
                val errorMessage = when (e.errorCode) {
                    OIDCException.ERROR_USER_CANCELLED -> "Login cancelled"
                    OIDCException.ERROR_POPUP_BLOCKED -> "Please allow popups for this site"
                    OIDCException.ERROR_NETWORK -> "Network error. Please check your connection."
                    else -> e.message ?: "Login failed"
                }
                sendAction(
                    LoginAction.Internal.ReceiveOidcLoginResult(
                        DataState.Error(Exception(errorMessage)),
                    ),
                )
            } catch (e: Exception) {
                sendAction(
                    LoginAction.Internal.ReceiveOidcLoginResult(
                        DataState.Error(e),
                    ),
                )
            }
        }
    }

    /**
     * Handle OIDC login result.
     */
    private fun handleOidcLoginResult(action: LoginAction.Internal.ReceiveOidcLoginResult) {
        when (action.result) {
            is DataState.Error -> {
                updateState {
                    it.copy(
                        isError = true,
                        uiState = ScreenUiState.Success,
                        showOverlay = false,
                        isOidcLoginInProgress = false,
                        dialogState = LoginState.DialogState.Error(
                            action.result.exception.message ?: "OIDC login failed",
                        ),
                    )
                }
            }

            is DataState.Loading -> {
                updateState { it.copy(showOverlay = true, isOidcLoginInProgress = true) }
            }

            is DataState.Success -> {
                updateState {
                    it.copy(
                        showOverlay = false,
                        isOidcLoginInProgress = false,
                    )
                }

                viewModelScope.launch {
                    userPreferencesRepositoryImpl.setIsAuthenticated(true)
                }

                // Navigate to main screen (skip passcode for OIDC users)
                sendEvent(LoginEvent.NavigateToPasscode)
            }
        }
    }

    private fun handleLoginResult(action: LoginAction.Internal.ReceiveLoginResult) {
        when (action.loginResult) {
            is DataState.Error -> {
                updateState {
                    it.copy(
                        isError = true,
                        uiState = ScreenUiState.Success,
                        showOverlay = false,
                        dialogState = LoginState.DialogState.Error(action.loginResult.message),
                        userNameError = Res.string.feature_sign_in_username_error,
                        passwordError = Res.string.feature_sign_in_password_error,
                    )
                }
            }

            is DataState.Loading -> {
                updateState { it.copy(showOverlay = true) }
            }

            is DataState.Success -> {
                updateState { it.copy(showOverlay = false) }
                val user = action.loginResult.data
                val userData = UserData(
                    userId = user.userId,
                    userName = user.username.orEmpty(),
                    clientId = if (user.clients.isNotEmpty()) {
                        user.clients[0]
                    } else {
                        user.userId
                    },
                    isAuthenticated = user.isAuthenticated,
                    base64EncodedAuthenticationKey = user.base64EncodedAuthenticationKey.orEmpty(),
                    officeName = user.officeName.orEmpty(),
                    password = state.password,
                )
                viewModelScope.launch {
                    userPreferencesRepositoryImpl.updateUser(userData)
                    userPreferencesRepositoryImpl.setIsAuthenticated(true)
                }
                sendEvent(LoginEvent.NavigateToPasscode)
            }
        }
    }

    private fun loginUser(
        username: String,
        password: String,
    ) {
        loginJob?.cancel()

        updateState { it.copy(showOverlay = true) }

        loginJob = viewModelScope.launch {
            delay(300)

            val result = userAuthRepositoryImpl.login(username, password)
            sendAction(LoginAction.Internal.ReceiveLoginResult(result))
        }
    }
}

data class LoginState(
    val username: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val clientName: String = "",
    val isError: Boolean = false,
    val userNameError: StringResource? = null,
    val passwordError: StringResource? = null,
    val dialogState: DialogState? = null,
    val uiState: ScreenUiState?,
    val showOverlay: Boolean = false,
    // OIDC-specific state
    val isOidcAvailable: Boolean = false,
    val isOidcLoginInProgress: Boolean = false,
) {
    sealed interface DialogState {
        data class Error(val message: String) : DialogState
    }

    val isLoginButtonEnabled: Boolean
        get() = username.isNotEmpty() && password.length >= 8

    val isOidcLoginButtonEnabled: Boolean
        get() = isOidcAvailable && !isOidcLoginInProgress && !showOverlay
}

sealed interface LoginEvent {
    data object NavigateToSignup : LoginEvent
    data object NavigateToPasscode : LoginEvent
    data object NavigateToForgotPassword : LoginEvent
    data class ShowToast(val message: String) : LoginEvent
}

sealed interface LoginAction {
    data class UsernameChanged(val username: String) : LoginAction
    data class PasswordChanged(val password: String) : LoginAction
    data object TogglePasswordVisibility : LoginAction
    data object ErrorDialogDismiss : LoginAction
    data object LoginClicked : LoginAction
    data object SignupClicked : LoginAction
    data object NavigateToForgotPassword : LoginAction

    /** Initiate OIDC popup login with Zitadel */
    data object OidcLoginClicked : LoginAction

    sealed class Internal : LoginAction {
        data class ReceiveLoginResult(
            val loginResult: DataState<User>,
        ) : Internal()

        data class ReceiveOidcLoginResult(
            val result: DataState<String>, // Access token on success
        ) : Internal()
    }
}
