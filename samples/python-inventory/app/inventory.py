"""Business rules for stock reservations."""
from app import config, db, events


class UnknownSku(Exception):
    pass


class InsufficientStock(Exception):
    def __init__(self, sku, requested, available):
        super().__init__(f"{sku}: requested {requested}, only {available} available")


def get_stock(sku):
    with db.transaction() as conn:
        row = conn.execute("SELECT on_hand, reserved FROM stock WHERE sku = %s", (sku,)).fetchone()
    if row is None:
        raise UnknownSku(sku)
    return {"sku": sku, "on_hand": row[0], "reserved": row[1]}


def reserve(sku, quantity):
    """Reserve units for an order; raises InsufficientStock if fewer than `quantity` are free."""
    with db.transaction() as conn:
        row = db.lock_stock_row(conn, sku)
        if row is None:
            raise UnknownSku(sku)
        on_hand, reserved = row
        available = on_hand - reserved
        if quantity > available:
            raise InsufficientStock(sku, quantity, available)
        db.update_reserved(conn, sku, reserved + quantity)
    remaining = available - quantity
    events.publish("stock.reserved", {"sku": sku, "quantity": quantity})
    if remaining <= config.LOW_STOCK_THRESHOLD:
        events.publish("stock.low", {"sku": sku, "remaining": remaining})
    return {"sku": sku, "reserved": quantity, "remaining": remaining}


def release(sku, quantity):
    """Give reserved units back, for example when an order is cancelled."""
    with db.transaction() as conn:
        row = db.lock_stock_row(conn, sku)
        if row is None:
            raise UnknownSku(sku)
        db.update_reserved(conn, sku, max(0, row[1] - quantity))
    return {"sku": sku, "released": quantity}
