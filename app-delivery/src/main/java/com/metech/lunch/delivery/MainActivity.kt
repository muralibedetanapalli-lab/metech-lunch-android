package com.metech.lunch.delivery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.metech.lunch.core.ui.LunchLogo
import com.metech.lunch.core.ui.LunchTheme
import com.metech.lunch.core.ui.OtpLoginScreen
import com.metech.lunch.core.ui.Palette
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

@Composable
private fun DeliveryApp(container: AppContainer) {
    val loggedIn by produceState<Boolean?>(initialValue = null) { value = container.auth.isLoggedIn() }
    val startLoggedIn = loggedIn
    if (startLoggedIn == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            LunchLogo(size = 72.dp, tile = Palette.Saffron, bowl = Palette.GreenDeep)
        }
        return
    }

    val nav = rememberNavController()
    val scope = rememberCoroutineScope()

    // Signed out elsewhere, deactivated, or refresh token expired: back to login.
    LaunchedEffect(Unit) {
        container.session.tokens.drop(1).collect { tokens ->
            if (tokens == null && nav.currentDestination?.route != Routes.LOGIN) nav.goToLogin()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavHost(
            navController = nav,
            startDestination = if (startLoggedIn) Routes.BATCHES else Routes.LOGIN,
            enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 6 } },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(260)) { it / 6 } },
        ) {
            composable(Routes.LOGIN) {
                val vm: LoginViewModel = viewModel { LoginViewModel(container.auth) }
                val state by vm.state.collectAsStateWithLifecycle()
                OtpLoginScreen(
                    appName = "Lunch Partner",
                    tagline = "Pick up, deliver, done.",
                    heroEmoji = listOf("🛵", "🍱", "📦", "✅"),
                    codeSent = state.codeSent,
                    busy = state.busy,
                    errorMessage = state.error,
                    onRequestOtp = vm::requestOtp,
                    onVerifyOtp = { phone, otp ->
                        vm.verifyOtp(phone, otp) {
                            nav.navigate(Routes.BATCHES) { popUpTo(Routes.LOGIN) { inclusive = true } }
                        }
                    },
                    onEditPhone = vm::editPhone,
                )
            }
            composable(Routes.BATCHES) {
                val vm: BatchesViewModel = viewModel { BatchesViewModel(container.api) }
                val state by vm.state.collectAsStateWithLifecycle()
                LifecycleResumeEffect(Unit) {
                    vm.load()
                    onPauseOrDispose { }
                }
                BatchesScreen(
                    state,
                    onOpen = { nav.navigate(Routes.batch(it)) },
                    onRefresh = vm::load,
                    onSignOut = { scope.launch { container.auth.logout() } },
                )
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
                    onBack = { nav.popBackStack() },
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
