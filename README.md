# PayFlow

Payment processing system built with Java 17, Spring Boot 3, PostgreSQL, Apache Kafka and AWS SNS/SQS.

## Technologies

- Java 17
- Spring Boot 3.3.5
- PostgreSQL 16
- Flyway
- Apache Kafka
- AWS SNS/SQS (LocalStack for local development)
- Docker + Docker Compose
- Lombok
- MapStruct

## Requirements

- Java 17+
- Maven 3.9+
- Docker + Docker Compose

## Getting Started

Clone the repository:

```bash
git clone https://github.com/Thiagosb21/payflow.git
cd payflow
```

Start the infrastructure:

```bash
docker compose up -d
```

Create AWS resources on LocalStack:

```bash
docker exec -it payflow-localstack awslocal sns create-topic --name payment-notifications

docker exec -it payflow-localstack awslocal sqs create-queue --queue-name payment-notifications-queue

docker exec -it payflow-localstack awslocal sns subscribe \
  --topic-arn arn:aws:sns:us-east-1:000000000000:payment-notifications \
  --protocol sqs \
  --notification-endpoint arn:aws:sqs:us-east-1:000000000000:payment-notifications-queue
```

Run the application:

```bash
mvn spring-boot:run
```

## API

### Create payment

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{
    "payerId": "user-123",
    "payeeId": "merchant-456",
    "amount": 150.50,
    "currency": "BRL",
    "description": "Test payment"
  }'
```

### Get payment by ID

```bash
curl http://localhost:8080/api/v1/payments/{id}
```

### List all payments

```bash
curl http://localhost:8080/api/v1/payments
```

### Filter by status

```bash
curl http://localhost:8080/api/v1/payments?status=APPROVED
```

## Payment flow

```
POST /payments
      |
      v
  PENDING --> Kafka --> Consumer --> PROCESSING --> APPROVED
                                               --> FAILED
                                               
  On completion: SNS notification --> SQS queue
```

## Database

Flyway manages database migrations:

- `V1__create_payments.sql` - payments table
- `V2__create_payment_events.sql` - payment events audit table