# Order management API

A small FastAPI and SQLAlchemy service (customers, orders, order lines) on
SQLite. It is the demo application whose pull requests are reviewed by
**Agentic Code Review with AgentCore x Temporal**: AI agents orchestrated by
Temporal and running as Serverless Workers on Amazon Bedrock AgentCore.

## Run it

```bash
uv sync
uv run uvicorn app.main:app --reload
```

The first start creates `orders.db` with sample data. The interactive API
documentation is at <http://127.0.0.1:8000/docs>.

## Test it

```bash
uv run pytest
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
