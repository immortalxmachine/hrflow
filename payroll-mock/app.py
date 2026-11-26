"""
Mock payroll service for HRFlow.

Receives the signed "leave approved" webhook from the HRFlow backend, verifies
the HMAC-SHA256 signature, and records the event. It can inject failures so the
backend's retry logic can be exercised and measured:

  * PAYROLL_FAIL_RATE (env, 0.0-1.0): randomly reject this fraction of requests
    with 503, simulating flaky downstream availability.
  * POST /control/fail-next/{n}: deterministically fail the next n delivery
    attempts with 503, then succeed -- used to measure "delivered after N
    injected failures".

All data here is synthetic and held in memory; this is a test double, not a
real payroll system.
"""
import hashlib
import hmac
import os
import random
from typing import Any

from fastapi import FastAPI, Header, Request, Response

# Shared secret; MUST match the backend's HRFLOW_WEBHOOK_SECRET.
WEBHOOK_SECRET = os.environ.get("PAYROLL_WEBHOOK_SECRET", "dev-webhook-secret")
# Fraction of requests to randomly fail (chaos testing).
FAIL_RATE = float(os.environ.get("PAYROLL_FAIL_RATE", "0.0"))

app = FastAPI(title="HRFlow Mock Payroll", version="1.0.0")

# In-memory state (synthetic).
received: list[dict[str, Any]] = []
_fail_next = {"count": 0}


def _expected_signature(body: bytes) -> str:
    """Hex HMAC-SHA256 of the raw request body under the shared secret."""
    return hmac.new(WEBHOOK_SECRET.encode("utf-8"), body, hashlib.sha256).hexdigest()


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.get("/received")
def list_received() -> dict[str, Any]:
    """All accepted events plus a count (used by the metrics script)."""
    return {"count": len(received), "events": received}


@app.post("/control/fail-next/{n}")
def fail_next(n: int) -> dict[str, int]:
    """Make the next n delivery attempts fail with 503, then succeed."""
    _fail_next["count"] = n
    return {"failNext": n}


@app.post("/control/reset")
def reset() -> dict[str, str]:
    received.clear()
    _fail_next["count"] = 0
    return {"status": "reset"}


@app.post("/webhooks/leave-approved")
async def leave_approved(
    request: Request,
    x_hrflow_signature: str = Header(default=""),
    x_hrflow_event: str = Header(default=""),
) -> Response:
    body = await request.body()

    # 1. Verify authenticity: recompute the HMAC and compare in constant time.
    expected = _expected_signature(body)
    if not hmac.compare_digest(expected, x_hrflow_signature):
        return Response(content='{"error":"invalid signature"}',
                        status_code=401, media_type="application/json")

    # 2. Injected failures (deterministic counter first, then random rate).
    if _fail_next["count"] > 0:
        _fail_next["count"] -= 1
        return Response(content='{"error":"injected failure"}',
                        status_code=503, media_type="application/json")
    if FAIL_RATE > 0 and random.random() < FAIL_RATE:
        return Response(content='{"error":"random failure"}',
                        status_code=503, media_type="application/json")

    # 3. Accept and record the event.
    payload = await request.json()
    received.append({"event": x_hrflow_event, "payload": payload})
    return Response(content='{"status":"accepted"}',
                    status_code=200, media_type="application/json")
