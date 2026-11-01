# FinCore

**A digital wallet and payments REST API built like a small core-banking backend.** Users hold wallets, deposit money and send peer-to-peer transfers. The engineering focus is that **money is never lost, duplicated or double-spent**, even when many requests hit the same wallet at once, and that every endpoint is properly secured.

[![CI](https://github.com/piyush-nfx/fincore/actions/workflows/ci.yml/badge.svg)](https://github.com/piyush-nfx/fincore/actions/workflows/ci.yml)

**Live API docs (Swagger UI):** _add your deployed URL here, e.g. `https://<your-app>.up.railway.app/swagger-ui.html`_

**Stack:** Java 25 · Spring Boot 4.1 · Spring Security 7 + JWT · Spring Data JPA / Hibernate 7 · MySQL 8 · Flyway · springdoc-openapi · JUnit 5, Mockito, Testcontainers · Docker · GitHub Actions

---

## Highlights

- **Atomic transfers.** Debit, credit and the ledger entry happen in one database transaction: all of it commits, or none of it does.
- **No double spending under concurrency.** Wallets use optimistic locking (`@Version`). Requests that lose a race (version conflict or MySQL deadlock) are retried automatically in a fresh transaction, with exponential backoff and jitter. An integration test fires 10 simultaneous transfers at one wallet and proves the balance can never go negative.
- **Idempotent payments.** Every transfer carries an `Idempotency-Key`. A retried request returns the original result instead of paying twice, even when the duplicates arrive at the same instant. Keys are unique per user, enforced by the database.
- **Immutable ledger.** Every deposit and transfer, including failed attempts, is recorded and never modified. Balances are `BigDecimal` / `DECIMAL(19,4)`, never floating point.
- **Security.** BCrypt passwords, short-lived JWT access tokens and rotating refresh tokens with reuse detection, ownership checks on every wallet (IDOR-safe: other people's wallets return 404), an ADMIN role, and no user enumeration through login errors or timing.
- **Production basics.** Versioned schema migrations (Flyway), consistent JSON errors, paginated history, OpenAPI docs, a non-root Docker image, and CI that runs the full test suite against a real MySQL.

## Architecture

```mermaid
flowchart LR
    C[Client<br/>Postman / Swagger UI] -->|HTTPS + JSON| F
    subgraph App[Spring Boot application]
        F[JWT filter<br/>Spring Security] --> CT[Controllers<br/>validation, status codes]
        CT --> FA[TransferFacade<br/>retry on conflicts]
        FA --> S[Services<br/>@Transactional business rules]
        CT --> S
        S --> R[Repositories<br/>Spring Data JPA]
    end
    R -->|SQL| DB[(MySQL<br/>users · wallets ·<br/>transactions · refresh_tokens)]
    M[Flyway migrations] -.->|schema| DB
```

```
com.piyush.fincore
├── controller   REST endpoints (thin: HTTP in, DTOs out)
├── service      business rules, transactions, retry, idempotency
├── repository   Spring Data JPA interfaces and JPQL queries
├── entity       User, Wallet, Transaction (ledger), RefreshToken
├── dto          request/response records (entities never leave the service layer)
├── security     JWT service + filter, security config, admin bootstrap
├── config       OpenAPI / Swagger configuration
└── exception    domain exceptions + one global JSON error handler
```

## From v1 to v2

FinCore started as **CryptoVault**, a command-line Java wallet simulator that kept its ledger in an `ArrayList` and saved it to a text file (kept in this repo as the tag [`v1-cli`](https://github.com/piyush-nfx/fincore/tree/v1-cli)). That design couldn't handle concurrent users, crash safety or authentication, so v2 rebuilds it as a REST API on a transactional database:

| Concern | v1: CryptoVault (CLI) | v2: FinCore (REST API) |
|---|---|---|
| Storage | `ArrayList` + text file | MySQL with ACID transactions |
| Concurrency | single user at a keyboard | optimistic locking + automatic retry |
| Safety on retries | none | idempotency keys |
| Identity | none | JWT authentication, ownership checks, roles |
| History | editable log file | append-only ledger table |
| Schema changes | n/a | versioned Flyway migrations |

## API

All endpoints except the auth ones need `Authorization: Bearer <accessToken>`. The full, interactive reference is in Swagger UI at `/swagger-ui.html`.

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register` | Create an account (always role USER) |
| POST | `/api/auth/login` | Returns an access token (15 min) and a refresh token (7 days) |
| POST | `/api/auth/refresh` | Swap a refresh token for a new pair (each refresh token works once) |
| POST | `/api/auth/logout` | Revoke a refresh token |
| POST | `/api/wallets` | Create a wallet for yourself, e.g. `{"currency":"INR"}` |
| GET | `/api/wallets` | List your wallets |
| GET | `/api/wallets/{id}` | One of your wallets |
| POST | `/api/wallets/{id}/deposit` | Add money, e.g. `{"amount":1000}` |
| POST | `/api/transfers` | Send money. **Header `Idempotency-Key` required**, body `{"fromWalletId":1,"toWalletId":2,"amount":250.50}` |
| GET | `/api/transactions` | Your history: `?page=0&size=10&sort=createdAt,desc&walletId=1` |
| GET | `/api/admin/transactions` | All transactions (ADMIN only), paged |

Errors always use one JSON shape:

```json
{ "timestamp": "2026-11-01T10:15:30Z", "status": 422, "error": "Unprocessable Content",
  "message": "Insufficient balance in wallet 1: available 749.5000, requested 999999", "path": "/api/transfers" }
```

## Run it locally

### With Docker (easiest)

Needs Docker Desktop. This starts the app and its own MySQL; nothing else to install.

```bash
cp .env.example .env        # then edit .env: set DB_PASSWORD, JWT_SECRET, ADMIN_EMAIL, ADMIN_PASSWORD
docker compose up --build
```

Open <http://localhost:8083/swagger-ui.html>. Stop it with `docker compose down`.

### Without Docker

Needs Java 25 and a MySQL 8 server. Put your MySQL credentials and a JWT secret in `.env` (see `.env.example`), then:

```bash
./mvnw spring-boot:run        # Windows: .\mvnw.cmd spring-boot:run
```

Flyway creates the schema on first start.

### Configuration

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MySQL connection (`DB_URL` defaults to a local `fincore` database) |
| `JWT_SECRET` | Base64 HMAC key, at least 32 bytes. Generate one: `openssl rand -base64 64` |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Optional: creates the ADMIN account at startup |
| `PORT` | HTTP port (default 8083; set automatically by most hosting platforms) |

## Tests

```bash
./mvnw verify
```

42 tests. Unit tests (JUnit 5, Mockito) cover the money rules, JWT handling, login and refresh-token rotation, transfers, idempotent replays and the retry logic. Integration tests start a real MySQL with **Testcontainers** (Docker required), build the schema with the Flyway migrations, and run real threads against it:

- 10 simultaneous transfers of 20 from a wallet holding 100: exactly 5 succeed, the balance ends at 0, no money is created or lost;
- transfers in both directions between two wallets at once (the classic deadlock pattern): every one succeeds;
- the same payment sent 5 times at once with one `Idempotency-Key`: money moves exactly once;
- two users using the same key: both payments go through (keys are per user).

Postman collections for manual testing are in [`postman/`](postman).

## Design decisions

- **`BigDecimal` for money.** `0.1 + 0.2` is not `0.3` in binary floating point. Amounts are compared with `compareTo`, never `equals`.
- **Optimistic over pessimistic locking.** Most transfers touch different wallets, so conflicts are rare and locking up front would only add waiting. Conflicts are detected by the version check and retried outside the transaction boundary, so each attempt gets a fresh transaction.
- **A deadlock found by testing.** Hibernate flushes INSERTs before UPDATEs, so the ledger insert took shared locks on the wallets (foreign keys) and two transfers deadlocked upgrading them. Wallet updates are now flushed first, and deadlocks are retried like version conflicts.
- **Failed transfers are recorded too**, in their own transaction (`REQUIRES_NEW`), so the audit trail survives the rollback of the failed transfer. Only the sender can see them.
- **Refresh tokens are random, stored only as SHA-256 hashes, and rotated**; reusing an old one revokes every session of that user.
- **Schema is owned by Flyway**; Hibernate only validates it. Migration V2 changed idempotency keys from globally unique to per-user on a database that already had data (add column, backfill, then constrain).

## Deploying (Railway)

1. Create a project on [railway.com](https://railway.com) and add a **MySQL** database.
2. Add a service from this GitHub repo. Railway builds it with the `Dockerfile` and sets `PORT` itself.
3. In the service's **Variables**, set:
   - `DB_URL` = `jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}`
   - `DB_USERNAME` = `${{MySQL.MYSQLUSER}}`, `DB_PASSWORD` = `${{MySQL.MYSQLPASSWORD}}`
   - `JWT_SECRET` = a **new** secret (never reuse your local one), plus `ADMIN_EMAIL` / `ADMIN_PASSWORD` if you want an admin
4. Under **Settings → Networking**, generate a public domain. Swagger UI is then at `https://<domain>/swagger-ui.html`.

Flyway creates the schema on the first start.
