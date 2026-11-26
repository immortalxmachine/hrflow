# Employee seed source — IBM HR Analytics Employee Attrition

HRFlow's startup seed (`backend/.../seed/DataSeeder.java`) builds its employee
population from a **real, public dataset**:

| | |
|---|---|
| **Dataset** | IBM HR Analytics Employee Attrition & Performance |
| **Kaggle slug** | [`pavansubhasht/ibm-hr-analytics-attrition-dataset`](https://www.kaggle.com/datasets/pavansubhasht/ibm-hr-analytics-attrition-dataset) |
| **File** | `WA_Fn-UseC_-HR-Employee-Attrition.csv` (1,470 rows) |
| **License** | Database Contents License (**DbCL-1.0**) |
| **Nature** | A fully anonymised teaching dataset created by IBM data scientists. It contains **no real individuals** and ships with **no names**. |

The CSV is committed on the backend classpath at
`backend/src/main/resources/data/WA_Fn-UseC_-HR-Employee-Attrition.csv` so the
seeder can load it in every environment (JAR, Docker, CI). This folder holds the
provenance note only.

## What is real vs. operational

**Real — taken verbatim from each dataset row:**

- `Department`, `JobRole`, `JobLevel`, `MonthlyIncome`, `Age`, `Gender`,
  `MaritalStatus` (persisted on the employee record; see `V4` migration).
- `EmployeeNumber` — drives the stable `employee_code` and the neutral
  `Employee NNNN` display label (the dataset has no names).

**Operational — assigned by the app because the dataset does not carry it
(this invents no personal attribute of a real person; it is the plumbing the
workflow engine runs on):**

- Login email on the reserved, clearly-non-real `.invalid` domain.
- Shared demo password (`Password123!`).
- HRFlow access **role**, derived from the dataset's own JobRole/Department
  (`JobRole = "Manager"` → MANAGER; HR-department HR staff → HR_ADMIN; everyone
  else → EMPLOYEE), plus one operational SYSTEM_ADMIN account.
- Leave-rule **country** (round-robined across IN/UAE/DE only to exercise the
  multi-jurisdiction leave engine — the dataset has no geography).
- Manager / HR-business-partner **wiring** (the dataset has no org chart).

**App-generated at runtime** — every approval, escalation, audit-log and
webhook-delivery record is produced by the engine as the app is used; none of it
is sourced data.

## Reproducing the download

```bash
export KAGGLE_API_TOKEN=$(cat ~/.kaggle/access_token)
kaggle datasets download -d pavansubhasht/ibm-hr-analytics-attrition-dataset -p .
unzip ibm-hr-analytics-attrition-dataset.zip
# -> WA_Fn-UseC_-HR-Employee-Attrition.csv
```

## Other files in `sample-data/`

- `employees_sample.csv` — a small, **real-derived** sample in HRFlow's CSV
  *import* format (neutral `Employee NNNN` labels, real departments/roles),
  for demoing the bulk importer.
- `employees_dirty.csv` — a hand-authored, **deliberately malformed** fixture
  (missing name, bad email, unknown country/role/manager) that demonstrates the
  importer's row-level validation. Its rows are validation inputs, not employee
  data.
