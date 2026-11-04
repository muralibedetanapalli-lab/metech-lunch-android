package com.metech.lunch.customer

import android.app.Application
import android.content.Context
import com.metech.lunch.core.auth.AuthRepository
import com.metech.lunch.core.auth.SessionStore
import com.metech.lunch.core.model.UserRole
import com.metech.lunch.core.network.LunchApi
import com.metech.lunch.core.network.NetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class LunchCustomerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual dependency wiring; swap for Hilt if the graph grows. */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val session = SessionStore(context, appScope)

    val api: LunchApi = NetworkModule.createApi(
        baseUrl = BuildConfig.API_BASE_URL,
        tokenProvider = session,
        enableLogging = BuildConfig.DEBUG,
    )

    val auth = AuthRepository(api, session, UserRole.CUSTOMER)
}

val Context.appContainer: AppContainer
    get() = (applicationContext as LunchCustomerApp).container
