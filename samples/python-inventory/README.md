# Inventory Service (sample)

A small Python service that tracks stock levels for an online shop. Other services call it to check
stock, reserve units for an order, and release a reservation when an order is cancelled.

This folder is a **sample project for the handover agent**: it is written in Python and has no CI
pipeline and no ticket tracker, so it shows the platform working from configuration alone.

## What it does

- `GET /stock/{sku}` returns the units on hand and the units reserved.
- `POST /stock/{sku}/reserve` reserves units for an order (409 if there is not enough stock).
- `POST /stock/{sku}/release` gives reserved units back.
- `GET /health` is used by the Kubernetes probes.
- When the remaining stock of a SKU falls to the low-stock threshold or below, it publishes a
  `stock.low` event; every successful reservation publishes `stock.reserved`.

## Run it locally

```bash
pip install -r requirements.txt
export DATABASE_URL=postgresql://inventory:secret@localhost:5432/inventory
uvicorn app.main:app --reload
pytest
```

## Configuration

| Variable | Meaning | Default |
|---|---|---|
| `DATABASE_URL` | PostgreSQL connection string | none, required |
| `AMQP_URL` | RabbitMQ connection string | `amqp://localhost:5672/` |
| `LOW_STOCK_THRESHOLD` | Remaining units at or below which `stock.low` is published | `5` |

Deployment is described in `deploy/` and the `Dockerfile`.
