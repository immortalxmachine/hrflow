# HRFlow — Interview Notes

20 questions and answers tied to specific files and functions. All data is
synthetic. Everything below reflects code in this repository.

---

### 1. What problem does HRFlow solve?
It mirrors a Darwinbox-style HRMS core: configurable multi-level approval
workflows, role-based access control, an append-only audit trail, country-specific
leave rules, bulk employee import, and a signed payroll integration. See the
module map in [`README.md`](README.md).

### 2. Why are approval workflows "configurable" and not hard-coded?
Workflow chains are rows in `workflow_definitions` + `workflow_steps`
([`V1__core_schema.sql`](backend/src/main/resources/db/migration/V1__core_schema.sql)),
seeded in [`V2__seed_reference_data.sql`](backend/src/main/resources/db/migration/V2__seed_reference_data.sql),
and edited via `WorkflowService.create/update`
([`WorkflowService.java`](backend/src/main/java/com/hrflow/service/WorkflowService.java)).
Adding a level is an INSERT, not a redeploy.

### 3. How does a request know which workflow to follow?
`RequestService.activeWorkflow(type)` looks up the single active definition for
the request type via `WorkflowDefinitionRepository.findByRequestTypeAndActiveTrue`
([`RequestService.java`](backend/src/main/java/com/hrflow/service/RequestService.java)).
A partial unique index (`uq_active_workflow_per_type`) enforces one active
workflow per type at the database level.

### 4. How does a request advance through steps?
`ApprovalService.decide()` loads the current step, checks approver eligibility,
records the decision, and either advances `current_step_order`, or (on the last
step) sets status APPROVED and runs final-approval side effects
([`ApprovalService.java`](backend/src/main/java/com/hrflow/service/ApprovalService.java)).

### 5. How is RBAC enforced, and why at the service layer?
`@EnableMethodSecurity` ([`SecurityConfig.java`](backend/src/main/java/com/hrflow/config/SecurityConfig.java))
turns on `@PreAuthorize` on service methods (e.g. `WorkflowService.create`,
`AuditQueryService.search`). Enforcing in the service means the rule holds no
matter which controller calls it. Denials become 403 via the
`AccessDeniedException` handler in
[`GlobalExceptionHandler.java`](backend/src/main/java/com/hrflow/web/error/GlobalExceptionHandler.java).
Proven by `RbacIntegrationTest`.

### 6. How does JWT authentication work end to end?
`AuthService.login` authenticates via the `AuthenticationManager`
(BCrypt + `EmployeeUserDetailsService`) and `JwtService.issue` mints an HS256
token with the employee id and role as claims. On each request,
`JwtAuthenticationFilter` parses the token and rebuilds the `AuthenticatedUser`
principal — stateless, no session, no DB hit
([security package](backend/src/main/java/com/hrflow/security)).

### 7. Why put the role in the JWT claims?
So authorization is stateless: `JwtAuthenticationFilter` reconstructs authorities
(`ROLE_<role>`) from the token without a database lookup on every call. Trade-off:
a role change only takes effect on the next login (token lifetime is 120 min).

### 8. How is a manager stopped from approving another team's request?
`ApprovalService.isEligibleApprover` requires the approver's role to match the
step's role AND, for MANAGER steps, that they are the requester's direct manager
(`requester.getManager().getId().equals(user.getEmployeeId())`). Otherwise
`AccessDeniedException` → 403. Proven by
`ApprovalFlowIntegrationTest.managerOfAnotherTeamCannotApprove`.

### 9. What makes the audit trail trustworthy?
`AuditService.record` writes an `audit_log` row with before/after JSON for every
config change and decision ([`AuditService.java`](backend/src/main/java/com/hrflow/service/AuditService.java)).
The `AuditLog` entity has no setters and there is no update/delete endpoint —
append-only by construction. Reads go through `AuditQueryService.search`
(admin-only, paginated, filterable).

### 10. How does escalation work?
`EscalationService.escalateOverdue()`
([`EscalationService.java`](backend/src/main/java/com/hrflow/service/EscalationService.java))
scans pending, not-yet-escalated requests; if the current step has been waiting
longer than its `sla_hours` and defines an `escalation_role`, it flips
`escalated=true`. While escalated, `isEligibleApprover` uses the step's
`escalationRole` instead of its normal role, so a higher authority can act.
Proven by `EscalationIntegrationTest`.

### 11. Why store escalation on the request, not the workflow?
The workflow definition is shared by all requests. Escalating one request must
not change the template, so `escalated`/`escalated_at` live on the `requests`
row ([`V3__request_escalation.sql`](backend/src/main/resources/db/migration/V3__request_escalation.sql)).

### 12. How are country-specific leave rules modelled?
`leave_policies` holds per-country entitlement, carry-forward, notice and
max-consecutive numbers; `public_holidays` holds per-country calendars. The pure
`LeaveRuleEngine.validate()`
([`LeaveRuleEngine.java`](backend/src/main/java/com/hrflow/rules/LeaveRuleEngine.java))
applies them. It has no Spring/DB dependencies so each country is unit-tested as
a pure function (`LeaveRuleEngineTest`).

### 13. How are working days counted?
`LeaveRuleEngine.countWorkingDays` iterates the date range, skipping Saturdays,
Sundays and the supplied public holidays. (Documented simplification: weekend =
Sat/Sun for all three countries; real differences come from policies + holiday
calendars.)

### 14. Where is leave balance deducted, and why there?
In `ApprovalService.onFinalApproval` — only after the LAST approval, so a
half-approved request never consumes balance. It reads `workingDays` from the
request payload and increments `leave_balances.used_days`.

### 15. Why is the leave payload stored as JSONB?
The engine is generic across request types; type-specific fields (leave type,
dates, working days) live in `requests.payload` (JSONB, mapped via Hibernate
`@JdbcTypeCode(SqlTypes.JSON)` in [`Request.java`](backend/src/main/java/com/hrflow/domain/Request.java)).
This keeps the `requests` table and the engine uniform for leave/onboarding/expense.

### 16. How does the CSV import avoid silent data loss?
`ImportService.importCsv`
([`ImportService.java`](backend/src/main/java/com/hrflow/service/ImportService.java))
validates each row and writes every rejected row to `import_errors` with a
reason; valid rows are imported. Nothing is dropped silently. Proven by
`ImportIntegrationTest` with a deliberately dirty CSV.

### 17. What does "idempotent on employee_code" mean here?
The importer upserts by `employee_code`: if an employee with that code exists it
updates, otherwise it inserts. Re-running the same file produces no duplicates
(`ImportIntegrationTest.reimportIsIdempotentOnEmployeeCode`).

### 18. How is the payroll webhook secured and made reliable?
`WebhookService.sendLeaveApproved`
([`WebhookService.java`](backend/src/main/java/com/hrflow/service/WebhookService.java))
signs the exact JSON body with HMAC-SHA256 (`X-HRFlow-Signature`), retries up to
`maxAttempts`, and records every attempt + final status in `webhook_deliveries`.
The mock verifies the signature in constant time
([`payroll-mock/app.py`](payroll-mock/app.py)). A payroll outage never rolls back
an approval (errors are swallowed and logged as FAILED).
It sends via the JDK `HttpClient` pinned to **HTTP/1.1** and `BodyPublishers.ofString`,
so the bytes on the wire are exactly the bytes that were signed — found the hard
way: the live test (`WebhookDeliveryLiveTest`) initially failed because the client
defaulted to HTTP/2, whose h2c upgrade the HTTP/1.1-only mock rejected with 400.

### 19. Why is calling the webhook synchronously inside the transaction a known trade-off?
It is simple and gives real delivery metrics, but holds a DB connection during an
external call. In production I'd use the transactional-outbox pattern: commit a
PENDING `webhook_deliveries` row, then deliver asynchronously (the schema and
`redeliver()` method already support this).

### 20. How do tests run without Docker or a local Postgres?
`AbstractPostgresTest`
([`AbstractPostgresTest.java`](backend/src/test/java/com/hrflow/support/AbstractPostgresTest.java))
starts a real embedded PostgreSQL (zonky; binary auto-downloaded) and feeds its
JDBC URL to Spring via `@DynamicPropertySource`. Flyway runs the production
migrations and Hibernate validates the mapping — same SQL as production, no
Docker. CI (`.github/workflows/ci.yml`) runs the same suite on Linux.
