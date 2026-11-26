"""Tests for the mock payroll webhook receiver."""
import hashlib
import hmac
import json

from fastapi.testclient import TestClient

import app as payroll

client = TestClient(payroll.app)
SECRET = payroll.WEBHOOK_SECRET


def sign(body: bytes) -> str:
    return hmac.new(SECRET.encode(), body, hashlib.sha256).hexdigest()


def post_event(signature: str):
    body = json.dumps({"event": "LEAVE_APPROVED", "requestId": 1}).encode()
    return client.post(
        "/webhooks/leave-approved",
        content=body,
        headers={"X-HRFlow-Signature": signature, "X-HRFlow-Event": "LEAVE_APPROVED",
                 "Content-Type": "application/json"},
    )


def setup_function(_):
    client.post("/control/reset")


def test_health():
    assert client.get("/health").json() == {"status": "UP"}


def test_valid_signature_is_accepted_and_recorded():
    body = json.dumps({"event": "LEAVE_APPROVED", "requestId": 1}).encode()
    resp = post_event(sign(body))
    assert resp.status_code == 200
    assert client.get("/received").json()["count"] == 1


def test_invalid_signature_is_rejected():
    resp = post_event("deadbeef")
    assert resp.status_code == 401
    assert client.get("/received").json()["count"] == 0


def test_injected_failure_then_success():
    body = json.dumps({"event": "LEAVE_APPROVED", "requestId": 1}).encode()
    client.post("/control/fail-next/1")
    # First attempt is forced to fail with 503...
    assert post_event(sign(body)).status_code == 503
    # ...the retry then succeeds.
    assert post_event(sign(body)).status_code == 200
    assert client.get("/received").json()["count"] == 1
