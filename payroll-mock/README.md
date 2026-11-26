# HRFlow Mock Payroll Service

A tiny FastAPI test double that receives HRFlow's signed `LEAVE_APPROVED`
webhook. It verifies the **HMAC-SHA256** signature and can inject failures so
the backend's retry logic can be exercised and measured.

> All data is **synthetic** and held in memory. This is a test double, not a
> real payroll system.

## Run

```bash
cd payroll-mock
python -m venv .venv
# Windows: .venv\Scripts\activate   |  macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt
# The secret must match the backend's HRFLOW_WEBHOOK_SECRET.
#   Windows PowerShell: $env:PAYROLL_WEBHOOK_SECRET="dev-webhook-secret"
uvicorn app:app --port 8001
```

## Endpoints

| Method | Path                       | Purpose                                             |
|--------|----------------------------|-----------------------------------------------------|
| GET    | `/health`                  | Liveness check.                                     |
| POST   | `/webhooks/leave-approved` | Receive a signed event (401 if signature invalid).  |
| GET    | `/received`                | Count + list of accepted events.                    |
| POST   | `/control/fail-next/{n}`   | Force the next `n` attempts to return 503.          |
| POST   | `/control/reset`           | Clear received events and failure counter.          |

## Signature scheme

The backend sends `X-HRFlow-Signature: <hex>` where

```
hex = HMAC_SHA256(key = PAYROLL_WEBHOOK_SECRET, message = raw_request_body)
```

The service recomputes this over the exact body bytes and compares in constant
time.

## Failure injection

- `PAYROLL_FAIL_RATE` (env, `0.0`–`1.0`): randomly reject that fraction with 503.
- `POST /control/fail-next/{n}`: deterministically fail the next `n` attempts,
  then succeed — used by `metrics.py` to measure "delivered after N failures".

## Test

```bash
pytest -q
```
