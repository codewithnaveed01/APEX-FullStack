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
railway.json         Railway deploy config (Dockerfile builder, /health check)
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
# app → http://localhost:3030   db → postgres://apex:apex@localhost:5432/apex
```

## Deploy to Railway — 10 steps

1. **Push this repo to GitHub** (this branch or your fork).
2. In Railway dashboard click **+ New Project → Deploy from GitHub repo** and pick the
   repo. `railway.json` makes Railway build the root `Dockerfile` automatically.
3. In the project open **+ New → Database → PostgreSQL**. Railway provisions a
   Postgres instance and injects `DATABASE_URL` into your service.
4. Click the app service → **Variables** tab and confirm `DATABASE_URL` is linked
   (Railway does this automatically when the database is in the same project).
5. Add the admin seed variables: `APEX_ADMIN_USER=admin` and a strong
   `APEX_ADMIN_PASS` (this account is created only on the first boot).
   Optional: `APEX_SESSION_HOURS=24`, `APEX_DB_POOL=5`.
6. **Settings → Networking** expose port **3030** (the app reads Railway's `PORT`
   override if you change it — set `PORT` to match the exposed port).
7. **Deploy.** First boot applies `backend/migrations/*.sql`, seeds the fleet, and
   creates the admin user. Watch the build logs for
   `APEX ready in ... ms`.
8. Open the generated `*.railway.app` domain → you should see the APEX website.
   Check `https://<your-domain>/health` → `{"status":"ok","db":"up"}`.
9. Go to `admin.html` on the same domain and sign in with the `APEX_ADMIN_USER` /
   `APEX_ADMIN_PASS` you set in step 5.
10. Everything (bookings, payments, chats, uploads) now persists in Railway's
    PostgreSQL. Redeploys keep the data; only `backend/uploads` volume is ephemeral
    unless you attach a volume — uploaded document bytes are stored **inside
    Postgres (BYTEA)** as the source of truth, so nothing is lost.

No MySQL anywhere: not in code, not in Docker, not in this guide.

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
