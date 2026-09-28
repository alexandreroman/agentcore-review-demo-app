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

curl 'http://localhost:8080/customers/search?q=ada&page=0&size=20'

curl 'http://localhost:8080/customers/1/orders?page=0&size=20'
```

Both customer endpoints are paged: `page` defaults to `0` and must not be
negative, `size` defaults to `20` and must be between `1` and `100`. Values
outside those ranges are rejected with `400 Bad Request`. `q` is required and
matches the start of a customer name; it is trimmed and must contain between 3
and 100 characters, so blank, short and oversized searches are rejected. Email
addresses are neither returned nor matched, so the search cannot confirm that a
guessed address belongs to a customer. Searches are limited to the first 1000
results (`page * size` must stay below `1000`) and each caller may run at most
20 searches per minute; further searches are rejected with `429 Too Many
Requests`.

A caller is identified by the principal the gateway propagates, or by its
network address (IPv6 callers are counted per `/64`) when there is none. The
address is taken from the forwarding headers of the proxy
(`server.forward-headers-strategy=framework`), so the gateway has to strip
client-supplied `X-Forwarded-*`/`Forwarded` headers; otherwise callers can pick
their own key. The counters are kept in the memory of one instance, so a
horizontally scaled deployment multiplies the effective limit by the number of
instances and should enforce the limit at the gateway (or share the counters)
instead.

Those limits only make bulk harvesting of the customer list inconvenient, they
are not access control: the demo service has no authentication at all, so put it
behind an authenticating gateway before exposing it to untrusted callers.

## Test it

```bash
./mvnw test
```

## Demo references

| Reference | Role |
|---|---|
| `baseline` (tag) | State of `main` at the start of every demo |
| `scenario/customer-search` (tag) | The "Add customer search & order history" change |
| `feature/customer-search` (branch) | Opened as a pull request during the demo |
| `dev/customer-search` (branch) | Same change, reviewed by a local development worker |

The **Reset demo** workflow (Actions tab, "Run workflow") closes the open
pull requests, moves `main` back to `baseline` and recreates both branches
from the scenario tag. Tags never move.
