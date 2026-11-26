# HRFlow — Resume Bullets

Real numbers only; each ≤ 25 words. (Pick 3–4.)

- Built HRFlow, a Spring Boot 3 HRMS engine with data-driven multi-level approval workflows across 3 request types, JWT auth and service-layer RBAC over 16 REST endpoints.

- Implemented an append-only audit trail and a per-country leave rule engine (India/UAE/Germany); covered with 27 Java tests on a real embedded Postgres plus 4 Python tests.

- Delivered HMAC-SHA256 signed payroll webhooks with retries and a delivery log; verified recovery on the 3rd attempt after 2 injected failures against a FastAPI mock.

- Wrote an idempotent CSV employee importer with row-level validation: a dirty 8-row file imported 3 and rejected 5 with reasons, no silent drops.

- Seeded the 1,470-employee demo population from the real public IBM HR Attrition dataset (Kaggle, DbCL-1.0), mapping real department/job-role/level/income with zero fabricated personal data.
