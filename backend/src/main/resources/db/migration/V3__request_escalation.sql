-- =====================================================================
-- V3: Per-request escalation state.
--
-- When a step breaches its SLA, an escalation sweep flips `escalated` to true
-- and stamps `escalated_at`. While escalated, the current step may be approved
-- by the step's escalation_role instead of its normal approver_role. Escalation
-- is tracked per-request so it never mutates the shared workflow definition.
-- =====================================================================
ALTER TABLE requests
    ADD COLUMN escalated    BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN escalated_at TIMESTAMPTZ;
