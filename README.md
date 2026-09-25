# APEX FullStack

Customer website + Operations (admin) panel + **Java OOP backend** + **PostgreSQL**.

```
frontend/            static SPA (index.html + admin.html + customer.js + customer.css)
backend/
  src/com/apex/      Java 17 source (controllers → services → repos → db wire client)
  migrations/        PostgreSQL schema (applied automatically on boot)
  lib/               gson-2.10.1.jar (only dependency; BCrypt is vendored in src)
  seed.json          first-boot fleet/drivers/config seed
  .env.example       all supported environment variables
Dockerfile           multi-stage temurin:17 build
docker-compose.yml   app + postgres, one command
railway.json         legacy Railway config for existing services (new services: use dashboard settings)
```

## Architecture

- **`web/`** — HTTP router, static file server, rate limiter, centralized `ApiException`
  (400/401/403/404/409/422/500 → `{"error": "..."}`), controllers per resource.
- **`service/`** — business rules: server-side pricing (hourly + daily + discounts + late
  fees with grace/cap), booking with **advisory-lock double-booking protection**,
  payment verification flow, 90/10 owner payouts, uploads, sync, stats, auth (BCrypt +
  DB sessions).
- **`dao/`** — one repository per table family; all SQL lives here.
- **`db/`** — self-contained PostgreSQL wire-protocol client (v3, SCRAM-SHA-256/MD5/
  cleartext, extended query protocol) + connection pool + transaction helper +
  startup migrator. **No JDBC driver jar needed.**
- **`migrations/001_init.sql`** — 18 tables: users, sessions, cars, drivers, bookings,
  booking_items, owner_applications, notifications, chat_threads, chat_messages,
  banned_cnic, wallet, owner_wallets, wallet_transactions, documents (BYTEA), payments,
  reviews, app_settings.

Money is **always computed server-side** from `cars.rate` / `hourly_rate` / settings —
client-sent prices are never trusted. Receipt upload → `Pending Verification`; only an
admin action verifies (credits wallet) / rejects / requests re-upload.

## Local run (no Docker)

Requirements: JDK 17+, PostgreSQL 14+.

```bash
# 1) database
createdb apex                       # or: psql -c 'CREATE DATABASE apex'

# 2) configure
cp backend/.env.example backend/.env
#   set DATABASE_URL=postgres://USER:PASS@localhost:5432/apex

# 3) build + run
cd backend
bash build.sh
export $(grep -v '^#' .env | xargs) APEX_HOME=$PWD
java -cp "out:lib/*" com.apex.Main
```

Open http://localhost:3030 — migrations run automatically, fleet is seeded from
`seed.json`, and the admin account is created **once** from `APEX_ADMIN_USER` /
`APEX_ADMIN_PASS` (defaults `admin` / `admin1234` — change them in production).

## Docker

```bash
docker compose up --build
# app → http://localhost:3030   db → postgres://apex:apex@db:5432/apex (inside Compose)
```

### Tests (use a disposable database; E2E creates bookings/payments)

```bash
# With the app running at localhost:3030 (e.g. via Docker Compose):
python3 backend/test/e2e_test.py
# Optional, with Node installed: frontend checks (no server needed)
node backend/test/frontend_sync_test.js
node backend/test/frontend_date_test.js
node backend/test/frontend_live_test.js
node backend/test/frontend_availability_test.js

# With a LOCAL PostgreSQL URL accessible from the host and JDK 17 installed:
bash backend/build.sh
javac --release 17 -cp 'backend/out:backend/lib/*' -d backend/out \
  backend/test/DbProtocolTest.java backend/test/DeploymentConfigTest.java
APEX_HOME="$PWD/backend" DATABASE_URL='postgres://USER:PASS@HOST:PORT/TEST_DB' \
  java -cp 'backend/out:backend/lib/*' com.apex.db.DbProtocolTest
env -u DATABASE_URL -u PGHOST -u RAILWAY_ENVIRONMENT -u RAILWAY_SERVICE_ID \
  java -cp 'backend/out:backend/lib/*' com.apex.config.DeploymentConfigTest
```

## Deploy to Railway (app + PostgreSQL)

The Java app serves **both** `/` (customer site), `/admin.html` (admin panel), and
`/api/*` from the **same domain**. Do not deploy `frontend/` as a separate service.

1. Add a **GitHub service** for this repo in Railway. Leave **Root Directory at the
   repository root** (not `frontend` or `backend`). Railway detects the root
   `Dockerfile` automatically; leave **Start Command unset** so Docker's `CMD` runs.
   Check the build log for `Using detected Dockerfile!`.
2. Add **+ New → Database → PostgreSQL** to the **same Railway project/environment**.
   Note the database service's actual name (often `Postgres`).
3. In the **app service → Variables**, create **`DATABASE_URL` as a reference** to
   the database service: `DATABASE_URL=${{Postgres.DATABASE_URL}}` (substitute its
   actual service name; use Railway's reference-variable autocomplete). **Deploy the
   staged variable change.** Creating a Postgres service does **not** automatically
   inject its credentials into the app service. Use the **private** `DATABASE_URL`,
   not `DATABASE_PUBLIC_URL`. Never paste DB passwords into source control.
4. In the **app service → Variables**, set `APEX_ADMIN_USER=admin` and a strong
   `APEX_ADMIN_PASS`; they create an admin **once** on an empty database (changing
   them later will not reset an existing account's password).
5. Set the **app service** Healthcheck Path to `/health` in **Settings → Deploy**
   (and timeout to 300s if the DB is slow to start). Leave `PORT` automatic; this
   server listens on Railway's `$PORT` on `0.0.0.0`. If setting `PORT=3030` manually,
   ensure the domain's **target port** is also `3030`. Do **not** use DB port 5432
   as the website's target port.
6. **Deploy** the app. In deployment logs, expect `Connected to PostgreSQL`,
   `Applying migration 001_init.sql`, `Applied migration 001_init.sql`, and
   `APEX ready in ... ms`. Migrations run automatically on first boot, before
   the HTTP server starts; later boots skip applied migrations and retain data.
7. Under the **app** service's **Settings → Networking → Public Networking** click
   **Generate Domain**. A Postgres service's domain/TCP proxy is **not** the website.
   Open `https://<app-domain>/` and `/admin.html`. Check
   `https://<app-domain>/health` for `{"status":"ok","db":"up",...}` and
   `/api/bootstrap` for the seeded fleet. `/health` returns HTTP 503 if the DB
   later becomes unavailable.

### When Railway connects but no tables appear

On the **PostgreSQL service** (not the app), open its SQL/Data browser and run:

```sql
SELECT current_database(), current_schema();
SELECT name, applied_at FROM public.schema_migrations;
SELECT count(*) AS app_tables FROM information_schema.tables
  WHERE table_schema = 'public' AND table_name <> 'schema_migrations';
SELECT count(*) AS seeded_cars FROM public.cars;
```

Expect **`001_init.sql`**, **18 app tables**, and **13 cars** on a new DB. If
`schema_migrations` is missing/empty or fewer tables appear, inspect the
**app's** deploy logs: the DB reference may be missing/not deployed, point to a
*different database*, or startup/migration may have failed. The app logs an
actionable error when no DB variable is set on Railway. Do not delete or
recreate your database to fix a missing reference.

### If the website still gives a 502 / unavailable

Check the app logs for `APEX ready`. If absent, fix the logged DB/migration error;
if present, verify **Generate Domain is on the app**, Root Directory is the repo
root, Start Command is unset, and its target port matches `$PORT`. `/health`
checks the DB; `/` checks static files; `/api/bootstrap` checks seeded data.

### Admin banner after refresh: `Server save FAILED (HTTP 422)`

Admin changes now wait for a verified server bootstrap. A refresh **does not** upload
old browser-cached users/bookings/fleet to PostgreSQL; after an edit only the
changed collection is synced. If a real save still fails, the red banner shows
the backend's specific `error` (for example, which row is missing a required
field). Correct the indicated data and edit/save again. Unsaved admin edits
are kept in that browser across refreshes, so **do not clear site storage**
without first backing up any unsaved work. If the banner says the admin session
expired, sign in again; if bootstrap cannot load, editing stays paused until
it succeeds. Server data is never reset by clearing a browser cache.

**Railway note (2026):** `railway.json` is a *legacy* [Config-as-Code](https://docs.railway.com/config-as-code)
file. New Railway services do not use it; set the healthcheck/restart settings
in the service dashboard as above (existing legacy services can still read the
file). The root Dockerfile is detected independently of `railway.json`.

Uploaded document bytes are stored in PostgreSQL (`BYTEA`) as the source of
truth; the local `backend/uploads` directory is ephemeral unless a volume is
attached. No MySQL is used.

## Configuration

All configuration is environment-based (see `backend/.env.example`):

| Variable | Purpose | Default |
|---|---|---|
| `PORT` | HTTP port | `3030` |
| `DATABASE_URL` | `postgres://user:pass@host:port/db?sslmode=require` | — |
| `PGHOST/PGPORT/PGUSER/PGPASSWORD/PGDATABASE/PGSSLMODE` | alternative to URL | local |
| `APEX_ADMIN_USER` / `APEX_ADMIN_PASS` | seeded admin (once) | `admin` / `admin1234` |
| `APEX_SESSION_HOURS` | session lifetime | `24` |
| `APEX_DB_POOL` | connection pool size | `5` |
| `APEX_HOME` / `APEX_STATIC` / `APEX_UPLOADS` | paths | auto |

## API overview

`GET /health` · `GET/PUT /api/settings` · `POST /api/auth/{register,login,logout}` ·
`GET /api/auth/me` · `GET /api/bootstrap` · `PUT /api/sync` (admin) ·
`GET/POST/PUT/DELETE /api/fleet[/{id}]` · `GET /api/availability` ·
`POST /api/orders` + `GET/PUT/DELETE /api/orders/{id}` ·
`POST /api/bookings/{id}/{pickup,return,cancel}` ·
`POST /api/payments/{submit,record}` · `GET /api/payments` ·
`POST /api/payments/{id}/review` · `POST /api/uploads` ·
`GET /api/documents[/{id}]` · `POST /api/documents/{id}/review` ·
`GET/POST/DELETE /api/reviews[...]` · `POST /api/chats/send` · `GET /api/chats` ·
`GET/POST/DELETE /api/banned-cnic[/{cnic}]` · `GET /api/stats` ·
`GET /api/wallet` · `POST /api/wallet/{add-cash,withdraw}` ·
collection CRUD for `users`, `drivers`, `applications`, `notifications` (admin).

Errors always return `{"error": "message"}` with the proper status code.

## Security notes

- Passwords hashed with BCrypt; sessions are opaque random tokens in Postgres.
- Admin credentials exist only server-side — nothing sensitive is in `customer.js`.
- CNIC / licence / receipt uploads: image magic-byte validation, 2.5 MB cap, stored in
  Postgres, readable only by the owner or an admin (no public URLs).
- CNIC ban list checked at booking time; bookings are serialized per car with
  `pg_advisory_xact_lock` so concurrent requests cannot double-book.
- Rate limiting on auth and upload endpoints; path traversal blocked on static files.
