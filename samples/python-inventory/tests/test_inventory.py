import pytest

from app import inventory


def test_unknown_sku_is_rejected(monkeypatch):
    monkeypatch.setattr(inventory.db, "transaction", lambda: (_ for _ in ()).throw(inventory.UnknownSku("x")))
    with pytest.raises(inventory.UnknownSku):
        inventory.reserve("x", 1)
