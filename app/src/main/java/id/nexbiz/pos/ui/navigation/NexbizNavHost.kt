package id.nexbiz.pos.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import id.nexbiz.pos.ui.screens.dashboard.DashboardScreen
import id.nexbiz.pos.ui.screens.dashboard.DashboardViewModel
import id.nexbiz.pos.ui.screens.pos.PosScreen
import id.nexbiz.pos.ui.screens.pos.PosViewModel
import id.nexbiz.pos.ui.screens.reports.ReportsScreen
import id.nexbiz.pos.ui.security.AppLockManager
import id.nexbiz.pos.ui.security.PermissionCheckOutcome
import id.nexbiz.pos.ui.security.PinLockScreen
import id.nexbiz.pos.ui.security.PosRestrictedAction
import id.nexbiz.pos.ui.security.RolePermissionEnforcer
import id.nexbiz.pos.ui.security.SupervisorAuthorizationDialog
import id.nexbiz.pos.ui.theme.DeepNavy900
import id.nexbiz.pos.ui.theme.ElectricCyan400
import id.nexbiz.pos.ui.theme.SyncEmerald500
import kotlinx.serialization.Serializable

@Serializable
object PosDestinationRoute

@Serializable
object DashboardDestinationRoute

@Serializable
object ReportsDestinationRoute

private data class TopLevelNavItem<T : Any>(
    val route: T,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun NexbizNavHost(
    posViewModel: PosViewModel,
    dashboardViewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appLockManager = remember(context) {
        AppLockManager(context = context, startLocked = false)
    }
    val sessionState by appLockManager.sessionState.collectAsStateWithLifecycle()

    var pendingRestrictedDenial by remember {
        mutableStateOf<PermissionCheckOutcome.Denied?>(null)
    }
    var pendingRestrictedSuccessCallback by remember {
        mutableStateOf<(() -> Unit)?>(null)
    }

    if (sessionState.isLocked) {
        PinLockScreen(
            sessionState = sessionState,
            onSubmitPin = { pin, targetUserId ->
                appLockManager.verifyPinAndUnlock(pin, targetUserId)
            },
            onRequestBiometricUnlock = {
                val activity = context as? FragmentActivity
                if (activity != null) {
                    appLockManager.promptBiometricUnlock(activity)
                }
            }
        )
        return
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val navItems = listOf(
        TopLevelNavItem(
            route = PosDestinationRoute,
            label = "Kasir POS",
            selectedIcon = Icons.Filled.PointOfSale,
            unselectedIcon = Icons.Outlined.PointOfSale,
            testTag = "nav_tab_pos"
        ),
        TopLevelNavItem(
            route = DashboardDestinationRoute,
            label = "Dashboard & BEP",
            selectedIcon = Icons.Filled.Dashboard,
            unselectedIcon = Icons.Outlined.Dashboard,
            testTag = "nav_tab_dashboard"
        ),
        TopLevelNavItem(
            route = ReportsDestinationRoute,
            label = "Laporan P&L",
            selectedIcon = Icons.Filled.Analytics,
            unselectedIcon = Icons.Outlined.Analytics,
            testTag = "nav_tab_reports"
        )
    )

    fun navigateWithRoleCheck(item: TopLevelNavItem<out Any>) {
        appLockManager.recordUserInteractionAndCheckTimeout()
        if (item.route is ReportsDestinationRoute) {
            val check = RolePermissionEnforcer.enforce(
                role = sessionState.activeSession.role,
                action = PosRestrictedAction.FINANCIAL_REPORTS
            )
            if (check is PermissionCheckOutcome.Denied) {
                pendingRestrictedDenial = check
                pendingRestrictedSuccessCallback = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                return
            }
        }
        navController.navigate(item.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 840.dp

        if (useNavigationRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = DeepNavy900,
                    contentColor = Color.White
                ) {
                    navItems.forEach { item ->
                        val selected = currentDestination?.hasRoute(item.route::class) == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateWithRoleCheck(item) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) },
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NexbizNavigationGraph(
                        navController = navController,
                        posViewModel = posViewModel,
                        dashboardViewModel = dashboardViewModel,
                        activeRoleName = sessionState.activeSession.role.name,
                        activeStaffName = sessionState.activeSession.fullName,
                        onLockSession = { appLockManager.lockSessionNow() }
                    )
                }
            }
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    ActiveCashierSecurityBar(
                        staffName = sessionState.activeSession.fullName,
                        roleName = sessionState.activeSession.role.name,
                        onLockClick = { appLockManager.lockSessionNow() }
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = DeepNavy900,
                        contentColor = Color.White,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateWithRoleCheck(item) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = DeepNavy900,
                                    selectedTextColor = ElectricCyan400,
                                    indicatorColor = ElectricCyan400,
                                    unselectedIconColor = Color(0xFF94A3B8),
                                    unselectedTextColor = Color(0xFF94A3B8)
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NexbizNavigationGraph(
                        navController = navController,
                        posViewModel = posViewModel,
                        dashboardViewModel = dashboardViewModel,
                        activeRoleName = sessionState.activeSession.role.name,
                        activeStaffName = sessionState.activeSession.fullName,
                        onLockSession = { appLockManager.lockSessionNow() }
                    )
                }
            }
        }
    }

    val denial = pendingRestrictedDenial
    if (denial != null) {
        SupervisorAuthorizationDialog(
            action = denial.action,
            denialMessage = denial.reasonMessage,
            onVerifySupervisorPin = { pin ->
                appLockManager.verifySupervisorOverridePin(pin, denial.action)
            },
            onAuthorized = {
                val callback = pendingRestrictedSuccessCallback
                pendingRestrictedDenial = null
                pendingRestrictedSuccessCallback = null
                callback?.invoke()
            },
            onDismiss = {
                pendingRestrictedDenial = null
                pendingRestrictedSuccessCallback = null
            }
        )
    }
}

@Composable
private fun ActiveCashierSecurityBar(
    staffName: String,
    roleName: String,
    onLockClick: () -> Unit
) {
    Surface(
        color = DeepNavy900,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = SyncEmerald500,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "$staffName • Role: $roleName",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedButton(
                onClick = onLockClick,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("lock_cashier_session_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Kunci PIN Kasir",
                    tint = ElectricCyan400,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Kunci / Ganti PIN",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan400
                )
            }
        }
    }
}

@Composable
private fun NexbizNavigationGraph(
    navController: androidx.navigation.NavHostController,
    posViewModel: PosViewModel,
    dashboardViewModel: DashboardViewModel,
    activeRoleName: String,
    activeStaffName: String,
    onLockSession: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = PosDestinationRoute
    ) {
        composable<PosDestinationRoute> {
            PosScreen(viewModel = posViewModel)
        }
        composable<DashboardDestinationRoute> {
            BackHandler {
                navController.popBackStack()
            }
            DashboardScreen(
                viewModel = dashboardViewModel,
                onNavigateToReports = {
                    navController.navigate(ReportsDestinationRoute) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
        composable<ReportsDestinationRoute> {
            BackHandler {
                navController.popBackStack()
            }
            ReportsScreen(viewModel = dashboardViewModel)
        }
    }
}
