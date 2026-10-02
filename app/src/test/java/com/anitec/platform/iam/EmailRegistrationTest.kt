package com.anitec.platform.iam

import com.anitec.platform.R
import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.EmailValidator
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.core.session.UserSession
import com.anitec.platform.iam.application.SignUpUseCase
import com.anitec.platform.iam.domain.AuthRepository
import com.anitec.platform.iam.interfaces.viewmodel.SignUpViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EmailValidatorTest {
    @Test
    fun `blank is accepted because the e-mail is optional`() {
        assertTrue(EmailValidator.isValidOrBlank(""))
        assertTrue(EmailValidator.isValidOrBlank("   "))
    }

    @Test
    fun `plausible addresses are accepted`() {
        assertTrue(EmailValidator.isValidOrBlank("ana@example.com"))
        assertTrue(EmailValidator.isValidOrBlank("  ana.lopez+farm@mail.example.pe  "))
    }

    @Test
    fun `malformed addresses are rejected`() {
        listOf("ana", "ana@", "@example.com", "ana@example", "ana @example.com", "ana@@example.com").forEach {
            assertFalse("should reject '$it'", EmailValidator.isValidOrBlank(it))
        }
    }

    @Test
    fun `an address longer than 254 characters is rejected`() {
        assertFalse(EmailValidator.isValidOrBlank("a".repeat(250) + "@example.com"))
    }

    @Test
    fun `normalize trims and turns blank into null`() {
        assertEquals("ana@example.com", EmailValidator.normalize("  ana@example.com "))
        assertNull(EmailValidator.normalize("   "))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class EmailRegistrationTest {

    private val session = UserSession(1, "demo", "Demo User", UserRole.Rancher, "token", "ana@example.com")

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun SignUpViewModel.fillValid(email: String) {
        onFullNameChange("Demo User"); onUsernameChange("demo"); onPasswordChange("secret"); onConfirmPasswordChange("secret")
        onTermsChange(true); onEmailChange(email)
    }

    @Test
    fun `a malformed e-mail blocks the registration`() {
        val useCase = mockk<SignUpUseCase>()
        val viewModel = SignUpViewModel(useCase)
        viewModel.fillValid("not-an-email")

        viewModel.submit()

        assertTrue(viewModel.state.value.emailInvalid)
        coVerify(exactly = 0) { useCase(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `leaving the e-mail blank is allowed`() {
        val useCase = mockk<SignUpUseCase>()
        coEvery { useCase(any(), any(), any(), any(), any()) } returns AppResult.Success(session)
        val viewModel = SignUpViewModel(useCase)
        viewModel.fillValid("")

        viewModel.submit()

        assertFalse(viewModel.state.value.emailInvalid)
        coVerify(exactly = 1) { useCase("Demo User", "demo", "secret", UserRole.Rancher, "") }
    }

    @Test
    fun `a valid e-mail is sent with the registration`() {
        val useCase = mockk<SignUpUseCase>()
        coEvery { useCase(any(), any(), any(), any(), any()) } returns AppResult.Success(session)
        val viewModel = SignUpViewModel(useCase)
        viewModel.fillValid("ana@example.com")

        viewModel.submit()

        coVerify(exactly = 1) { useCase("Demo User", "demo", "secret", UserRole.Rancher, "ana@example.com") }
    }

    @Test
    fun `a conflict naming the e-mail is not reported as a taken username`() {
        val useCase = mockk<SignUpUseCase>()
        coEvery { useCase(any(), any(), any(), any(), any()) } returns AppResult.Failure(AppError.Conflict(listOf("EmailAlreadyTaken")))
        val viewModel = SignUpViewModel(useCase)
        viewModel.fillValid("ana@example.com")

        viewModel.submit()

        assertEquals(R.string.auth_email_taken, viewModel.state.value.errorRes)
    }

    @Test
    fun `a conflict naming the username is still reported as a taken username`() {
        val useCase = mockk<SignUpUseCase>()
        coEvery { useCase(any(), any(), any(), any(), any()) } returns AppResult.Failure(AppError.Conflict(listOf("UsernameAlreadyTaken")))
        val viewModel = SignUpViewModel(useCase)
        viewModel.fillValid("ana@example.com")

        viewModel.submit()

        assertEquals(R.string.auth_username_taken, viewModel.state.value.errorRes)
    }

    @Test
    fun `the use case trims the address before it reaches the repository`() = runTest {
        val repository = mockk<AuthRepository>()
        coEvery { repository.signUp(any(), any(), any(), any(), any()) } returns AppResult.Success(session)

        SignUpUseCase(repository)("Demo", "demo", "secret", UserRole.Rancher, "  ana@example.com ")
        SignUpUseCase(repository)("Demo", "demo", "secret", UserRole.Rancher, "   ")

        coVerify { repository.signUp("Demo", "demo", "secret", UserRole.Rancher, "ana@example.com") }
        coVerify { repository.signUp("Demo", "demo", "secret", UserRole.Rancher, null) }
    }
}
