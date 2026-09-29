# APEX FullStack

Customer website + Operations (admin) panel + **Java OOP backend** + **PostgreSQL**.

```
frontend/            static SPA (index.html + admin.html + customer.js + customer.css)
backend/
  src/com/apex/      Java 17 source (controllers → services → repos → db wire client)
  migrations/        PostgreSQL schema (applied automatically on boot)
  lib/               gson-2.10.1.jar (only dependency; BCrypt is vendored in src)
  seed.json          first-boot fleet/7 drivers/config seed
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
- **`migrations/002_add_seed_drivers.sql`** — adds four drivers on existing installations
  without overwriting admin edits; a fresh installation seeds all seven from `seed.json`.
- **`migrations/003_align_default_driver_branches.sql`** — aligns only untouched seed
  drivers with Lahore, Islamabad, Karachi and Dera Ghazi Khan; admin-edited drivers
  and the seven-driver total are preserved.
- **`migrations/004_owner_vehicle_marketplace.sql`** — adds durable ordered owner-car
  image references, unique application-to-car links, approval/edit/deletion audit
  history, encrypted payout accounts and the idempotent 90/10 owner payout ledger.
- **`migrations/005_approve_seed_drivers.sql`** — marks only the exact seven built-in
  chauffeur records as approved; admin-created driver applications remain pending.
- **`migrations/006_externalize_legacy_fleet_images.sql`** — migrates historical
  inline/base64 fleet photos into PostgreSQL document bytes and replaces the
  multi-megabyte public payloads with compact image references.

Money is **always computed server-side** from `cars.rate` / `hourly_rate` / settings —
client-sent prices are never trusted. Receipt upload → `Pending Verification`; only an
admin action verifies (credits wallet) / rejects / requests re-upload. Walk-in
bookings require both CNIC sides; photos are stored privately, linked to the
booking, and are not automatically marked verified. Admin messages remain available
in the dashboard inbox and from the message button on the public-facing site.
Banning/unbanning a CNIC uses the server's atomic endpoints; the saved list is
reloaded from the database after a refresh instead of a stale browser cache.

## Windows local quick start

**Extract the full ZIP first** (do not run a `.bat` directly inside a ZIP). Install
and start Docker Desktop, then double-click **`START_APEX.bat` in the extracted
root folder**. It starts the database and app, waits for `/health`, opens
http://localhost:3030 and keeps errors visible instead of closing the window.
See `LOCAL_SETUP.md` for troubleshooting, changing the local port and stopping
without deleting your saved data. `backend/run.bat` is the manual Java +
PostgreSQL path; when no database is configured it opens the Docker launcher.

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
`APEX_ADMIN_PASS` (defaults `admin` / `admin1234` — change them immediately).
In **Operations → Settings → Admin login details**, enter the current password to
change the admin username and/or password. A new password must be at least 12
characters (at most 72 UTF-8 bytes); leave it blank to change only the username.
The current browser receives a fresh session; **other admin sessions are signed
out**. Changes survive restart: the seed credentials are not reapplied.

All customer and admin passwords in PostgreSQL are salted one-way **BCrypt
hashes**, not reversible encryption. On startup, older plaintext entries in
`password_hash` or legacy user JSON are converted and those JSON secret fields
removed without resetting logins. If an older database was ever copied/backed
up with plaintext secrets, protect/remove those historical backups and rotate
those passwords; a migration cannot erase old backups or database WAL history.

## Docker

```bash
docker compose up --build
# app → http://localhost:3030   db → postgres://apex:apex@db:5432/apex (inside Compose)
```

### Tests (use a disposable database; E2E creates bookings/payments)

```bash
# With the app running at localhost:3030 (e.g. via Docker Compose):
python3 backend/test/e2e_test.py
python3 backend/test/owner_vehicle_e2e.py  # disposable DB: owner images/approval/edit/delete/payout
# Optional, with Node installed: frontend checks (no server needed)
node backend/test/frontend_sync_test.js
node backend/test/frontend_date_test.js
node backend/test/frontend_live_test.js
node backend/test/frontend_availability_test.js
node backend/test/frontend_manual_test.js
node backend/test/frontend_admin_support_ban_test.js
node backend/test/frontend_admin_credentials_test.js

# Credential rotation changes the admin login; run only on a disposable local DB:
# APEX_TEST_ALLOW_ADMIN_ROTATION=disposable-local-db APEX_TEST_BASE=http://127.0.0.1:3030 \
#   python3 backend/test/admin_credentials_e2e.py initial
# Restart the backend with the same test DB, then rerun with "restart".

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
   `APEX ready in ... ms`. Migrations run before the HTTP server starts; later
   boots skip applied files, apply new migrations once, and retain existing data.
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

Expect migrations **`001_init.sql` through `006_externalize_legacy_fleet_images.sql`**,
**23 app tables**, **13 seeded cars**, and **7 approved seeded drivers** on a new DB. If `schema_migrations` is
missing/empty or fewer tables appear, inspect the
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
attached. Owner listings require exactly three ordered images (exterior,
interior, detail/engine); approval publishes durable `/api/vehicle-images/{id}`
references backed by those PostgreSQL bytes. Admin fleet photo edits use the same
compact PostgreSQL-byte/public-reference model; API and static text responses are
gzip-compressed, while HTML/JS/CSS use ETag revalidation so every device receives
the current deployment without repeatedly downloading unchanged files. Owner payout
account values are AES-256-GCM encrypted before storage. Set a stable `APEX_PAYOUT_ENCRYPTION_KEY`
in Railway before collecting payout details. No MySQL is used.

## Configuration

All configuration is environment-based (see `backend/.env.example`):

| Variable | Purpose | Default |
|---|---|---|
| `PORT` | HTTP port | `3030` |
| `DATABASE_URL` | `postgres://user:pass@host:port/db?sslmode=require` | — |
| `PGHOST/PGPORT/PGUSER/PGPASSWORD/PGDATABASE/PGSSLMODE` | alternative to URL | local |
| `APEX_ADMIN_USER` / `APEX_ADMIN_PASS` | seeded admin (once); afterwards edit in Operations → Settings | `admin` / `admin1234` |
| `APEX_BIND_HOST` | optional bind address (local ZIP uses loopback; Docker/Railway default to all interfaces) | `0.0.0.0` |
| `APEX_SESSION_HOURS` | session lifetime | `24` |
| `APEX_DB_POOL` | connection pool size | `5` |
| `APEX_PAYOUT_ENCRYPTION_KEY` | stable high-entropy AES-GCM key for owner payout details (set in Railway; never rotate without a data migration) | DB-credential-derived compatibility fallback |
| `APEX_HOME` / `APEX_STATIC` / `APEX_UPLOADS` | paths | auto |

## API overview

`GET /health` · `GET/PUT /api/settings` · `POST /api/auth/{register,login,logout}` ·
`GET /api/auth/me` · `PUT /api/admin/credentials` (admin; current password required) ·
`GET /api/bootstrap` · `PUT /api/sync` (admin) ·
`GET/POST/PUT/DELETE /api/fleet[/{id}]` · `GET /api/availability` ·
`POST/GET/PUT /api/applications[/{id}]` (three images + approval workflow) ·
`GET /api/vehicle-images/{id}` (approved fleet images only) ·
`GET /api/owner/cars` · `PUT /api/owner/cars/{id}/edit` · `DELETE /api/owner/cars/{id}` ·
`GET/PUT /api/owner/payout-account` · `GET /api/owner/wallet` ·
`GET /api/admin/owners/{id}/payout-account` ·
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

- Passwords use per-account salted BCrypt hashes; older plaintext records are
  migrated on startup. Sessions are opaque random tokens in Postgres, revoked
  when an admin changes credentials.
- Admin credentials exist only server-side — nothing sensitive is in `customer.js`.
- CNIC / licence / receipt uploads: image magic-byte validation, 2.5 MB cap, stored in
  Postgres, readable only by the owner or an admin (no public URLs).
- CNIC ban list checked at booking time; bookings are serialized per car with
  `pg_advisory_xact_lock` so concurrent requests cannot double-book.
- Rate limiting on auth and upload endpoints; path traversal blocked on static files.
