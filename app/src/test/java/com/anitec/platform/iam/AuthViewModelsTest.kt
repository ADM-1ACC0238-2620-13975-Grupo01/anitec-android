package com.anitec.platform.iam

import com.anitec.platform.R
import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.core.session.UserSession
import com.anitec.platform.iam.application.SignInUseCase
import com.anitec.platform.iam.application.SignUpUseCase
import com.anitec.platform.iam.interfaces.viewmodel.SignInViewModel
import com.anitec.platform.iam.interfaces.viewmodel.SignUpViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelsTest {

    private val session = UserSession(1, "demo", "Demo User", UserRole.Rancher, "token")

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    // --- sign in ---

    @Test
    fun `sign in with blank fields shows field errors and calls nothing`() {
        val useCase = mockk<SignInUseCase>()
        val viewModel = SignInViewModel(useCase)

        viewModel.submit()

        assertTrue(viewModel.state.value.usernameMissing)
        assertTrue(viewModel.state.value.passwordMissing)
        coVerify(exactly = 0) { useCase(any(), any()) }
    }

    @Test
    fun `sign in 400 is reported as invalid credentials`() {
        val useCase = mockk<SignInUseCase>()
        coEvery { useCase(any(), any()) } returns AppResult.Failure(AppError.Validation(listOf("InvalidCredentials")))
        val viewModel = SignInViewModel(useCase)
        viewModel.onUsernameChange("demo")
        viewModel.onPasswordChange("wrong")

        viewModel.submit()

        assertEquals(R.string.auth_invalid_credentials, viewModel.state.value.errorRes)
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun `sign in network failure is reported as a connection error`() {
        val useCase = mockk<SignInUseCase>()
        coEvery { useCase(any(), any()) } returns AppResult.Failure(AppError.Network)
        val viewModel = SignInViewModel(useCase)
        viewModel.onUsernameChange("demo")
        viewModel.onPasswordChange("secret")

        viewModel.submit()

        assertEquals(R.string.error_network, viewModel.state.value.errorRes)
    }

    @Test
    fun `sign in success clears errors and stops loading`() {
        val useCase = mockk<SignInUseCase>()
        coEvery { useCase(any(), any()) } returns AppResult.Success(session)
        val viewModel = SignInViewModel(useCase)
        viewModel.onUsernameChange("demo")
        viewModel.onPasswordChange("secret")

        viewModel.submit()

        assertNull(viewModel.state.value.errorRes)
        assertFalse(viewModel.state.value.loading)
        coVerify(exactly = 1) { useCase("demo", "secret") }
    }

    // --- sign up ---

    private fun SignUpViewModel.fillValid() {
        onFullNameChange("Demo User")
        onUsernameChange("demo")
        onPasswordChange("secret")
        onConfirmPasswordChange("secret")
        onTermsChange(true)
    }

    @Test
    fun `sign up with mismatched passwords is blocked`() {
        val useCase = mockk<SignUpUseCase>()
        val viewModel = SignUpViewModel(useCase)
        viewModel.fillValid()
        viewModel.onConfirmPasswordChange("different")

        viewModel.submit()

        assertTrue(viewModel.state.value.passwordsMismatch)
        coVerify(exactly = 0) { useCase(any(), any(), any(), any()) }
    }

    @Test
    fun `sign up requires accepting the terms`() {
        val useCase = mockk<SignUpUseCase>()
        val viewModel = SignUpViewModel(useCase)
        viewModel.fillValid()
        viewModel.onTermsChange(false)

        viewModel.submit()

        assertTrue(viewModel.state.value.termsMissing)
        coVerify(exactly = 0) { useCase(any(), any(), any(), any()) }
    }

    @Test
    fun `sign up sends the selected role`() {
        val useCase = mockk<SignUpUseCase>()
        coEvery { useCase(any(), any(), any(), any()) } returns AppResult.Success(session)
        val viewModel = SignUpViewModel(useCase)
        viewModel.fillValid()
        viewModel.onRoleChange(UserRole.Veterinarian)

        viewModel.submit()

        coVerify(exactly = 1) { useCase("Demo User", "demo", "secret", UserRole.Veterinarian) }
    }

    @Test
    fun `sign up with a taken username is reported`() {
        val useCase = mockk<SignUpUseCase>()
        coEvery { useCase(any(), any(), any(), any()) } returns AppResult.Failure(AppError.Conflict())
        val viewModel = SignUpViewModel(useCase)
        viewModel.fillValid()

        viewModel.submit()

        assertEquals(R.string.auth_username_taken, viewModel.state.value.errorRes)
    }
}
