-- =====================================================================
-- V1: Core schema for HRFlow.
--
-- Design notes:
--  * Enums are modelled as VARCHAR + CHECK constraints rather than native
--    Postgres ENUM types. This keeps them easy to extend via migration and
--    maps cleanly to Java enums without custom Hibernate types.
--  * Workflow definitions and steps are DATA, not code: an admin can add a
--    new approval flow by inserting rows, no redeploy needed.
--  * audit_log is append-only. There are deliberately no UPDATE/DELETE paths
--    for it anywhere in the application.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Employees (also the principal/identity table for authentication).
-- ---------------------------------------------------------------------
CREATE TABLE employees (
    id              BIGSERIAL PRIMARY KEY,
    employee_code   VARCHAR(32)  NOT NULL UNIQUE,          -- stable external id, import key
    full_name       VARCHAR(160) NOT NULL,
    email           VARCHAR(190) NOT NULL UNIQUE,
    country         VARCHAR(2)   NOT NULL,                 -- ISO-3166 alpha-2: IN, AE, DE
    role            VARCHAR(20)  NOT NULL,                 -- EMPLOYEE | MANAGER | HR_ADMIN | SYSTEM_ADMIN
    department      VARCHAR(80),
    manager_id      BIGINT       REFERENCES employees(id), -- direct manager (approval level 1)
    hrbp_id         BIGINT       REFERENCES employees(id), -- HR business partner (approval level 2)
    password_hash   VARCHAR(100) NOT NULL,                 -- bcrypt hash
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT employees_role_chk
        CHECK (role IN ('EMPLOYEE','MANAGER','HR_ADMIN','SYSTEM_ADMIN')),
    CONSTRAINT employees_country_chk
        CHECK (country IN ('IN','AE','DE'))
);
CREATE INDEX idx_employees_manager ON employees(manager_id);
CREATE INDEX idx_employees_country ON employees(country);

-- ---------------------------------------------------------------------
-- Leave policies: country-specific entitlement rules (seed data).
-- ---------------------------------------------------------------------
CREATE TABLE leave_policies (
    id                    BIGSERIAL PRIMARY KEY,
    country               VARCHAR(2)  NOT NULL,
    leave_type            VARCHAR(20) NOT NULL,            -- ANNUAL | SICK | CASUAL
    annual_entitlement    INT         NOT NULL,            -- days granted per year
    carry_forward_max     INT         NOT NULL DEFAULT 0,  -- max days carried to next year
    min_notice_days       INT         NOT NULL DEFAULT 0,  -- advance notice required
    max_consecutive_days  INT         NOT NULL DEFAULT 365,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT leave_policies_type_chk
        CHECK (leave_type IN ('ANNUAL','SICK','CASUAL')),
    CONSTRAINT leave_policies_country_chk
        CHECK (country IN ('IN','AE','DE')),
    CONSTRAINT leave_policies_uq UNIQUE (country, leave_type)
);

-- ---------------------------------------------------------------------
-- Public holidays per country (seed data). Working-day calculations skip
-- these dates and weekends.
-- ---------------------------------------------------------------------
CREATE TABLE public_holidays (
    id           BIGSERIAL PRIMARY KEY,
    country      VARCHAR(2)  NOT NULL,
    holiday_date DATE        NOT NULL,
    name         VARCHAR(120) NOT NULL,
    CONSTRAINT public_holidays_country_chk CHECK (country IN ('IN','AE','DE')),
    CONSTRAINT public_holidays_uq UNIQUE (country, holiday_date)
);
CREATE INDEX idx_holidays_country ON public_holidays(country);

-- ---------------------------------------------------------------------
-- Leave balances: per employee / leave type / year.
-- ---------------------------------------------------------------------
CREATE TABLE leave_balances (
    id             BIGSERIAL PRIMARY KEY,
    employee_id    BIGINT      NOT NULL REFERENCES employees(id),
    leave_type     VARCHAR(20) NOT NULL,
    year           INT         NOT NULL,
    entitled_days  NUMERIC(5,1) NOT NULL,
    used_days      NUMERIC(5,1) NOT NULL DEFAULT 0,
    carried_days   NUMERIC(5,1) NOT NULL DEFAULT 0,
    CONSTRAINT leave_balances_type_chk CHECK (leave_type IN ('ANNUAL','SICK','CASUAL')),
    CONSTRAINT leave_balances_uq UNIQUE (employee_id, leave_type, year)
);

-- ---------------------------------------------------------------------
-- Workflow definitions + steps. These rows ARE the approval configuration.
-- ---------------------------------------------------------------------
CREATE TABLE workflow_definitions (
    id           BIGSERIAL PRIMARY KEY,
    request_type VARCHAR(20) NOT NULL,                     -- LEAVE | ONBOARDING | EXPENSE
    name         VARCHAR(120) NOT NULL,
    active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT workflow_def_type_chk
        CHECK (request_type IN ('LEAVE','ONBOARDING','EXPENSE'))
);
-- At most one active definition per request type (partial unique index).
CREATE UNIQUE INDEX uq_active_workflow_per_type
    ON workflow_definitions(request_type) WHERE active;

CREATE TABLE workflow_steps (
    id              BIGSERIAL PRIMARY KEY,
    workflow_id     BIGINT      NOT NULL REFERENCES workflow_definitions(id) ON DELETE CASCADE,
    step_order      INT         NOT NULL,                  -- 1-based position in the chain
    approver_role   VARCHAR(20) NOT NULL,                  -- role that must approve this step
    sla_hours       INT         NOT NULL DEFAULT 48,       -- time allowed before it is "overdue"
    escalation_role VARCHAR(20),                           -- who to escalate to when overdue (nullable)
    CONSTRAINT workflow_steps_role_chk
        CHECK (approver_role IN ('MANAGER','HR_ADMIN','SYSTEM_ADMIN')),
    CONSTRAINT workflow_steps_escal_chk
        CHECK (escalation_role IS NULL OR escalation_role IN ('MANAGER','HR_ADMIN','SYSTEM_ADMIN')),
    CONSTRAINT workflow_steps_uq UNIQUE (workflow_id, step_order)
);
CREATE INDEX idx_steps_workflow ON workflow_steps(workflow_id);

-- ---------------------------------------------------------------------
-- Requests moving through a workflow (leave / onboarding / expense).
-- Request-type-specific fields live in the JSONB payload so the engine
-- stays generic.
-- ---------------------------------------------------------------------
CREATE TABLE requests (
    id                 BIGSERIAL PRIMARY KEY,
    request_type       VARCHAR(20) NOT NULL,
    requester_id       BIGINT      NOT NULL REFERENCES employees(id),
    workflow_id        BIGINT      NOT NULL REFERENCES workflow_definitions(id),
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | APPROVED | REJECTED | CANCELLED
    current_step_order INT         NOT NULL DEFAULT 1,          -- which step is awaiting action
    payload            JSONB       NOT NULL,                    -- type-specific fields
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT requests_type_chk  CHECK (request_type IN ('LEAVE','ONBOARDING','EXPENSE')),
    CONSTRAINT requests_status_chk CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED'))
);
CREATE INDEX idx_requests_requester ON requests(requester_id);
CREATE INDEX idx_requests_status    ON requests(status);

-- ---------------------------------------------------------------------
-- Approval actions: one row per decision taken on a request's step.
-- Append-only operational history (distinct from the system-wide audit_log).
-- ---------------------------------------------------------------------
CREATE TABLE approval_actions (
    id           BIGSERIAL PRIMARY KEY,
    request_id   BIGINT      NOT NULL REFERENCES requests(id),
    step_order   INT         NOT NULL,
    approver_id  BIGINT      NOT NULL REFERENCES employees(id),
    decision     VARCHAR(20) NOT NULL,                    -- APPROVED | REJECTED
    comment      VARCHAR(500),
    acted_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT approval_actions_decision_chk CHECK (decision IN ('APPROVED','REJECTED'))
);
CREATE INDEX idx_actions_request ON approval_actions(request_id);

-- ---------------------------------------------------------------------
-- Audit log: append-only. Records who did what, with before/after JSON.
-- No endpoint ever updates or deletes these rows.
-- ---------------------------------------------------------------------
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    BIGINT      REFERENCES employees(id),      -- nullable: SYSTEM actions have no human
    actor_code  VARCHAR(32),                               -- denormalised code/"SYSTEM" for readability
    action      VARCHAR(60) NOT NULL,                      -- e.g. WORKFLOW_UPDATED, REQUEST_APPROVED
    entity_type VARCHAR(40) NOT NULL,                      -- e.g. workflow_definition, request
    entity_id   BIGINT,
    before_json JSONB,                                     -- null for creates
    after_json  JSONB,                                     -- null for deletes
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_action     ON audit_log(action);
CREATE INDEX idx_audit_entity     ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_created_at ON audit_log(created_at);

-- ---------------------------------------------------------------------
-- CSV import tracking + row-level error report (no silent drops).
-- ---------------------------------------------------------------------
CREATE TABLE import_batches (
    id            BIGSERIAL PRIMARY KEY,
    filename      VARCHAR(255) NOT NULL,
    total_rows    INT         NOT NULL DEFAULT 0,
    imported_rows INT         NOT NULL DEFAULT 0,
    failed_rows   INT         NOT NULL DEFAULT 0,
    created_by    VARCHAR(32),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE import_errors (
    id         BIGSERIAL PRIMARY KEY,
    batch_id   BIGINT      NOT NULL REFERENCES import_batches(id) ON DELETE CASCADE,
    row_number INT         NOT NULL,                       -- 1-based data row (excludes header)
    raw_row    TEXT        NOT NULL,                       -- original CSV line, for debugging
    reason     VARCHAR(300) NOT NULL,                      -- why the row was rejected
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_import_errors_batch ON import_errors(batch_id);

-- ---------------------------------------------------------------------
-- Webhook delivery log: every outbound payroll notification + retries.
-- ---------------------------------------------------------------------
CREATE TABLE webhook_deliveries (
    id                 BIGSERIAL PRIMARY KEY,
    request_id         BIGINT      REFERENCES requests(id),
    event_type         VARCHAR(40) NOT NULL,               -- e.g. LEAVE_APPROVED
    target_url         VARCHAR(300) NOT NULL,
    payload_json       JSONB       NOT NULL,
    signature          VARCHAR(100) NOT NULL,              -- HMAC-SHA256 hex of the payload
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | SUCCESS | FAILED
    attempt_count      INT         NOT NULL DEFAULT 0,
    last_response_code INT,
    last_error         VARCHAR(300),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT webhook_status_chk CHECK (status IN ('PENDING','SUCCESS','FAILED'))
);
CREATE INDEX idx_webhook_status ON webhook_deliveries(status);
