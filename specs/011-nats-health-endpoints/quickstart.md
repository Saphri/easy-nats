# Quickstart: NATS Health Check Endpoints

The NATS health check endpoints are automatically enabled when you include the `quarkus-easynats` extension in your project. There is no additional configuration required.

## Verifying Health

You can check the status of the NATS connection by accessing the following endpoints:

-   **Liveness**: `GET /q/health/live`
-   **Readiness**: `GET /q/health/ready`
-   **Startup**: `GET /q/health/started`

### Healthy Response

When the NATS connection is established, the endpoints will return an HTTP 200 OK status with a response body similar to this:

```json
{
    "status": "UP",
    "checks": [
        {
            "name": "NATS Connection",
            "status": "UP",
            "data": {
                "connectionStatus": "CONNECTED"
            }
        }
    ]
}
```

### Unhealthy Response (Readiness)

If the NATS connection is temporarily lost or in a lame duck state, the readiness probe will return an HTTP 503 Service Unavailable status:

```json
{
    "status": "DOWN",
    "checks": [
        {
            "name": "NATS Connection",
            "status": "DOWN",
            "data": {
                "connectionStatus": "RECONNECTING"
            }
        }
    ]
}
```

### Unhealthy Response (Liveness)

The liveness probe will only return an HTTP 503 Service Unavailable status if the connection is permanently closed:

```json
{
    "status": "DOWN",
    "checks": [
        {
            "name": "NATS Connection",
            "status": "DOWN",
            "data": {
                "connectionStatus": "CLOSED"
            }
        }
    ]
}
```