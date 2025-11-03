#!/bin/sh

# Wait for NATS server to be ready
echo "Waiting for NATS server to be ready..."
sleep 3

# Create test-stream
echo "Creating test-stream..."
nats stream add test-stream \
  --subjects "test.>" \
  --server "nats://${NATS_USERNAME}:${NATS_PASSWORD}@nats:4222" \
  --defaults

# Create test-consumer
echo "Creating test-consumer..."
nats consumer add test-stream test-consumer \
  --filter "test.>" \
  --pull \
  --server "nats://${NATS_USERNAME}:${NATS_PASSWORD}@nats:4222" \
  --defaults

echo "Stream and consumer setup complete."
