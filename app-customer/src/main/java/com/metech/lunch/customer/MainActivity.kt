package com.metech.lunch.customer

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.metech.lunch.core.ui.LunchTheme
import com.metech.lunch.core.ui.OtpLoginScreen
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerApp(container: AppContainer) {
    val loggedIn by produceState<Boolean?>(initialValue = null) { value = container.auth.isLoggedIn() }
    val startLoggedIn = loggedIn
    if (startLoggedIn == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
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
        topBar = {
            if (route != null && route != Routes.LOGIN) {
                TopAppBar(
                    title = { Text(titleFor(route)) },
                    actions = {
                        if (route == Routes.MENU) {
                            TextButton(onClick = { nav.navigate(Routes.ORDERS) }) { Text("My orders") }
                            TextButton(onClick = { scope.launch { container.auth.logout() } }) { Text("Sign out") }
                        }
                    },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (startLoggedIn) Routes.MENU else Routes.LOGIN,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.LOGIN) {
                val vm: LoginViewModel = viewModel { LoginViewModel(container.auth) }
                val error by vm.error.collectAsStateWithLifecycle()
                OtpLoginScreen(
                    title = "Order lunch",
                    onRequestOtp = vm::requestOtp,
                    onVerifyOtp = { phone, otp ->
                        vm.verifyOtp(phone, otp) {
                            nav.navigate(Routes.MENU) { popUpTo(Routes.LOGIN) { inclusive = true } }
                        }
                    },
                    errorMessage = error,
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
                ProfileScreen(state) { name, officeId, dropPointId ->
                    vm.saveProfile(name, officeId, dropPointId) { nav.popBackStack(Routes.MENU, inclusive = false) }
                }
            }
            composable(Routes.CHECKOUT) {
                val vm = nav.orderingViewModel(container)
                val state by vm.state.collectAsStateWithLifecycle()
                val context = LocalContext.current
                CheckoutScreen(state, onChangeQuantity = vm::changeQuantity) {
                    vm.placeOrder { orderId, paymentUrl ->
                        nav.navigate(Routes.order(orderId)) { popUpTo(Routes.MENU) }
                        paymentUrl?.let { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(it)) }
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
                OrdersScreen(state, onOpen = { nav.navigate(Routes.order(it)) }, onRetry = vm::load)
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
                OrderDetailScreen(
                    state,
                    onCancel = vm::cancel,
                    onBackToMenu = { nav.popBackStack(Routes.MENU, inclusive = false) },
                )
            }
        }
    }
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

private fun titleFor(route: String) = when (route) {
    Routes.MENU -> "Today's lunch"
    Routes.PROFILE -> "Profile"
    Routes.CHECKOUT -> "Checkout"
    Routes.ORDERS -> "My orders"
    Routes.ORDER -> "Order"
    else -> ""
}
