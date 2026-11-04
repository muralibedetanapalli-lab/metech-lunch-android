package com.metech.lunch.delivery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.metech.lunch.core.ui.LunchTheme
import com.metech.lunch.core.ui.OtpLoginScreen
import com.metech.lunch.delivery.home.HomeScreen
import com.metech.lunch.delivery.login.LoginViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = appContainer
        setContent {
            LunchTheme {
                Scaffold { padding ->
                    DeliveryNavHost(container, Modifier.padding(padding))
                }
            }
        }
    }
}

private object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
}

@Composable
private fun DeliveryNavHost(container: AppContainer, modifier: Modifier = Modifier) {
    val loggedIn by produceState<Boolean?>(initialValue = null) {
        value = container.auth.isLoggedIn()
    }

    val isLoggedIn = loggedIn
    if (isLoggedIn == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = if (isLoggedIn) Routes.HOME else Routes.LOGIN,
        modifier = modifier,
    ) {
        composable(Routes.LOGIN) {
            val vm: LoginViewModel = viewModel { LoginViewModel(container.auth) }
            val error by vm.error.collectAsStateWithLifecycle()
            OtpLoginScreen(
                title = "Partner login",
                onRequestOtp = vm::requestOtp,
                onVerifyOtp = { phone, otp ->
                    vm.verifyOtp(phone, otp) {
                        nav.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } }
                    }
                },
                errorMessage = error,
            )
        }
        composable(Routes.HOME) {
            HomeScreen()
        }
    }
}
