package com.anitec.platform.app

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.anitec.platform.R
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.core.session.UserSession
import com.anitec.platform.iam.interfaces.ui.TermsScreen

private data class BottomDestination(
    val route: Any,
    @StringRes val label: Int,
    val icon: ImageVector,
)

private fun bottomDestinations(role: UserRole): List<BottomDestination> = listOf(
    BottomDestination(HomeRoute, R.string.nav_home, Icons.Filled.Home),
    when (role) {
        UserRole.Rancher -> BottomDestination(AnimalsRoute, R.string.nav_animals, Icons.Filled.Pets)
        UserRole.Veterinarian -> BottomDestination(PatientsRoute, R.string.nav_patients, Icons.Filled.Badge)
    },
    BottomDestination(HealthRoute, R.string.nav_health, Icons.Filled.Favorite),
    BottomDestination(ActivitiesRoute, R.string.nav_activities, Icons.Filled.Event),
    BottomDestination(MoreRoute, R.string.nav_more, Icons.Filled.MoreHoriz),
)

/** Signed-in frame: top bar, role-specific bottom navigation and the nested graph. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(session: UserSession, onSignOut: () -> Unit) {
    val navController = rememberNavController()
    val destinations = remember(session.role) { bottomDestinations(session.role) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val current = destinations.firstOrNull { item -> currentDestination?.hierarchy?.any { it.hasRoute(item.route::class) } == true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Sub-screens (terms, placeholders) draw their own bar with a back button.
            if (current != null) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(R.drawable.anitec_logo),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(current.label), style = MaterialTheme.typography.titleLarge)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        },
        bottomBar = {
            if (current != null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    destinations.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.hasRoute(item.route::class) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTopLevel(item.route) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.label)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(padding),
        ) {
            composable<HomeRoute> { HomeScreen(session) }
            composable<AnimalsRoute> { ComingSoonScreen() }
            composable<PatientsRoute> { ComingSoonScreen() }
            composable<HealthRoute> { ComingSoonScreen() }
            composable<ActivitiesRoute> { ComingSoonScreen() }
            composable<MoreRoute> {
                MoreScreen(
                    session = session,
                    onOpen = { route -> navController.navigate(route) },
                    onSignOut = onSignOut,
                )
            }
            composable<TermsRoute> { TermsScreen(onBack = { navController.popBackStack() }) }
            composable<PlaceholderRoute> { entry ->
                PlaceholderScreen(
                    titleRes = entry.toRoute<PlaceholderRoute>().titleRes,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun NavHostController.navigateToTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
