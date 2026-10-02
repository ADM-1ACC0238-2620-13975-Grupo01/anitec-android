package com.anitec.platform.app

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anitec.platform.sync.SyncBanner
import com.anitec.platform.sync.SyncStatusViewModel
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
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.QrCodeScanner
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
import com.anitec.platform.activities.interfaces.ui.ActivityFormScreen
import com.anitec.platform.activities.interfaces.ui.ActivityListScreen
import com.anitec.platform.analytics.interfaces.ui.AnalyticsScreen
import com.anitec.platform.scanner.interfaces.ui.ScannerScreen
import com.anitec.platform.devices.interfaces.ui.DeviceFormScreen
import com.anitec.platform.devices.interfaces.ui.DeviceListScreen
import com.anitec.platform.financial.interfaces.ui.FinancialFormScreen
import com.anitec.platform.financial.interfaces.ui.FinancialListScreen
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.core.session.UserSession
import com.anitec.platform.iam.interfaces.ui.TermsScreen
import com.anitec.platform.livestock.interfaces.ui.AnimalFormScreen
import com.anitec.platform.livestock.interfaces.ui.AnimalListScreen
import com.anitec.platform.livestock.interfaces.ui.CorralFormScreen
import com.anitec.platform.livestock.interfaces.ui.CorralListScreen
import com.anitec.platform.livestock.interfaces.ui.HerdFormScreen
import com.anitec.platform.livestock.interfaces.ui.HerdListScreen
import com.anitec.platform.veterinary.interfaces.ui.AddClientScreen
import com.anitec.platform.veterinary.interfaces.ui.ClientListScreen
import com.anitec.platform.veterinary.interfaces.ui.ClinicalHistoryScreen
import com.anitec.platform.veterinary.interfaces.ui.PatientsScreen
import com.anitec.platform.sanitary.interfaces.ui.HealthFormScreen
import com.anitec.platform.sanitary.interfaces.ui.HealthListScreen

private data class BottomDestination(
    val route: Any,
    @StringRes val label: Int,
    val icon: ImageVector,
)

private fun bottomDestinations(role: UserRole): List<BottomDestination> = listOf(
    BottomDestination(HomeRoute, R.string.nav_home, Icons.Filled.Home),
    when (role) {
        UserRole.Rancher -> BottomDestination(AnimalsRoute(), R.string.nav_animals, Icons.Filled.Pets)
        UserRole.Veterinarian -> BottomDestination(PatientsRoute(), R.string.nav_patients, Icons.Filled.Badge)
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
    val syncViewModel: SyncStatusViewModel = hiltViewModel()
    val syncStatus by syncViewModel.status.collectAsStateWithLifecycle()
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
                    actions = {
                        IconButton(onClick = { navController.navigate(ScannerRoute) }) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = stringResource(R.string.scanner_action))
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
        Column(modifier = Modifier.padding(padding)) {
        SyncBanner(status = syncStatus, onRetry = syncViewModel::retry, onDiscard = syncViewModel::discardFailed)
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.weight(1f),
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    session = session,
                    onRegisterAnimal = { navController.navigate(AnimalFormRoute()) },
                    onRecordHealth = { navController.navigate(HealthFormRoute()) },
                    onViewAllHealth = { navController.navigateToTopLevel(HealthRoute) },
                    onViewAllActivities = { navController.navigateToTopLevel(ActivitiesRoute) },
                    onAddClient = { navController.navigate(AddClientRoute) },
                    onViewPatients = { clientId -> navController.navigate(PatientsRoute(clientId)) },
                    onOpenClients = { navController.navigate(ClientsRoute) },
                )
            }
            composable<AnimalsRoute> {
                AnimalListScreen(
                    onNewAnimal = { navController.navigate(AnimalFormRoute()) },
                    onEditAnimal = { id -> navController.navigate(AnimalFormRoute(id)) },
                )
            }
            composable<PatientsRoute> {
                PatientsScreen(onOpenHistory = { animalId -> navController.navigate(ClinicalHistoryRoute(animalId)) })
            }
            composable<ClinicalHistoryRoute> {
                ClinicalHistoryScreen(
                    onBack = { navController.popBackStack() },
                    onNewRecord = { animalId -> navController.navigate(HealthFormRoute(animalId = animalId)) },
                )
            }
            composable<ClientsRoute> {
                ClientListScreen(
                    onBack = { navController.popBackStack() },
                    onAddClient = { navController.navigate(AddClientRoute) },
                    onViewPatients = { clientId -> navController.navigate(PatientsRoute(clientId)) },
                )
            }
            composable<AddClientRoute> { AddClientScreen(onBack = { navController.popBackStack() }) }
            composable<HealthRoute> {
                HealthListScreen(
                    onNew = { navController.navigate(HealthFormRoute()) },
                    onEdit = { id -> navController.navigate(HealthFormRoute(eventId = id)) },
                )
            }
            composable<HealthFormRoute> { HealthFormScreen(onBack = { navController.popBackStack() }) }
            composable<ActivitiesRoute> {
                ActivityListScreen(
                    onNew = { navController.navigate(ActivityFormRoute()) },
                    onEdit = { id -> navController.navigate(ActivityFormRoute(id)) },
                )
            }
            composable<ActivityFormRoute> { ActivityFormScreen(onBack = { navController.popBackStack() }) }
            composable<FinancialRoute> {
                FinancialListScreen(
                    onBack = { navController.popBackStack() },
                    onNew = { navController.navigate(FinancialFormRoute()) },
                    onEdit = { id -> navController.navigate(FinancialFormRoute(id)) },
                )
            }
            composable<FinancialFormRoute> { FinancialFormScreen(onBack = { navController.popBackStack() }) }
            composable<ScannerRoute> {
                ScannerScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAnimal = { id ->
                        // A rancher sees the animal's record; a veterinarian its clinical history.
                        navController.popBackStack()
                        if (session.role == UserRole.Veterinarian) {
                            navController.navigate(ClinicalHistoryRoute(id))
                        } else {
                            navController.navigate(AnimalsRoute(openAnimalId = id)) { launchSingleTop = true }
                        }
                    },
                )
            }
            composable<AnalyticsRoute> { AnalyticsScreen(onBack = { navController.popBackStack() }) }
            composable<DevicesRoute> {
                DeviceListScreen(
                    onBack = { navController.popBackStack() },
                    onNew = { navController.navigate(DeviceFormRoute()) },
                    onEdit = { id -> navController.navigate(DeviceFormRoute(id)) },
                )
            }
            composable<DeviceFormRoute> { DeviceFormScreen(onBack = { navController.popBackStack() }) }
            composable<MoreRoute> {
                MoreScreen(
                    session = session,
                    onOpen = { route -> navController.navigate(route) },
                    onSignOut = onSignOut,
                    unsyncedCount = syncStatus.total,
                )
            }
            composable<TermsRoute> { TermsScreen(onBack = { navController.popBackStack() }) }
            composable<AnimalFormRoute> {
                AnimalFormScreen(
                    onBack = { navController.popBackStack() },
                    onCreateCorral = { navController.navigate(CorralFormRoute()) },
                )
            }
            composable<HerdsRoute> {
                HerdListScreen(
                    onBack = { navController.popBackStack() },
                    onNew = { navController.navigate(HerdFormRoute()) },
                    onEdit = { id -> navController.navigate(HerdFormRoute(id)) },
                )
            }
            composable<HerdFormRoute> { HerdFormScreen(onBack = { navController.popBackStack() }) }
            composable<CorralsRoute> {
                CorralListScreen(
                    onBack = { navController.popBackStack() },
                    onNew = { navController.navigate(CorralFormRoute()) },
                    onEdit = { id -> navController.navigate(CorralFormRoute(id)) },
                )
            }
            composable<CorralFormRoute> { CorralFormScreen(onBack = { navController.popBackStack() }) }
            composable<PlaceholderRoute> { entry ->
                PlaceholderScreen(
                    titleRes = entry.toRoute<PlaceholderRoute>().titleRes,
                    onBack = { navController.popBackStack() },
                )
            }
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
