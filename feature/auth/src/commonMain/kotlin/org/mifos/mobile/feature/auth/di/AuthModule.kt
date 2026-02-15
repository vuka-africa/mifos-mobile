/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mobile-mobile/blob/master/LICENSE.md
 */
package org.mifos.mobile.feature.auth.di

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.mifos.mobile.core.datastore.OIDCService
import org.mifos.mobile.feature.auth.login.LoginViewModel
import org.mifos.mobile.feature.auth.otpAuthentication.OtpAuthenticationViewModel
import org.mifos.mobile.feature.auth.recoverPassword.RecoverPasswordViewModel
import org.mifos.mobile.feature.auth.registration.RegistrationViewModel
import org.mifos.mobile.feature.auth.setNewPassword.SetPasswordViewModel
import org.mifos.mobile.feature.auth.uploadId.UploadIdViewModel

val AuthModule = module {
    // LoginViewModel with nullable OIDCService - uses getOrNull for optional dependency
    viewModel { params ->
        LoginViewModel(
            userAuthRepositoryImpl = get(),
            userPreferencesRepositoryImpl = get(),
            clientRepository = get(),
            savedStateHandle = params.get(),
            oidcService = getOrNull<OIDCService>(),
        )
    }
    viewModelOf(::RegistrationViewModel)
    viewModelOf(::UploadIdViewModel)
    viewModelOf(::OtpAuthenticationViewModel)
    viewModelOf(::SetPasswordViewModel)
    viewModelOf(::RecoverPasswordViewModel)
}
