package com.metech.lunch.core.network

import com.metech.lunch.core.model.AuthTokens
import com.metech.lunch.core.model.DailyMenu
import com.metech.lunch.core.model.DeliveryBatch
import com.metech.lunch.core.model.DeliveryIssueReport
import com.metech.lunch.core.model.HandoverConfirmation
import com.metech.lunch.core.model.Office
import com.metech.lunch.core.model.Order
import com.metech.lunch.core.model.OtpRequest
import com.metech.lunch.core.model.OtpRequestResponse
import com.metech.lunch.core.model.OtpVerifyRequest
import com.metech.lunch.core.model.PickupConfirmation
import com.metech.lunch.core.model.PlaceOrderRequest
import com.metech.lunch.core.model.PlaceOrderResponse
import com.metech.lunch.core.model.UpdateProfileRequest
import com.metech.lunch.core.model.UserProfile
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Backend contract shared by both apps. Implemented by the Ktor server in /backend
 * (see backend/.../routes/Routes.kt); paths are relative to `<host>/api/`.
 */
interface LunchApi {

    // --- Auth ---
    @POST("auth/otp/request")
    suspend fun requestOtp(@Body body: OtpRequest): OtpRequestResponse

    @POST("auth/otp/verify")
    suspend fun verifyOtp(@Body body: OtpVerifyRequest): AuthTokens

    @GET("me")
    suspend fun me(): UserProfile

    @PUT("me")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): UserProfile

    // --- Customer ---
    @GET("offices")
    suspend fun offices(): List<Office>

    @GET("menu/today")
    suspend fun todaysMenu(@Query("officeId") officeId: String): DailyMenu

    @POST("orders")
    suspend fun placeOrder(@Body body: PlaceOrderRequest): PlaceOrderResponse

    @GET("orders")
    suspend fun myOrders(): List<Order>

    @GET("orders/{id}")
    suspend fun order(@Path("id") id: String): Order

    @POST("orders/{id}/cancel")
    suspend fun requestCancellation(@Path("id") id: String): Order

    // --- Delivery partner ---
    @GET("partner/batches")
    suspend fun assignedBatches(@Query("status") status: String? = null): List<DeliveryBatch>

    @GET("partner/batches/{id}")
    suspend fun batch(@Path("id") id: String): DeliveryBatch

    @POST("partner/batches/{id}/pickup")
    suspend fun confirmPickup(@Path("id") id: String, @Body body: PickupConfirmation): DeliveryBatch

    @POST("partner/batches/{id}/handover")
    suspend fun confirmHandover(@Path("id") id: String, @Body body: HandoverConfirmation): DeliveryBatch

    @POST("partner/batches/{id}/issue")
    suspend fun reportIssue(@Path("id") id: String, @Body body: DeliveryIssueReport): DeliveryBatch
}
