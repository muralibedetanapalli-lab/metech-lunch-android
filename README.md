# Metech Lunch — Android

Android apps for the Metech employee lunch ordering platform. One Gradle project builds two apps that share their core code.

| Module | What it is |
|---|---|
| `app-customer` | Employee app: login, office selection, daily menu, order and payment, order status |
| `app-delivery` | Delivery partner app: assigned batches, pickup, handover (OTP), issue reporting |
| `core:model` | Pure Kotlin API and domain models (orders, menu, batches, auth) |
| `core:network` | Retrofit `LunchApi` and the OkHttp client with the auth header |
| `core:auth` | `SessionStore` (token storage) and `AuthRepository` (phone + OTP) |
| `core:ui` | Compose theme, shared login screen, formatting helpers |

The owner and kitchen web dashboard and the backend are not in this repo.

## Build

Requires the Android SDK (API 36) and JDK 17 or newer. Android Studio's bundled JBR works.

```bash
./gradlew assembleDebug
```

APKs are written to `app-customer/build/outputs/apk/debug/` and `app-delivery/build/outputs/apk/debug/`.

Debug builds call `http://10.0.2.2:8080/api/`, which is a local backend as seen from the emulator. The release URL is a placeholder in each app's `build.gradle.kts`.

## Rules the apps rely on the server for

- The 10 AM same-day cutoff, service area and meal capacity are enforced by the backend. The apps only display the result.
- An order is confirmed only after the backend verifies the payment. The app reads order and payment state from the server and never trusts the gateway redirect.
- `PlaceOrderRequest.idempotencyKey` guards against duplicate orders.
- Every order, menu and batch carries a `kitchenId` so more kitchens can be added later.

## Open decisions (pending with the client)

The payment gateway, login/OTP provider, timezone and operating days, refund policy, and handover model (per employee or office coordinator). The API paths in `LunchApi` are placeholders until the backend contract is agreed.
