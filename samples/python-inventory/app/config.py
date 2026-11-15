"""Settings, read from environment variables once at start-up."""
import os

DATABASE_URL = os.environ["DATABASE_URL"]  # required: the service refuses to start without it
AMQP_URL = os.getenv("AMQP_URL", "amqp://localhost:5672/")
LOW_STOCK_THRESHOLD = int(os.getenv("LOW_STOCK_THRESHOLD", "5"))
EVENT_EXCHANGE = "inventory.events"
