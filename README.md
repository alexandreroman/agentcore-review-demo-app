# Order management API

A small Spring Boot service (customers, orders, order lines) backed by an
in-memory H2 database. It is the demo application whose pull requests are
reviewed by **Agentic Code Review with AgentCore x Temporal**: AI agents
orchestrated by Temporal and running as Serverless Workers on Amazon Bedrock
AgentCore.

## Run it

```bash
./mvnw spring-boot:run
```

The API listens on <http://localhost:8080> (set `PORT` to change it). Every
start fills the database with sample customers and orders:

```bash
curl http://localhost:8080/orders/1

curl -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId": 1, "lines": [{"product": "Notebook", "quantity": 2, "unitPriceCents": 450}]}'
```

## Test it

```bash
./mvnw test
```
