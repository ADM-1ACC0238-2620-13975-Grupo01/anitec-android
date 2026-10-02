package com.anitec.platform.iam.interfaces.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anitec.platform.R
import com.anitec.platform.core.designsystem.component.AniTecTextField
import com.anitec.platform.core.designsystem.component.ErrorMessage
import com.anitec.platform.core.designsystem.component.PasswordField
import com.anitec.platform.core.designsystem.component.PrimaryButton
import com.anitec.platform.iam.interfaces.viewmodel.SignInViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignInScreen(
    sessionExpired: Boolean,
    onNavigateToSignUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val required = stringResource(R.string.error_field_required)

    AuthScaffold(
        title = stringResource(R.string.auth_sign_in_title),
        subtitle = stringResource(R.string.auth_sign_in_subtitle),
        modifier = modifier,
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RoleBadge(stringResource(R.string.auth_role_rancher_badge))
            RoleBadge(stringResource(R.string.auth_role_veterinarian_badge))
        }
        if (sessionExpired) ErrorMessage(stringResource(R.string.error_session_expired))

        AniTecTextField(
            value = state.username,
            onValueChange = viewModel::onUsernameChange,
            label = stringResource(R.string.auth_username),
            modifier = Modifier.fillMaxWidth(),
            error = if (state.usernameMissing) required else null,
        )
        PasswordField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(R.string.auth_password),
            showLabel = stringResource(R.string.common_show_password),
            hideLabel = stringResource(R.string.common_hide_password),
            modifier = Modifier.fillMaxWidth(),
            error = if (state.passwordMissing) required else null,
            imeAction = ImeAction.Done,
            onImeAction = viewModel::submit,
        )
        state.errorRes?.let { ErrorMessage(stringResource(it)) }
        PrimaryButton(
            text = stringResource(R.string.auth_sign_in_action),
            onClick = viewModel::submit,
            modifier = Modifier.fillMaxWidth(),
            loading = state.loading,
            icon = Icons.AutoMirrored.Filled.Login,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = onNavigateToSignUp) {
                Text(stringResource(R.string.auth_no_account), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
