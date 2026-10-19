# SC Peace SK Bank

Simulated banking operations console for a Java full-stack portfolio. Employees sign in, open customer and account records, and run deposits, withdrawals, and transfers. Nothing in this project is a real bank, and the bundled people, balances, and passwords are fictional.

## Clone onto another computer

Install Git, a JDK 17, and Node.js 22. Docker Desktop is optional and is the path that uses MySQL. Maven does not need to be installed; the first `mvnw` run downloads it.

```powershell
git clone https://github.com/<your-account>/banking-management-system.git
cd banking-management-system
copy .env.example .env
```

`.env` stays on that machine and is not part of the repository. The example file only contains the local simulation defaults. Then start the stack with Docker, or follow the local run steps below. On macOS or Linux, use `cp .env.example .env` and `./mvnw` instead of `.\mvnw.cmd`.

## What you can practice

- Spring Boot REST APIs, validation, and consistent error responses
- Spring Security with bcrypt passwords, JWT access tokens, rotating refresh tokens, and permission checks on the server
- MySQL relational design: foreign keys, checks, indexes, and `DECIMAL(19,2)` balances
- A transfer that debits one account and credits another in a single `@Transactional` unit of work
- React screens for customers, accounts, transactions, approvals, staff, and audit logs

## Roles

| Role | What they can do |
| --- | --- |
| Admin | Manage employees and settings, and perform every operation |
| Manager | Review records, freeze or activate accounts, and approve large operations |
| Teller | Create customers and accounts, and simulate deposits, withdrawals, and transfers |
| Auditor | Read customers, accounts, transactions, staff, and audit logs |

Permissions live in `roles`, `permissions`, and `role_permissions`. Hiding a button is not the control. An auditor who sends `POST /api/transactions/transfer` receives `403`.

Withdrawals and transfers at or above the approval threshold (default `10000.00`) stay pending. No balance changes until a different employee approves them. Funds are checked again at approval time; they are not reserved while the request is waiting.

## Run with Docker

Docker Compose starts MySQL, the API, and the console. The published ports bind to `127.0.0.1`. The database password fallback in `compose.yaml` is a development-only default. Copy `.env.example` to `.env` before you share a machine.

```powershell
docker compose up --build
```

Open [http://127.0.0.1:8088](http://127.0.0.1:8088). The API is at [http://127.0.0.1:8080](http://127.0.0.1:8080), and MySQL is at `127.0.0.1:3306`.

## Run the API and console locally

Start only the database:

```powershell
docker compose up db
```

In another terminal, from `backend`:

```powershell
.\mvnw.cmd spring-boot:run
```

From `frontend`:

```powershell
npm install
npm run dev
```

Open [http://127.0.0.1:3000](http://127.0.0.1:3000). Vite proxies `/api` to the API.

If Docker is not available, the API can boot with an in-memory database. That profile is only a local demo; the portfolio database is MySQL, and this data disappears when the process stops.

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

## Demo employees

These passwords are for the local simulation. Change them with the `SEED_*` environment variables before first startup if you want different ones.

| Username | Password | Role |
| --- | --- | --- |
| admin | `ChangeMe-Admin-1` | Admin |
| manager | `ChangeMe-Manager-1` | Manager |
| teller | `ChangeMe-Teller-1` | Teller |
| auditor | `ChangeMe-Auditor-1` | Auditor |

The sign-in screen can fill these in. The API still checks the password.

A failed sign-in is recorded. Five failures lock that employee for 15 minutes. Passwords are stored with bcrypt, and refresh tokens are stored as SHA-256 hashes.

## Try a transfer

1. Sign in as `teller`.
2. Open **Transactions** and move a small simulated amount between two active accounts. Both balances change together.
3. Submit a transfer of `10000.00` or more. The status stays `PENDING_APPROVAL` and neither balance changes.
4. Sign in as `manager` and approve it. The debit, credit, transaction row, and audit row commit together.
5. Sign in as `auditor`, open **Audit log**, and confirm the teller cannot post a transfer from the API.

Send `Idempotency-Key` when you retry a deposit, withdrawal, or transfer. The same key and same request return the original result. The same key with a different amount returns `409`.

## API

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/auth/login` | Sign in |
| POST | `/api/auth/refresh` | Rotate tokens |
| POST | `/api/auth/logout` | Revoke a refresh token |
| GET | `/api/customers` | Search customers by name or number |
| POST | `/api/customers` | Create a customer |
| GET | `/api/accounts/{id}` | View an account |
| POST | `/api/accounts` | Open a savings or checking account |
| POST | `/api/transactions/deposit` | Simulate a deposit |
| POST | `/api/transactions/withdraw` | Simulate a withdrawal |
| POST | `/api/transactions/transfer` | Simulate a transfer |
| POST | `/api/transactions/{id}/approve` | Approve a pending operation |
| GET | `/api/audit-logs` | Review activity |

`docs/api.http` has ready-to-send requests. Errors use one JSON shape: `code`, `message`, `path`, and `fieldErrors`.

## Data

Flyway creates the tables in `backend/src/main/resources/db/migration`. The important relationships are:

- A customer has many accounts.
- An employee has many roles, and a role has many permissions.
- An account appears on many transactions.
- An employee produces many audit records.

Balances use `DECIMAL(19,2)`. Account updates take a row lock in identifier order, so two transfers cannot deadlock by grabbing the accounts in opposite directions. The transfer method is one database transaction: if the credit fails, the debit is rolled back too.

Startup loads fictional customers in Exampleland when the customer table is empty. Registration dates, a frozen account, a closed account, and one transfer waiting for approval are included so the screens are not blank.

## Tests

```powershell
cd backend
.\mvnw.cmd verify
```

The tests use an in-memory database and cover authorization, balance checks, idempotency, approval, account closure, and a concurrent withdrawal.

## Project layout

```
backend/     Spring Boot API
frontend/    React operations console
compose.yaml MySQL, API, and console
```

This is a learning project. A production financial system would also need ledger reconciliation, holds, key management, network controls, and a much broader audit of operational risk.
