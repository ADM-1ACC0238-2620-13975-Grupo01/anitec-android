package com.anitec.platform.ui

// Test coverage marker: UI scenarios of the critical flows (TS-014).

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.anitec.platform.MainActivity
import com.anitec.platform.R
import com.anitec.platform.core.session.SessionStore
import com.anitec.platform.integration.FakeBackend
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * The real screens, navigation, view models, repositories and cache, over a fake server: what a person does with the app.
 * Texts are read from the resources, so the tests do not depend on the language of the device.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CriticalFlowsUiTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject lateinit var sessionStore: SessionStore

    private val backend = FakeBackend()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun text(@StringRes id: Int, vararg args: Any) = context.getString(id, *args)

    @Before
    fun setUp() {
        // Debug builds ask for the local network permission when they open; a system dialog would cover the app.
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, "android.permission.ACCESS_LOCAL_NETWORK")
        hilt.inject()
        UiTestServer.server.dispatcher = backend
        runBlocking { sessionStore.clear() }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
        runBlocking { sessionStore.clear() }
    }

    private fun waitFor(label: String, timeoutMillis: Long = 15_000) {
        compose.waitUntil(timeoutMillis) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun signIn() {
        waitFor(text(R.string.auth_sign_in_title))
        compose.onNodeWithText(text(R.string.auth_username)).performTextInput("rancher")
        compose.onNodeWithText(text(R.string.auth_password)).performTextInput("secret")
        compose.onNodeWithText(text(R.string.auth_sign_in_action)).performScrollTo().performClick()
        waitFor(text(R.string.home_greeting, "Jose Quispe"))
    }

    private fun openFromMore(@StringRes entry: Int) {
        compose.onNodeWithText(text(R.string.nav_more)).performClick()
        waitFor(text(entry))
        compose.onNodeWithText(text(entry)).performScrollTo().performClick()
    }

    @Test
    fun aRancherSignsInAndLandsOnTheirHome() {
        signIn()

        compose.onNodeWithText(text(R.string.home_greeting, "Jose Quispe")).assertIsDisplayed()
        assertEquals("the sign-in reached the server", 1, backend.received.count { it.url.encodedPath.endsWith("authentication/sign-in") })
    }

    @Test
    fun aRancherAcceptsTheRequestOfAVeterinarian_andSeesItMoveToTheOnesWithAccess() {
        signIn()

        openFromMore(R.string.nav_veterinarians)
        waitFor("Vet 1")
        compose.onNodeWithText("Vet 1").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.consent_accept)).performClick()

        waitFor(text(R.string.consent_accepted_message))
        assertEquals("Accepted", backend.links[1])
        // It is no longer waiting for an answer: the buttons to answer are gone.
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.consent_accept)).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun aRancherRevokesTheAccessOfAVeterinarian_afterConfirming() {
        signIn()

        openFromMore(R.string.nav_veterinarians)
        waitFor("Vet 2")
        compose.onNodeWithText(text(R.string.consent_revoke)).performClick()
        // The dialog asks first: nothing has changed yet.
        waitFor(text(R.string.consent_revoke_confirm, "Vet 2"))
        assertEquals("Accepted", backend.links[2])
        compose.onAllNodesWithText(text(R.string.consent_revoke)).fetchSemanticsNodes().let { nodes ->
            // The title and the confirm button of the dialog share the label; the last one is the button.
            compose.onAllNodesWithText(text(R.string.consent_revoke))[nodes.size - 1].performClick()
        }

        waitFor(text(R.string.consent_revoked_message))
        assertEquals("Revoked", backend.links[2])
    }

    @Test
    fun anEditOfARecordChangedMeanwhile_showsTheConflict_andKeepingMineSavesIt() {
        backend.seed(buildJsonObject {
            put("id", JsonPrimitive(7)); put("ownerId", JsonPrimitive(10)); put("type", JsonPrimitive("Ingreso"))
            put("category", JsonPrimitive("Venta de ganado")); put("amount", JsonPrimitive(3900.0)); put("date", JsonPrimitive("2026-10-15"))
            put("description", JsonPrimitive("Venta de bovinos")); put("version", JsonPrimitive(1))
        })
        signIn()

        openFromMore(R.string.nav_finance)
        waitFor(text(R.string.common_edit))
        compose.onNodeWithText(text(R.string.common_edit)).performClick()
        waitFor("3900")
        // Another device changes the same record while this form is open.
        backend.changeElsewhere(7, "description", "From the other device")
        compose.onNodeWithText("3900").performTextReplacement("3950")
        compose.onNodeWithText(text(R.string.common_save)).performScrollTo().performClick()

        waitFor(text(R.string.conflict_title))
        compose.onNodeWithText(text(R.string.conflict_message)).assertIsDisplayed()
        assertEquals("nothing was overwritten yet", "From the other device", backend.record(7)!!.getValue("description").jsonPrimitive.content)

        compose.onNodeWithText(text(R.string.conflict_keep_mine)).performClick()

        compose.waitUntil(10_000) { backend.record(7)!!.getValue("version").jsonPrimitive.content == "3" }
        assertEquals("3950.0", backend.record(7)!!.getValue("amount").jsonPrimitive.content)
    }
}
