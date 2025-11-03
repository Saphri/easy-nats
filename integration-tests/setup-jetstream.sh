#!/bin/sh

# Wait for NATS server to be ready
echo "Waiting for NATS server to be ready..."
sleep 3

# Create test-stream
echo "Creating test-stream..."
nats stream add test-stream \
  --subjects "test.>" \
  --storage memory \
  --max-age 1h \
  --server "nats://guest:guest@nats:4222" \
  --defaults

# Create test-consumer
echo "Creating test-consumer..."
nats consumer add test-stream test-consumer \
  --filter "test.>" \
  --deliver all \
  --ack explicit \
  --wait 30s \
  --max-deliver 5 \
  --pull \
  --server "nats://guest:guest@nats:4222"

echo "Stream and consumer setup complete."
