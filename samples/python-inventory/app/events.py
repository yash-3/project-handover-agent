"""Publishes domain events to RabbitMQ (topic exchange, one routing key per event type)."""
import json

import pika

from app import config


def publish(event_type, payload):
    """Send an event such as stock.low or stock.reserved. Failures are raised to the caller."""
    connection = pika.BlockingConnection(pika.URLParameters(config.AMQP_URL))
    try:
        channel = connection.channel()
        channel.exchange_declare(exchange=config.EVENT_EXCHANGE, exchange_type="topic", durable=True)
        channel.basic_publish(
            exchange=config.EVENT_EXCHANGE,
            routing_key=event_type,
            body=json.dumps(payload),
        )
    finally:
        connection.close()
