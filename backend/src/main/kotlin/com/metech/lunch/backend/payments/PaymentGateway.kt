package com.metech.lunch.backend.payments

import com.metech.lunch.backend.common.constantTimeEquals
import com.metech.lunch.backend.common.randomToken
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class Checkout(val gatewayRef: String, val url: String)

/** A gateway callback after its signature has been checked. */
data class GatewayEvent(val eventId: String, val gatewayRef: String, val success: Boolean, val payload: String)

/**
 * One payment provider. The real gateway (Razorpay, Cashfree, ...) is still to be chosen;
 * it will implement this interface the same way [MockGateway] does.
 */
interface PaymentGateway {
    val name: String

    fun createCheckout(paymentId: String, orderId: String, amountPaise: Long): Checkout

    /** Verifies the callback signature and parses it. Returns null if the signature is invalid. */
    fun parseWebhook(headers: Map<String, String>, body: String): GatewayEvent?
}

@Serializable
data class MockWebhookBody(val eventId: String, val gatewayRef: String, val status: String)

/** Dev/test gateway: a local checkout page and HMAC-signed callbacks. */
class MockGateway(private val publicBaseUrl: String, private val secret: String) : PaymentGateway {
    override val name = "mock"

    override fun createCheckout(paymentId: String, orderId: String, amountPaise: Long): Checkout {
        val ref = "mock_" + randomToken(12)
        return Checkout(ref, "$publicBaseUrl/api/payments/mock/checkout/$ref")
    }

    override fun parseWebhook(headers: Map<String, String>, body: String): GatewayEvent? {
        val signature = headers[SIGNATURE_HEADER.lowercase()] ?: return null
        if (!constantTimeEquals(signature, sign(body))) return null
        val parsed = runCatching { Json.decodeFromString(MockWebhookBody.serializer(), body) }.getOrNull() ?: return null
        return GatewayEvent(parsed.eventId, parsed.gatewayRef, parsed.status == "success", body)
    }

    fun sign(body: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(body.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val SIGNATURE_HEADER = "X-Mock-Signature"
    }
}
