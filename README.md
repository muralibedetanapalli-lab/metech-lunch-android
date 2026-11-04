# Metech Lunch

Employee lunch ordering platform: a customer Android app, a delivery partner Android app, an owner/kitchen web dashboard, and the backend they share.

| Module | What it is |
|---|---|
| `backend` | Ktor + PostgreSQL API: OTP login, menus, orders, payments, delivery batches, admin API |
| `dashboard` | React + TypeScript (Vite) owner and kitchen dashboard |
| `app-customer` | Employee app: login, office selection, today's menu, cart, payment, order status and history |
| `app-delivery` | Delivery partner app: assigned batches, pickup count, navigation and call, OTP handover, problem reports, history |
| `core:model` | Kotlin API models shared by the backend and both apps |
| `core:network` | Retrofit `LunchApi`, token refresh, error handling |
| `core:auth` | Session storage and OTP login |
| `core:ui` | Compose theme, shared login screen, formatting |

## Run the backend locally

The simplest option needs no database install and no Docker. It runs an embedded Postgres with demo data:

```bash
./gradlew :backend:runDev
```

The API listens on `http://localhost:8080/api`. Every phone number's OTP is `123456`.

Demo accounts:
- **Customer:** any number. The app asks for a name and office the first time.
- **Delivery partner:** `+919999900001`.
- **Owner:** `+919999900000`.

Ordering closes at 10:00 India time. To try ordering later in the day, make the server pretend it's morning:

```bash
DEV_PRETEND_TIME=09:00 ./gradlew :backend:runDev
```

To use a real Postgres instead, start one with Docker. It runs on port 5433 so it doesn't clash with a local install:

```bash
docker compose up -d
```

```bash
./gradlew :backend:run
```

Payments use a **mock gateway** in dev. The checkout link opens a test page with Pay and Fail buttons, and no money moves.

### Backend configuration (environment variables)

| Variable | Purpose | Dev default |
|---|---|---|
| `APP_ENV` | `production` turns off the dev OTP, mock payments and demo data, and makes secrets required | (dev) |
| `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` | Postgres connection | `localhost:5433/metech_lunch`, `metech` / `metech` |
| `JWT_SECRET` | Signs access tokens | dev value |
| `PUBLIC_BASE_URL` | Public URL of the API, used in checkout links | `http://localhost:8080` |
| `CORS_ORIGINS` | Dashboard origins, comma-separated | `http://localhost:5173` |

### Tests

```bash
./gradlew :backend:test
```

The tests run against an embedded Postgres. They cover the acceptance checks from the scope:
- the 10 AM cutoff, including late payments for orders placed before it
- meal capacity, including unpaid orders inside the payment hold window
- duplicate and forged payment callbacks
- idempotent order placement
- cancellation and refund rules
- role isolation, including kitchen staff versus owner
- delivery pickup count, handover OTPs, and partial or failed delivery
- kitchen totals and batches that reconcile with confirmed orders
- refund approval and rejection
- menu changes that can't undercut meals already sold
- reports and CSV export, including protection against spreadsheet formula injection

## Run the dashboard

Start the backend first (`./gradlew :backend:runDev`), then:

```bash
cd dashboard
```

```bash
npm install
```

```bash
npm run dev
```

Open http://localhost:5173. During development, `/api` calls are forwarded to the backend on port 8080.

Sign in as **Owner** with `+919999900000`, or as **Kitchen staff** with `+919999900003`. The OTP is `123456`.

| Page | Who | What it does |
|---|---|---|
| Overview | Everyone | The day's orders by status, meals to prepare, open issues; owners also see sales and refunds |
| Kitchen prep | Everyone | Meal totals, packing lists by office and drop point (printable), and "Start preparation" after the cutoff |
| Dispatch | Everyone | Create batches from paid orders, assign a delivery partner, optional office-coordinator code, delivery issues |
| Menu | Everyone | Publish the day's meals, prices and capacity. Owners also edit the meal catalogue |
| Orders | Owner | Every order with customer, payment reference and status; CSV export; start a refund |
| Refunds | Owner | Approve (sends the refund to the gateway) or reject (the order goes back to confirmed) |
| Reports | Owner | Sales, refunds, meals by day, meal and office totals for a date range; CSV export |
| People | Owner | Add or deactivate delivery partners, kitchen staff and owners; customer list |
| Settings | Owner | Kitchen name, address and cutoff time; offices and drop points; activity log |

Kitchen staff cannot reach financial, people or settings endpoints; the backend enforces this, not just the menu. The production build is `npm run build`, which writes static files to `dashboard/dist/`. Serve them from the same domain as the API, or set `CORS_ORIGINS`.

## Run the Android apps

```bash
./gradlew assembleDebug
```

Debug builds call `http://10.0.2.2:8080/api/`, which is your computer as seen from the Android emulator. Start `runDev` first.

## Business rules (all enforced by the server)

- **Same-day ordering:** orders are accepted until the kitchen's cutoff (10:00 Asia/Kolkata by default).
- **Payment confirmation:** an order is confirmed only by a verified gateway callback, never by the app's redirect. Callbacks are recorded once, so replays change nothing.
- **Payment hold:** an unpaid order holds its meals for 15 minutes. If payment arrives after the hold and the meals are gone, the order is cancelled and a refund is requested.
- **Duplicate orders:** each checkout sends an `idempotencyKey`, so retries never create a second order.
- **Cancellation:** a paid order can be cancelled before the cutoff, and the refund then waits for owner approval.
- **Handover:** the partner enters either the customer's 4-digit code or the office coordinator's batch code.
- **Multiple kitchens:** every record carries a `kitchenId`, ready for more kitchens later.

## Open decisions (pending with the client)

- **Payment gateway:** it plugs in behind the `PaymentGateway` interface.
- **SMS/OTP provider:** behind the `OtpSender` interface.
- **Business rules:** operating days, the refund policy, and whether employees or an office coordinator confirm handover.
- **Hosting.**
