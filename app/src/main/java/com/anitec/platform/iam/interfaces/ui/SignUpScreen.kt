package com.anitec.platform.iam.interfaces.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anitec.platform.R
import com.anitec.platform.core.designsystem.component.AniTecTextField
import com.anitec.platform.core.designsystem.component.ErrorMessage
import com.anitec.platform.core.designsystem.component.PasswordField
import com.anitec.platform.core.designsystem.component.PrimaryButton
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.iam.interfaces.viewmodel.SignUpViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    onNavigateToSignIn: () -> Unit,
    onOpenTerms: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SignUpViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val required = stringResource(R.string.error_field_required)
    val roles = UserRole.entries

    AuthScaffold(
        title = stringResource(R.string.auth_sign_up_title),
        subtitle = stringResource(R.string.auth_sign_up_subtitle),
        modifier = modifier,
    ) {
        Text(stringResource(R.string.auth_role), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            roles.forEachIndexed { index, role ->
                SegmentedButton(
                    selected = state.role == role,
                    onClick = { viewModel.onRoleChange(role) },
                    shape = SegmentedButtonDefaults.itemShape(index, roles.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        activeBorderColor = MaterialTheme.colorScheme.primary,
                    ),
                ) { Text(stringResource(role.labelRes())) }
            }
        }
        AniTecTextField(
            value = state.fullName,
            onValueChange = viewModel::onFullNameChange,
            label = stringResource(R.string.auth_full_name),
            modifier = Modifier.fillMaxWidth(),
            error = if (state.fullNameMissing) required else null,
        )
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
        )
        PasswordField(
            value = state.confirmPassword,
            onValueChange = viewModel::onConfirmPasswordChange,
            label = stringResource(R.string.auth_confirm_password),
            showLabel = stringResource(R.string.common_show_password),
            hideLabel = stringResource(R.string.common_hide_password),
            modifier = Modifier.fillMaxWidth(),
            error = if (state.passwordsMismatch) stringResource(R.string.auth_passwords_mismatch) else null,
            imeAction = ImeAction.Done,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = state.acceptedTerms, onCheckedChange = viewModel::onTermsChange)
            Text(stringResource(R.string.auth_accept_terms), style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onOpenTerms) {
            Text(stringResource(R.string.auth_read_terms), color = MaterialTheme.colorScheme.primary)
        }
        if (state.termsMissing) ErrorMessage(stringResource(R.string.auth_terms_required))
        state.errorRes?.let { ErrorMessage(stringResource(it)) }

        PrimaryButton(
            text = stringResource(R.string.auth_sign_up_action),
            onClick = viewModel::submit,
            modifier = Modifier.fillMaxWidth(),
            loading = state.loading,
            icon = Icons.Filled.PersonAdd,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = onNavigateToSignIn) {
                Text(stringResource(R.string.auth_have_account), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
