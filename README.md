# Quickpay customer wallet demo

A runnable customer payment demo built with a React/Vite client and Spring Boot microservices. Login and account creation are the first screen. Customers can select a merchant, pay from their local demo balance, receive a confirmation, and review their transaction history.

**This is not a real payment processor.** A successful demo payment debits the account's local PostgreSQL `available_balance`; it never contacts or charges an external payment provider. Use test data only.

## Architecture

| Component | Responsibility | Database |
|---|---|---|
| API gateway (`8081` on host, `8080` in Docker) | Routes APIs and verifies customer JWTs | — |
| Customer service | Signup/login, customer profile, wallet balance | `customer_db` |
| Merchant service | Merchant listing | `merchant_db` |
| Payment service | Demo wallet debit and payment creation; publishes Kafka event | `payment_db` |
| Transaction service | Consumes payment events and stores customer-scoped history | `transaction_db` |
| Notification service | Consumes payment events and stores customer-scoped alerts | `notification_db` |
| PostgreSQL (`5433` on host, `5432` in Docker) | One local database per service | — |
| Kafka (`9092` internal, `29092` host) | `payment-events` topic | — |
| React UI (`3000`) | Signup/login, overview, payment, history, service status | — |

Each backend application has its own Maven module and Docker image under `backend`. The `shared` module contains common JWT, health-check, and API error handling code. Only the gateway and frontend ports are exposed for browser traffic; backend service ports are internal to the Compose network.

## Run with Docker Compose

Requirement: Docker Desktop with Docker Compose. Docker builds the Java services and frontend, so Java, Maven, and Node.js do not need to be installed on the host.

From this folder:

```powershell
docker compose up --build
```

Open <http://localhost:3000>. The first screen is customer signup/login. New accounts start with a `$1,000.00` demo balance. The API gateway health endpoint is <http://localhost:8081/health>. Set `GATEWAY_PORT` in `.env` if port `8081` is unavailable.

Stop the stack with `Ctrl+C`, or from another PowerShell window run `docker compose down`. To rebuild and start after source changes, run `docker compose up --build` again.

For a new database, optionally copy `.env.example` to `.env` and set `POSTGRES_PASSWORD` and `JWT_SECRET` before starting Compose. PostgreSQL applies its password only when its data volume is first initialized. If you already have a database volume, keep its existing password in `.env`; changing the value alone will not update the database. Changing `JWT_SECRET` invalidates existing login tokens. `docker compose down -v` resets the database and deletes local account/payment data.

## Development

Each Spring Boot service can be built independently by Compose from its Maven module. The Compose setup is the supported way to run the complete application.

## Customer data and authentication

The customer service creates:

- `accounts`: `customer_id` primary key, customer name, unique email, phone number, account status, non-negative available balance, creation and update timestamps.
- `customer_credentials`: `customer_id` foreign key to `accounts`, BCrypt password hash, creation and update timestamps.

Passwords are never persisted in plaintext. Passwords must be 10–72 characters, and customer API requests (except signup/login) require a signed, expiring JWT. The gateway derives customer identity from the JWT and sets the internal customer header itself; transaction and notification reads are scoped to that identity.

The Compose-provided `.env` defaults are for local demonstration only. Configure unique secrets and secure database credentials before exposing any service outside the local machine. This educational app is not a production financial system.

## API overview

All browser API calls use the gateway at `/api`:

- `POST /api/auth/signup` with `{ "name", "email", "phone", "password" }`
- `POST /api/auth/login` with `{ "email", "password" }`
- `GET /api/customers/me`
- `GET /api/merchants`
- `POST /api/payments` with `{ "merchantId", "amount" }`
- `GET /api/transactions`
- `GET /api/notifications`

The payment service debits the wallet through the internal customer service endpoint and publishes a `payment-events` Kafka message. Transaction and notification services consume that event independently. The payment response itself immediately confirms that the **demo** payment completed.
