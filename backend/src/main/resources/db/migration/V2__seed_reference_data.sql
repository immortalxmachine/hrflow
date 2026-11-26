-- =====================================================================
-- V2: Seed reference/configuration data.
--
-- NOTE: the leave entitlement numbers below are simplified, illustrative
-- approximations of each country's leave rules for demo purposes. They are NOT
-- legal advice and should not be treated as authoritative labour-law figures.
--
-- Employees are NOT seeded here; the DataSeeder loads them on startup from the
-- real public IBM HR Attrition dataset (1,470 rows; see README and
-- sample-data/ibm-hr-attrition/SOURCE.md), or they are imported via CSV.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Leave policies per country.
--   annual_entitlement | carry_forward_max | min_notice_days | max_consecutive_days
-- ---------------------------------------------------------------------
INSERT INTO leave_policies
    (country, leave_type, annual_entitlement, carry_forward_max, min_notice_days, max_consecutive_days) VALUES
    -- India: generous carry-forward of earned leave, 3 days notice.
    ('IN', 'ANNUAL', 18, 30, 3, 15),
    ('IN', 'SICK',   12,  0, 0, 10),
    ('IN', 'CASUAL', 12,  0, 1,  5),
    -- UAE: 30 calendar days annual (labour law), limited carry-forward.
    ('AE', 'ANNUAL', 30, 15, 7, 30),
    ('AE', 'SICK',   15,  0, 0, 15),
    ('AE', 'CASUAL',  5,  0, 1,  5),
    -- Germany: statutory minimum annual leave, carry-forward must be used early
    -- in the new year, longer notice is customary.
    ('DE', 'ANNUAL', 20, 10, 14, 20),
    ('DE', 'SICK',   30,  0,  0, 30),
    ('DE', 'CASUAL',  3,  0,  1,  3);

-- ---------------------------------------------------------------------
-- Public holidays for 2026 (a representative subset per country).
-- ---------------------------------------------------------------------
INSERT INTO public_holidays (country, holiday_date, name) VALUES
    -- India
    ('IN', '2026-01-26', 'Republic Day'),
    ('IN', '2026-08-15', 'Independence Day'),
    ('IN', '2026-10-02', 'Gandhi Jayanti'),
    ('IN', '2026-11-08', 'Diwali'),
    -- UAE
    ('AE', '2026-01-01', 'New Year''s Day'),
    ('AE', '2026-03-20', 'Eid al-Fitr (observed)'),
    ('AE', '2026-12-02', 'National Day'),
    ('AE', '2026-12-03', 'National Day Holiday'),
    -- Germany
    ('DE', '2026-01-01', 'Neujahr'),
    ('DE', '2026-04-03', 'Karfreitag'),
    ('DE', '2026-10-03', 'Tag der Deutschen Einheit'),
    ('DE', '2026-12-25', 'Erster Weihnachtstag');

-- ---------------------------------------------------------------------
-- Workflow definitions: the approval chains, stored as editable data.
-- ---------------------------------------------------------------------
INSERT INTO workflow_definitions (request_type, name, active) VALUES
    ('LEAVE',      'Standard Leave Approval',     TRUE),
    ('ONBOARDING', 'Employee Onboarding Review',  TRUE),
    ('EXPENSE',    'Expense Reimbursement Flow',  TRUE);

-- Steps for LEAVE: manager approves, then HR. Manager step escalates to HR
-- if it breaches its 48h SLA.
INSERT INTO workflow_steps (workflow_id, step_order, approver_role, sla_hours, escalation_role)
SELECT id, 1, 'MANAGER',  48, 'HR_ADMIN' FROM workflow_definitions WHERE request_type = 'LEAVE'
UNION ALL
SELECT id, 2, 'HR_ADMIN', 72, NULL       FROM workflow_definitions WHERE request_type = 'LEAVE';

-- Steps for ONBOARDING: HR sets up, system admin provisions access.
INSERT INTO workflow_steps (workflow_id, step_order, approver_role, sla_hours, escalation_role)
SELECT id, 1, 'HR_ADMIN',     72, 'SYSTEM_ADMIN' FROM workflow_definitions WHERE request_type = 'ONBOARDING'
UNION ALL
SELECT id, 2, 'SYSTEM_ADMIN', 72, NULL           FROM workflow_definitions WHERE request_type = 'ONBOARDING';

-- Steps for EXPENSE: manager -> HR (finance) -> system admin sign-off.
INSERT INTO workflow_steps (workflow_id, step_order, approver_role, sla_hours, escalation_role)
SELECT id, 1, 'MANAGER',      24, 'HR_ADMIN'     FROM workflow_definitions WHERE request_type = 'EXPENSE'
UNION ALL
SELECT id, 2, 'HR_ADMIN',     48, 'SYSTEM_ADMIN' FROM workflow_definitions WHERE request_type = 'EXPENSE'
UNION ALL
SELECT id, 3, 'SYSTEM_ADMIN', 72, NULL           FROM workflow_definitions WHERE request_type = 'EXPENSE';
