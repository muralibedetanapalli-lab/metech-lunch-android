package com.metech.lunch.delivery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.metech.lunch.delivery.batches.BatchDetailScreen
import com.metech.lunch.delivery.batches.BatchDetailViewModel
import com.metech.lunch.delivery.batches.BatchesScreen
import com.metech.lunch.delivery.batches.BatchesViewModel
import com.metech.lunch.delivery.login.LoginViewModel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = appContainer
        setContent {
            LunchTheme { DeliveryApp(container) }
        }
    }
}

private object Routes {
    const val LOGIN = "login"
    const val BATCHES = "batches"
    const val BATCH = "batch/{id}"
    fun batch(id: String) = "batch/$id"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeliveryApp(container: AppContainer) {
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

    // Signed out elsewhere, deactivated, or refresh token expired: back to login.
    LaunchedEffect(Unit) {
        container.session.tokens.drop(1).collect { tokens ->
            if (tokens == null && nav.currentDestination?.route != Routes.LOGIN) nav.goToLogin()
        }
    }

    Scaffold(
        topBar = {
            if (route != null && route != Routes.LOGIN) {
                TopAppBar(
                    title = { Text(if (route == Routes.BATCHES) "My deliveries" else "Delivery") },
                    navigationIcon = {
                        if (route == Routes.BATCH) TextButton(onClick = { nav.popBackStack() }) { Text("Back") }
                    },
                    actions = {
                        if (route == Routes.BATCHES) {
                            TextButton(onClick = { scope.launch { container.auth.logout() } }) { Text("Sign out") }
                        }
                    },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (startLoggedIn) Routes.BATCHES else Routes.LOGIN,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.LOGIN) {
                val vm: LoginViewModel = viewModel { LoginViewModel(container.auth) }
                val error by vm.error.collectAsStateWithLifecycle()
                OtpLoginScreen(
                    title = "Partner login",
                    onRequestOtp = vm::requestOtp,
                    onVerifyOtp = { phone, otp ->
                        vm.verifyOtp(phone, otp) {
                            nav.navigate(Routes.BATCHES) { popUpTo(Routes.LOGIN) { inclusive = true } }
                        }
                    },
                    errorMessage = error,
                )
            }
            composable(Routes.BATCHES) {
                val vm: BatchesViewModel = viewModel { BatchesViewModel(container.api) }
                val state by vm.state.collectAsStateWithLifecycle()
                LifecycleResumeEffect(Unit) {
                    vm.load()
                    onPauseOrDispose { }
                }
                BatchesScreen(state, onOpen = { nav.navigate(Routes.batch(it)) }, onRefresh = vm::load)
            }
            composable(Routes.BATCH) { entry ->
                val batchId = entry.arguments?.getString("id").orEmpty()
                val vm: BatchDetailViewModel = viewModel { BatchDetailViewModel(container.api, batchId) }
                val state by vm.state.collectAsStateWithLifecycle()
                // Refresh when coming back from the maps or dialer app.
                LifecycleResumeEffect(batchId) {
                    vm.load()
                    onPauseOrDispose { }
                }
                BatchDetailScreen(
                    state = state,
                    onConfirmPickup = vm::confirmPickup,
                    onHandOver = vm::handOver,
                    onReportIssue = vm::reportIssue,
                    onRetry = vm::load,
                    onDismissMessages = vm::clearMessages,
                )
            }
        }
    }
}

private fun NavHostController.goToLogin() {
    navigate(Routes.LOGIN) { popUpTo(graph.id) { inclusive = true } }
}
