-- =====================================================================
-- V4: Real HR attributes on the employee record.
--
-- The startup seed now loads the public IBM HR Analytics Employee Attrition
-- dataset (Kaggle: pavansubhasht/ibm-hr-analytics-attrition-dataset, DbCL-1.0).
-- These columns hold the dataset's real per-row attributes so the seeded
-- population reflects the source's actual shape instead of fabricated values.
--
-- All columns are nullable: the CSV importer and test fixtures create
-- employees without them, and they carry no business logic (the workflow /
-- RBAC / leave engine does not read them).
-- =====================================================================
ALTER TABLE employees ADD COLUMN age            INT;
ALTER TABLE employees ADD COLUMN gender         VARCHAR(10);
ALTER TABLE employees ADD COLUMN job_role       VARCHAR(80);
ALTER TABLE employees ADD COLUMN job_level      INT;
ALTER TABLE employees ADD COLUMN monthly_income INT;
ALTER TABLE employees ADD COLUMN marital_status VARCHAR(20);
