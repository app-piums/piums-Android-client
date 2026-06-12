package com.piums.cliente.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.time.LocalDate
import com.piums.cliente.ui.screens.artist.ArtistProfileScreen
import com.piums.cliente.ui.screens.booking.BookingDetailScreen
import com.piums.cliente.ui.screens.booking.BookingScreen
import com.piums.cliente.ui.screens.home.HomeScreen
import com.piums.cliente.ui.screens.inbox.DisputeDetailScreen
import com.piums.cliente.ui.screens.inbox.InboxScreen
import com.piums.cliente.ui.screens.myspace.MySpaceScreen
import com.piums.cliente.ui.screens.notifications.NotificationsScreen
import com.piums.cliente.ui.screens.payments.PaymentsScreen
import com.piums.cliente.ui.screens.payments.WalletScreen
import com.piums.cliente.ui.screens.profile.ProfileScreen
import com.piums.cliente.ui.screens.search.SearchScreen
import com.piums.cliente.ui.screens.coupons.CouponsScreen
import com.piums.cliente.ui.screens.booking.ArtistSearchByDateScreen
import com.piums.cliente.ui.screens.notifications.NotificationPreferencesScreen
import com.piums.cliente.ui.screens.profile.IdentityVerificationScreen
import com.piums.cliente.ui.screens.tutorial.HowItWorksScreen
import com.piums.cliente.ui.screens.tutorial.TutorialScreen
import com.piums.cliente.ui.screens.reviews.ReviewScreen
import com.piums.cliente.ui.screens.disputes.CreateDisputeScreen
import com.piums.cliente.ui.screens.disputes.MyDisputesScreen
import com.piums.cliente.ui.screens.payments.PaymentCheckoutScreen
import com.piums.cliente.data.local.TokenStorage
import com.piums.cliente.ui.components.TourOverlay
import com.piums.cliente.ui.theme.PiumsOrange
import com.piums.cliente.utils.DeepLinkTarget
import com.piums.cliente.utils.TourManager

private enum class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    HOME(    "home",    "Inicio",     Icons.Default.Home,          Icons.Default.Home),
    SEARCH(  "search",  "Explorar",   Icons.Default.Search,        Icons.Default.Search),
    MYSPACE( "myspace", "Mi Espacio", Icons.Default.GridView,      Icons.Default.GridView),
    INBOX(   "inbox",   "Mensajes",   Icons.Default.Message,       Icons.Default.Message),
    PROFILE( "profile", "Perfil",     Icons.Default.PersonOutline, Icons.Default.Person),
}

private val TAB_ROUTES = Tab.entries.map { it.route }.toSet()

@Composable
fun MainScaffold(
    onLogout: () -> Unit,
    tokenStorage: TokenStorage? = null
) {
    val mainVm: MainViewModel = hiltViewModel()
    val innerNav = rememberNavController()
    val backStack by innerNav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val isTabRoute = currentRoute in TAB_ROUTES
    var isChatOpen by remember { mutableStateOf(false) }
    LaunchedEffect(currentRoute) {
        if (currentRoute != Tab.INBOX.route) isChatOpen = false
    }

    val tourStep by mainVm.tourManager.currentStep.collectAsState()
    val context  = LocalContext.current

    // Pedir notificaciones → luego ubicación (solo si no están ya concedidos)
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            locationLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    LaunchedEffect(Unit) {
        val locationGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            when {
                !notifGranted  -> notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                !locationGranted -> locationLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        } else if (!locationGranted) {
            locationLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    // Switch tab when tour step changes
    LaunchedEffect(tourStep) {
        if (!mainVm.tourManager.isActive.value) return@LaunchedEffect
        val targetTab = mainVm.tourManager.currentStepData?.tab ?: return@LaunchedEffect
        val route = Tab.entries.getOrNull(targetTab)?.route ?: return@LaunchedEffect
        if (currentRoute != route) {
            innerNav.navigate(route) {
                popUpTo(innerNav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true; restoreState = true
            }
        }
    }

    // Auto-logout when token refresh fails at runtime
    LaunchedEffect(Unit) {
        mainVm.authEventBus.logoutRequired.collect { onLogout() }
    }

    // Handle deep link navigation from FCM notifications
    LaunchedEffect(Unit) {
        mainVm.deepLinkManager.pending.collect { target ->
            when (target) {
                is DeepLinkTarget.Artist -> innerNav.navigate("artist/${target.id}")
                is DeepLinkTarget.Booking -> innerNav.navigate("booking-detail/${target.id}")
                is DeepLinkTarget.Inbox -> innerNav.navigate(Tab.INBOX.route) {
                    popUpTo(innerNav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true; restoreState = true
                }
                is DeepLinkTarget.Coupons -> innerNav.navigate("coupons")
                is DeepLinkTarget.Notifications -> innerNav.navigate("notifications")
            }
        }
    }

    // Navegar al tab de Mensajes cuando llega una conversación por push (equivalente a .onReceive(.navigateToConversation))
    LaunchedEffect(Unit) {
        mainVm.socketManager.pendingDeepLinkConversationId.collect { id ->
            if (id != null) {
                innerNav.navigate(Tab.INBOX.route) {
                    popUpTo(innerNav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true; restoreState = true
                }
            }
        }
    }

    // Ciclo de vida del socket (equivalente a .onChange(of: scenePhase) en iOS)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mainVm.setSocketActive(true)
                Lifecycle.Event.ON_PAUSE  -> mainVm.setSocketActive(false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = if (isChatOpen) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
            bottomBar = {
                AnimatedVisibility(
                    visible = isTabRoute && !isChatOpen,
                    enter = slideInVertically { it },
                    exit  = slideOutVertically { it }
                ) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        Tab.entries.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected  = selected,
                                onClick   = {
                                    innerNav.navigate(tab.route) {
                                        popUpTo(innerNav.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState    = true
                                    }
                                },
                                icon = {
                                    BadgedBox(badge = {
                                        if (tab == Tab.INBOX && mainVm.unreadCount > 0) {
                                            Badge {
                                                Text(if (mainVm.unreadCount > 99) "99+" else "${mainVm.unreadCount}")
                                            }
                                        }
                                    }) {
                                        Icon(
                                            if (selected) tab.selectedIcon else tab.icon,
                                            contentDescription = tab.label
                                        )
                                    }
                                },
                                label  = {
                                    Text(
                                        text = tab.label,
                                        maxLines = 1,
                                        softWrap = false,
                                        fontSize = 10.sp
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PiumsOrange,
                                    selectedTextColor = PiumsOrange,
                                    indicatorColor    = PiumsOrange.copy(alpha = 0.12f)
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController    = innerNav,
                startDestination = Tab.HOME.route,
                modifier         = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                composable(Tab.HOME.route) {
                    HomeScreen(
                        onSearchClick = {
                            innerNav.navigate(Tab.SEARCH.route) {
                                popUpTo(innerNav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        },
                        onArtistClick        = { id -> innerNav.navigate("artist/$id") },
                        onNotificationsClick = { innerNav.navigate("notifications") },
                        onProfileClick       = {
                            innerNav.navigate(Tab.PROFILE.route) {
                                popUpTo(innerNav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        },
                        onSearchByDate       = { date, lat, lng, location ->
                            val enc = java.net.URLEncoder.encode(location, "UTF-8")
                            innerNav.navigate("search-by-date?date=$date&lat=$lat&lng=$lng&location=$enc")
                        }
                    )
                }
                composable(Tab.SEARCH.route) {
                    SearchScreen(onArtistClick = { id -> innerNav.navigate("artist/$id") })
                }
                composable(Tab.MYSPACE.route) {
                    MySpaceScreen(
                        onArtistClick  = { id -> innerNav.navigate("artist/$id") },
                        onBookingClick = { id -> innerNav.navigate("booking-detail/$id") }
                    )
                }
                composable(Tab.INBOX.route) {
                    InboxScreen(
                        onUnreadChanged = { mainVm.loadUnreadCount() },
                        onDisputeClick  = { id -> innerNav.navigate("dispute/$id") },
                        onChatOpen      = { open -> isChatOpen = open }
                    )
                }
                composable(Tab.PROFILE.route) {
                    ProfileScreen(
                        onLogout                     = onLogout,
                        onPaymentsClick              = { innerNav.navigate("payments") },
                        onWalletClick                = { innerNav.navigate("wallet") },
                        onCouponsClick               = { innerNav.navigate("coupons") },
                        onNotifPrefsClick            = { innerNav.navigate("notif-prefs") },
                        onDisputesClick              = { innerNav.navigate("my-disputes") },
                        onTutorialClick              = { innerNav.navigate("how-it-works") },
                        onIdentityVerificationClick  = { innerNav.navigate("identity-verification") }
                    )
                }

                // Detail screens
                composable(
                    route = "artist/{artistId}?date={date}&lat={lat}&lng={lng}&location={location}",
                    arguments = listOf(
                        navArgument("artistId") { type = NavType.StringType },
                        navArgument("date")     { type = NavType.StringType; defaultValue = ""; nullable = true },
                        navArgument("lat")      { type = NavType.StringType; defaultValue = "0"; nullable = true },
                        navArgument("lng")      { type = NavType.StringType; defaultValue = "0"; nullable = true },
                        navArgument("location") { type = NavType.StringType; defaultValue = ""; nullable = true },
                    )
                ) { entry ->
                    val artistId = entry.arguments?.getString("artistId") ?: return@composable
                    // Contexto opcional del flujo "buscar por fecha": se propaga a la reserva
                    val presetDate = entry.arguments?.getString("date").orEmpty()
                    val presetLat  = entry.arguments?.getString("lat") ?: "0"
                    val presetLng  = entry.arguments?.getString("lng") ?: "0"
                    val presetLoc  = entry.arguments?.getString("location").orEmpty()
                    ArtistProfileScreen(
                        artistId = artistId,
                        onBack   = { innerNav.popBackStack() },
                        onBook   = { id ->
                            if (presetDate.isNotBlank() || presetLoc.isNotBlank()) {
                                val locEnc = java.net.URLEncoder.encode(presetLoc, "UTF-8")
                                innerNav.navigate("booking/$id?date=$presetDate&lat=$presetLat&lng=$presetLng&location=$locEnc")
                            } else {
                                innerNav.navigate("booking/$id")
                            }
                        }
                    )
                }
                composable(
                    route = "booking/{artistId}?date={date}&lat={lat}&lng={lng}&location={location}",
                    arguments = listOf(
                        navArgument("artistId") { type = NavType.StringType },
                        navArgument("date")     { type = NavType.StringType; defaultValue = ""; nullable = true },
                        navArgument("lat")      { type = NavType.StringType; defaultValue = "0"; nullable = true },
                        navArgument("lng")      { type = NavType.StringType; defaultValue = "0"; nullable = true },
                        navArgument("location") { type = NavType.StringType; defaultValue = ""; nullable = true },
                    )
                ) { entry ->
                    val artistId = entry.arguments?.getString("artistId") ?: return@composable
                    BookingScreen(
                        artistId = artistId,
                        onBack   = { innerNav.popBackStack() },
                        onDone   = {
                            innerNav.navigate(Tab.MYSPACE.route) {
                                popUpTo(innerNav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        },
                        onPay    = { id -> innerNav.navigate("payment-checkout/$id") }
                    )
                }
                composable("dispute/{disputeId}") { entry ->
                    val disputeId = entry.arguments?.getString("disputeId") ?: return@composable
                    DisputeDetailScreen(onBack = { innerNav.popBackStack() })
                }
                composable("booking-detail/{bookingId}") {
                    BookingDetailScreen(
                        onBack        = { innerNav.popBackStack() },
                        onPay         = { id -> innerNav.navigate("payment-checkout/$id") },
                        onOpenDispute = { id -> innerNav.navigate("create-dispute/$id") },
                        onLeaveReview = { id -> innerNav.navigate("review/$id") }
                    )
                }
                composable("payment-checkout/{bookingId}") {
                    PaymentCheckoutScreen(
                        onBack = { innerNav.popBackStack() },
                        onDone = {
                            innerNav.navigate(Tab.MYSPACE.route) {
                                popUpTo(innerNav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        }
                    )
                }
                composable("review/{bookingId}") {
                    ReviewScreen(onBack = { innerNav.popBackStack() })
                }
                composable("create-dispute/{bookingId}") {
                    CreateDisputeScreen(onBack = { innerNav.popBackStack() })
                }
                composable("notifications") {
                    NotificationsScreen(onBack = { innerNav.popBackStack() })
                }
                composable("payments") {
                    PaymentsScreen(onBack = { innerNav.popBackStack() })
                }
                composable("wallet") {
                    WalletScreen(onBack = { innerNav.popBackStack() })
                }
                composable("coupons") {
                    CouponsScreen(onBack = { innerNav.popBackStack() })
                }
                composable("notif-prefs") {
                    NotificationPreferencesScreen(onBack = { innerNav.popBackStack() })
                }
                composable(
                    route = "search-by-date?date={date}&lat={lat}&lng={lng}&location={location}",
                    arguments = listOf(
                        navArgument("date")     { type = NavType.StringType; defaultValue = "" },
                        navArgument("lat")      { type = NavType.FloatType;  defaultValue = 0f },
                        navArgument("lng")      { type = NavType.FloatType;  defaultValue = 0f },
                        navArgument("location") { type = NavType.StringType; defaultValue = ""; nullable = true },
                    )
                ) { entry ->
                    val dateStr  = entry.arguments?.getString("date") ?: ""
                    val lat      = (entry.arguments?.getFloat("lat") ?: 0f).toDouble()
                    val lng      = (entry.arguments?.getFloat("lng") ?: 0f).toDouble()
                    val location = entry.arguments?.getString("location")
                        ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }
                        ?.takeIf { it.isNotBlank() }
                    ArtistSearchByDateScreen(
                        onBack           = { innerNav.popBackStack() },
                        onArtistClick    = { artistId, date, lat, lng, locationLabel ->
                            val locEnc = java.net.URLEncoder.encode(locationLabel, "UTF-8")
                            val latStr = lat?.toString() ?: "0"
                            val lngStr = lng?.toString() ?: "0"
                            // Mostrar primero el perfil del artista; la reserva hereda fecha/ubicación
                            innerNav.navigate("artist/$artistId?date=$date&lat=$latStr&lng=$lngStr&location=$locEnc")
                        },
                        initialDate      = dateStr.takeIf { it.isNotBlank() }?.let {
                            runCatching { LocalDate.parse(it) }.getOrNull()
                        },
                        initialLat       = lat.takeIf { it != 0.0 },
                        initialLng       = lng.takeIf { it != 0.0 },
                        initialLocation  = location
                    )
                }
                composable("tutorial") {
                    TutorialScreen(
                        onDone = {
                            tokenStorage?.tutorialDone = true
                            innerNav.popBackStack()
                        },
                        tourManager = mainVm.tourManager
                    )
                }
                composable("identity-verification") {
                    IdentityVerificationScreen(onBack = { innerNav.popBackStack() })
                }
                composable("my-disputes") {
                    MyDisputesScreen(
                        onBack          = { innerNav.popBackStack() },
                        onDisputeClick  = { id -> innerNav.navigate("dispute/$id") }
                    )
                }
                composable("how-it-works") {
                    HowItWorksScreen(
                        onBack            = { innerNav.popBackStack() },
                        onNavigateToTab   = { tab ->
                            innerNav.popBackStack()
                            val route = Tab.entries.getOrNull(tab)?.route ?: return@HowItWorksScreen
                            innerNav.navigate(route) {
                                popUpTo(innerNav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        },
                        onStartTour       = {
                            innerNav.popBackStack()
                            mainVm.tourManager.start()
                        }
                    )
                }
            }
        }

        // Tour overlay — sits above everything, including the tab bar
        TourOverlay(
            tourManager = mainVm.tourManager,
            navBarHeightDp = 80.dp
        )
    }
}
