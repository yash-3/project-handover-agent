"""HTTP API (FastAPI)."""
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from app import inventory

app = FastAPI(title="Inventory Service")


class Quantity(BaseModel):
    quantity: int = Field(gt=0)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/stock/{sku}")
def stock(sku: str):
    try:
        return inventory.get_stock(sku)
    except inventory.UnknownSku:
        raise HTTPException(status_code=404, detail="unknown sku")


@app.post("/stock/{sku}/reserve")
def reserve(sku: str, body: Quantity):
    try:
        return inventory.reserve(sku, body.quantity)
    except inventory.UnknownSku:
        raise HTTPException(status_code=404, detail="unknown sku")
    except inventory.InsufficientStock as e:
        raise HTTPException(status_code=409, detail=str(e))


@app.post("/stock/{sku}/release")
def release(sku: str, body: Quantity):
    try:
        return inventory.release(sku, body.quantity)
    except inventory.UnknownSku:
        raise HTTPException(status_code=404, detail="unknown sku")
