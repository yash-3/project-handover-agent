"""PostgreSQL access through a psycopg connection pool."""
from contextlib import contextmanager

from psycopg_pool import ConnectionPool

from app import config

pool = ConnectionPool(config.DATABASE_URL, min_size=1, max_size=10)


@contextmanager
def transaction():
    """One database transaction; commits on success and rolls back on any error."""
    with pool.connection() as conn:
        yield conn


def lock_stock_row(conn, sku):
    """Read a SKU's stock row and lock it until the transaction ends.

    SELECT ... FOR UPDATE makes concurrent reservations of the same SKU wait for each other,
    so two orders can never both take the last unit.
    """
    return conn.execute(
        "SELECT on_hand, reserved FROM stock WHERE sku = %s FOR UPDATE", (sku,)
    ).fetchone()


def update_reserved(conn, sku, reserved):
    conn.execute("UPDATE stock SET reserved = %s WHERE sku = %s", (reserved, sku))
