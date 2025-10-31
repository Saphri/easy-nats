# Quickstart: Health Probe Stability

**Date**: 2025-10-31

## Overview

The health probe stability feature ensures that the application's startup probe provides a reliable, one-time transition from "DOWN" to "UP". This is crucial for applications running in containerized environments like Kubernetes, as it prevents the orchestrator from prematurely restarting the application due to transient issues that might occur after a successful startup.

## Behavior

Once the NATS connection is successfully established, the startup health probe (`/q/health/started`) will report an "UP" status. This status is then "latched" and will remain "UP" for the entire lifecycle of the application.

- **During startup**: The probe may report "DOWN".
- **After successful startup**: The probe will report "UP".
- **Post-startup**: The probe will always report "UP", regardless of any subsequent transient connection issues.

This ensures that Kubernetes (or a similar orchestrator) can confidently move from the startup probe to the liveness probe (`/q/health/live`) without the risk of a flapping startup probe causing unnecessary restarts.
