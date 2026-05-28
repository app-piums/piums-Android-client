package com.piums.cliente.ui.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.piums.cliente.data.local.TokenStorage
import com.piums.cliente.data.repository.AuthRepository
import com.piums.cliente.ui.screens.auth.AuthScreen
import com.piums.cliente.ui.screens.biometric.BiometricGateScreen
import com.piums.cliente.ui.screens.home.HomeScreen
import com.piums.cliente.ui.screens.inbox.InboxScreen
import com.piums.cliente.ui.screens.myspace.MySpaceScreen
import com.piums.cliente.ui.screens.onboarding.OnboardingScreen
import com.piums.cliente.ui.screens.profile.ProfileScreen
import com.piums.cliente.ui.screens.tutorial.TutorialScreen
import com.piums.cliente.ui.screens.search.SearchScreen
import com.piums.cliente.ui.screens.splash.SplashScreen
import com.piums.cliente.ui.theme.PiumsError
import com.piums.cliente.utils.ConnectivityObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed class Screen(val route: String) {
    object Splash    : Screen("splash")
    object Biometric : Screen("biometric_gate")
    object Auth      : Screen("auth")
    object Onboarding: Screen("onboarding")
    object Tutorial  : Screen("new_user_tutorial")
    object Main      : Screen("main")
}

@HiltViewModel
class NavViewModel @Inject constructor(
    val authRepository: AuthRepository,
    val tokenStorage: TokenStorage,
    connectivityObserver: ConnectivityObserver
) : ViewModel() {
    val isOnline: StateFlow<Boolean> = connectivityObserver.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val navVm: NavViewModel = hiltViewModel()
    val isOnline by navVm.isOnline.collectAsState(initial = true)

    val startDest = if (!navVm.tokenStorage.onboardingDone) Screen.Onboarding.route else Screen.Splash.route

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = startDest) {

            composable(Screen.Splash.route) {
                SplashScreen(
                    onLoggedIn = {
                        navController.navigate(Screen.Main.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    },
                    onBiometricGate = {
                        navController.navigate(Screen.Biometric.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    },
                    onNotLoggedIn = {
                        navController.navigate(Screen.Auth.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Biometric.route) {
                BiometricGateScreen(
                    onSuccess = {
                        navController.navigate(Screen.Main.route) {
                            popUpTo(Screen.Biometric.route) { inclusive = true }
                        }
                    },
                    onFallback = {
                        navController.navigate(Screen.Auth.route) {
                            popUpTo(Screen.Biometric.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Auth.route) {
                AuthScreen(
                    onSuccess = {
                        navController.navigate(Screen.Main.route) {
                            popUpTo(Screen.Auth.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onDone = {
                        navController.navigate(Screen.Tutorial.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Tutorial.route) {
                TutorialScreen(
                    onDone = {
                        navController.navigate(Screen.Splash.route) {
                            popUpTo(Screen.Tutorial.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Main.route) {
                MainScaffold(
                    onLogout     = {
                        navController.navigate(Screen.Auth.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    tokenStorage = navVm.tokenStorage
                )
            }
        }

        // Offline banner
        AnimatedVisibility(
            visible = !isOnline,
            enter   = slideInVertically { -it },
            exit    = slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PiumsError.copy(alpha = 0.92f))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.WifiOff, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text("Sin conexión a internet", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
