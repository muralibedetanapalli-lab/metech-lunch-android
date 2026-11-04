package com.metech.lunch.core.network

import com.metech.lunch.core.model.ApiError
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val code: String, val message: String, val httpStatus: Int? = null) : ApiResult<Nothing>
}

const val NETWORK_ERROR = "NETWORK"

/** Runs an API call and turns HTTP and network errors into a [ApiResult.Failure] with the server's message. */
suspend fun <T> apiCall(block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    val error = runCatching {
        NetworkModule.json.decodeFromString(ApiError.serializer(), e.response()?.errorBody()?.string().orEmpty())
    }.getOrNull()
    ApiResult.Failure(error?.code ?: "HTTP_${e.code()}", error?.message ?: "Something went wrong (${e.code()})", e.code())
} catch (e: IOException) {
    ApiResult.Failure(NETWORK_ERROR, "Check your internet connection and try again")
}
