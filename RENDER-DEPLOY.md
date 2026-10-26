# Deploy on Render

1. Push this project to GitHub.
2. Render: New > PostgreSQL. Copy internal host, user, password, db name.
   All services can share this one database (table names do not collide).
3. For each service: New > Web Service > Docker, then set:

| Render name     | Dockerfile Path                          | Build Context | Env file (Add from .env)        |
|-----------------|------------------------------------------|---------------|---------------------------------|
| qp-customer     | backend/Dockerfile.customer-service      | backend       | render-env/customer-service.env |
| qp-merchant     | backend/Dockerfile.merchant-service      | backend       | render-env/merchant-service.env |
| qp-transaction  | backend/Dockerfile.transaction-service   | backend       | render-env/transaction-service.env |
| qp-notification | backend/Dockerfile.notification-service  | backend       | render-env/notification-service.env |
| qp-payment      | backend/Dockerfile.payment-service       | backend       | render-env/payment-service.env  |
| qp-gateway      | backend/Dockerfile.api-gateway           | backend       | render-env/api-gateway.env      |
| qp-frontend     | frontend/Dockerfile.render               | frontend      | render-env/frontend.env         |

Root Directory: leave empty. Same region for everything.
Deploy order: customer, merchant, transaction, notification, payment, gateway, frontend.
Replace every PASTE_* value. Never commit real passwords.
Kafka is needed for payment, transaction, notification (hosted Kafka required).

Local run: docker compose up --build  (uses .env, open http://localhost:3000)
