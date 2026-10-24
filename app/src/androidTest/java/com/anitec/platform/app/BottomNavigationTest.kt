package com.anitec.platform.app

import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.reflect.KClass

/**
 * The bottom bar uses [navigateToTopLevel] with the real routes. Home is the start destination, and screens opened
 * from it (like a client's patients) sit above it in the same stack, so going back to Home needs care.
 */
class BottomNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        composeRule.setContent {
            navController = TestNavHostController(LocalContext.current).apply { navigatorProvider.addNavigator(ComposeNavigator()) }
            NavHost(navController, startDestination = HomeRoute) {
                composable<HomeRoute> { Text("home") }
                composable<PatientsRoute> { Text("patients") }
                composable<HealthRoute> { Text("health") }
                composable<ActivitiesRoute> { Text("activities") }
                composable<MoreRoute> { Text("more") }
            }
        }
    }

    private fun act(block: NavHostController.() -> Unit) = composeRule.runOnIdle { navController.block() }

    private fun isAt(route: KClass<*>): Boolean = composeRule.runOnIdle {
        navController.currentBackStackEntry?.destination?.hasRoute(route) == true
    }

    @Test
    fun home_goes_back_to_home_after_opening_the_patients_of_a_client() {
        act { navigate(PatientsRoute(clientId = 5)) }
        assertTrue(isAt(PatientsRoute::class))

        act { navigateToTopLevel(HomeRoute) }

        assertTrue("Home should be shown, not the client's patients", isAt(HomeRoute::class))
    }

    @Test
    fun home_from_another_tab_does_not_bring_back_what_was_opened_from_it() {
        act { navigate(PatientsRoute(clientId = 5)) }
        act { navigateToTopLevel(HealthRoute) }
        assertTrue(isAt(HealthRoute::class))

        act { navigateToTopLevel(HomeRoute) }

        assertTrue("Home should be shown, not the client's patients", isAt(HomeRoute::class))
    }

    @Test
    fun home_can_be_reached_again_and_again() {
        repeat(3) {
            act { navigate(PatientsRoute(clientId = 5)) }
            act { navigateToTopLevel(HomeRoute) }
            assertTrue(isAt(HomeRoute::class))
        }
    }

    @Test
    fun the_tabs_switch_in_any_order() {
        act { navigateToTopLevel(HealthRoute) }
        assertTrue(isAt(HealthRoute::class))
        act { navigateToTopLevel(PatientsRoute()) }
        assertTrue(isAt(PatientsRoute::class))
        act { navigateToTopLevel(MoreRoute) }
        assertTrue(isAt(MoreRoute::class))
        act { navigateToTopLevel(HealthRoute) }
        assertTrue(isAt(HealthRoute::class))
        act { navigateToTopLevel(HomeRoute) }
        assertTrue(isAt(HomeRoute::class))
    }
}
