package com.metech.lunch.customer

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.metech.lunch.core.ui.LunchLogo
import com.metech.lunch.core.ui.LunchTheme
import com.metech.lunch.core.ui.OtpLoginScreen
import com.metech.lunch.core.ui.Palette
import com.metech.lunch.customer.login.LoginViewModel
import com.metech.lunch.customer.ordering.CheckoutScreen
import com.metech.lunch.customer.ordering.MenuScreen
import com.metech.lunch.customer.ordering.OrderingViewModel
import com.metech.lunch.customer.ordering.ProfileScreen
import com.metech.lunch.customer.orders.OrderDetailScreen
import com.metech.lunch.customer.orders.OrderDetailViewModel
import com.metech.lunch.customer.orders.OrdersScreen
import com.metech.lunch.customer.orders.OrdersViewModel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = appContainer
        setContent {
            LunchTheme { CustomerApp(container) }
        }
    }
}

private object Routes {
    const val LOGIN = "login"
    const val PROFILE = "profile"
    const val MENU = "menu"
    const val CHECKOUT = "checkout"
    const val ORDERS = "orders"
    const val ORDER = "order/{id}"
    fun order(id: String) = "order/$id"
}

private val TABS = setOf(Routes.MENU, Routes.ORDERS)

@Composable
private fun CustomerApp(container: AppContainer) {
    val loggedIn by produceState<Boolean?>(initialValue = null) { value = container.auth.isLoggedIn() }
    val startLoggedIn = loggedIn
    if (startLoggedIn == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            LunchLogo(size = 72.dp)
        }
        return
    }

    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    // If the refresh token is rejected, the session store is cleared: go back to login.
    LaunchedEffect(Unit) {
        container.session.tokens.drop(1).collect { tokens ->
            if (tokens == null && nav.currentDestination?.route != Routes.LOGIN) nav.goToLogin()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(route in TABS, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    TabItem(nav, route, Routes.MENU, "Lunch") { Icon(Icons.Default.Home, null) }
                    TabItem(nav, route, Routes.ORDERS, "Orders") { Icon(Icons.AutoMirrored.Filled.List, null) }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (startLoggedIn) Routes.MENU else Routes.LOGIN,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 6 } },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(260)) { it / 6 } },
        ) {
            composable(Routes.LOGIN) {
                val vm: LoginViewModel = viewModel { LoginViewModel(container.auth) }
                val state by vm.state.collectAsStateWithLifecycle()
                OtpLoginScreen(
                    appName = "Metech Lunch",
                    tagline = "Fresh lunch, delivered to your desk",
                    heroEmoji = listOf("🍛", "🥗", "🍚", "🍗"),
                    codeSent = state.codeSent,
                    shownCode = state.shownCode,
                    busy = state.busy,
                    errorMessage = state.error,
                    onRequestOtp = vm::requestOtp,
                    onVerifyOtp = { phone, otp ->
                        vm.verifyOtp(phone, otp) {
                            nav.navigate(Routes.MENU) { popUpTo(Routes.LOGIN) { inclusive = true } }
                        }
                    },
                    onEditPhone = vm::editPhone,
                )
            }
            composable(Routes.MENU) {
                val vm = nav.orderingViewModel(container)
                val state by vm.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.needsProfile) { if (state.needsProfile) nav.navigate(Routes.PROFILE) }
                LifecycleResumeEffect(Unit) {
                    vm.refresh()
                    onPauseOrDispose { }
                }
                MenuScreen(
                    state = state,
                    onChangeQuantity = vm::changeQuantity,
                    onChangeOffice = { nav.navigate(Routes.PROFILE) },
                    onReview = { nav.navigate(Routes.CHECKOUT) },
                    onRetry = vm::refresh,
                )
            }
            composable(Routes.PROFILE) {
                val vm = nav.orderingViewModel(container)
                val state by vm.state.collectAsStateWithLifecycle()
                ProfileScreen(
                    state = state,
                    onBack = if (state.needsProfile) null else ({ nav.popBackStack() }),
                    onSave = { name, officeId, dropPointId ->
                        vm.saveProfile(name, officeId, dropPointId) { nav.popBackStack() }
                    },
                    onSignOut = { scope.launch { container.auth.logout() } },
                )
            }
            composable(Routes.CHECKOUT) {
                val vm = nav.orderingViewModel(container)
                val state by vm.state.collectAsStateWithLifecycle()
                val context = LocalContext.current
                val toolbar = MaterialTheme.colorScheme.primary.toArgb()
                CheckoutScreen(
                    state = state,
                    onChangeQuantity = vm::changeQuantity,
                    onBack = { nav.popBackStack() },
                    onChangeOffice = { nav.navigate(Routes.PROFILE) },
                ) {
                    vm.placeOrder { orderId, paymentUrl ->
                        nav.navigate(Routes.order(orderId)) { popUpTo(Routes.MENU) }
                        paymentUrl?.let {
                            container.checkoutUrls[orderId] = it
                            openCheckout(context, it, toolbar)
                        }
                    }
                }
            }
            composable(Routes.ORDERS) {
                val vm: OrdersViewModel = viewModel { OrdersViewModel(container.api) }
                val state by vm.state.collectAsStateWithLifecycle()
                LifecycleResumeEffect(Unit) {
                    vm.load()
                    onPauseOrDispose { }
                }
                OrdersScreen(
                    state,
                    onOpen = { nav.navigate(Routes.order(it)) },
                    onRetry = vm::load,
                    onBrowse = { nav.switchTab(Routes.MENU) },
                )
            }
            composable(Routes.ORDER) { entry ->
                val orderId = entry.arguments?.getString("id").orEmpty()
                val vm: OrderDetailViewModel = viewModel { OrderDetailViewModel(container.api, orderId) }
                val state by vm.state.collectAsStateWithLifecycle()
                // Re-check when the user comes back from the payment page.
                LifecycleResumeEffect(orderId) {
                    vm.startPolling()
                    onPauseOrDispose { vm.stopPolling() }
                }
                val context = LocalContext.current
                val toolbar = MaterialTheme.colorScheme.primary.toArgb()
                val checkoutUrl = container.checkoutUrls[orderId]
                OrderDetailScreen(
                    state,
                    onPay = checkoutUrl?.let { url -> { openCheckout(context, url, toolbar) } },
                    onCancel = vm::cancel,
                    onBack = { nav.popBackStack() },
                    onBackToMenu = { nav.popBackStack(Routes.MENU, inclusive = false) },
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(
    nav: NavHostController,
    current: String?,
    route: String,
    label: String,
    icon: @Composable () -> Unit,
) {
    NavigationBarItem(
        selected = current == route,
        onClick = { nav.switchTab(route) },
        icon = icon,
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            indicatorColor = Palette.SaffronSoft,
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

private fun NavHostController.switchTab(route: String) {
    // The menu is always at the root of the signed-in stack (sign-in pops itself).
    navigate(route) {
        popUpTo(Routes.MENU) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun openCheckout(context: android.content.Context, url: String, toolbarColor: Int) {
    CustomTabsIntent.Builder()
        .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(toolbarColor).build())
        .setShowTitle(true)
        .build()
        .launchUrl(context, Uri.parse(url))
}

/** The menu, profile and checkout screens share one ViewModel tied to the menu entry. */
@Composable
private fun NavHostController.orderingViewModel(container: AppContainer): OrderingViewModel {
    val owner = runCatching { getBackStackEntry(Routes.MENU) }.getOrNull()
    return if (owner != null) viewModel(viewModelStoreOwner = owner) { OrderingViewModel(container.api) }
    else viewModel { OrderingViewModel(container.api) }
}

private fun NavHostController.goToLogin() {
    navigate(Routes.LOGIN) { popUpTo(graph.id) { inclusive = true } }
}
