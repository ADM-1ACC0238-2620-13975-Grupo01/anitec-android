package com.anitec.platform.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.anitec.platform.R
import com.anitec.platform.core.session.SessionState
import com.anitec.platform.iam.interfaces.ui.SignInScreen
import com.anitec.platform.iam.interfaces.ui.SignUpScreen
import com.anitec.platform.iam.interfaces.ui.TermsScreen

/** The session state decides the graph: signing in or out flips it, with no manual navigation. */
@Composable
fun AniTecApp(viewModel: AppViewModel = hiltViewModel()) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val sessionExpired by viewModel.sessionExpired.collectAsStateWithLifecycle()

    when (val state = sessionState) {
        SessionState.Loading -> SplashScreen()
        SessionState.SignedOut -> AuthNavHost(sessionExpired = sessionExpired)
        is SessionState.SignedIn -> MainShell(session = state.session, onSignOut = viewModel::onSignOut)
    }
}
/**
 * Navigation host for the authentication flow.
 * Uses Type-Safe Navigation Compose and follows unidirectional data flow
 * by exposing lambdas instead of passing the NavController to the screens.
 */
@Composable
private fun AuthNavHost(sessionExpired: Boolean) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = SignInRoute) {
        composable<SignInRoute> {
            SignInScreen(
                sessionExpired = sessionExpired,
                onNavigateToSignUp = { navController.navigate(SignUpRoute) },
            )
        }
        composable<SignUpRoute> {
            SignUpScreen(
                onNavigateToSignIn = { navController.popBackStack() },
                onOpenTerms = { navController.navigate(TermsRoute) },
            )
        }
        composable<TermsRoute> {
            TermsScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.anitec_logo),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
        )
    }
}
