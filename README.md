# APEX — Driven Beyond Ordinary (FULL-STACK)

Customer website + operations (admin) panel + **Java OOP backend** + **SQLite database** —
ek hi zip me, laptop par local chalane ke liye aur server/VPS/Docker par deploy karne ke liye.

```
┌────────────────────────┐   REST (JSON)    ┌──────────────────────────────┐
│  frontend/             │ ───────────────► │  backend/  (Java 11, OOP)    │
│  index.html  (customer)│  /api/bootstrap  │  web  → service → dao → db   │
│  admin.html  (ops)     │  /api/sync       │  SQLite: apex.db (14 tables) │
│  customer.js + adapter │  /api/cars ...   │  static files bhi serve      │
└────────────────────────┘ ─────────────── └──────────────────────────────┘
```

## 1) Quick start (local laptop)

Requirements: **JDK 11+** (javac/java). Koi aur dependency nahi — jars `backend/lib/` me shamil hain.

**Linux / Mac**
```bash
cd backend
./run.sh            # build (first time) + start on http://localhost:3030
```

**Windows**
```bat
cd backend
run.bat
```

Phir browser me:
- Customer website : `http://localhost:3030/`
- Admin panel      : `http://localhost:3030/admin.html`  — login **admin / admin1234**
- API health       : `http://localhost:3030/api/health`

Port badalna ho: `PORT=9000 ./run.sh` (Windows: `set PORT=9000 && run.bat`).

## 2) Folder structure

```
frontend/            Customer + admin SPA (original design, 3D depth effects)
  index.html         Customer website
  admin.html         Operations panel (login admin / admin1234)
  customer.css       Styles
  customer.js        App logic + "JAVA BACKEND SYNC ADAPTER" (end of file)
  assets/            44 car images (main / interior / detail)
backend/
  src/com/apex/      Java OOP source
    model/           Car, Driver, User, Booking(+Items/Totals), OwnerApplication,
                     Notification, Chat, SiteConfig   (entities + validation)
    dao/             CarDao, DriverDao, UserDao, BookingDao, ApplicationDao,
                     NotificationDao, ChatDao, BanDao, WalletDao, SessionDao,
                     SettingsDao, BaseDao             (JDBC data-access layer)
    service/         StateService (single write-path), AuthService (tokens),
                     WalletService (ledger), StatsService (SQL aggregates)
    web/             Router, ApiRoutes, StaticHandler, HttpUtil
    db/              Database (schema + transactions)
    config/          AppConfig (env-driven)
  lib/               gson, sqlite-jdbc, slf4j jars (bundled)
  seed.json          First-run seed: 13 cars + 3 drivers + company settings
  schema.sql         Database ka full reference DDL (14 tables)
  build.sh / build.bat / run.sh / run.bat
Dockerfile           2-stage image (JDK build → JRE run)
```

## 3) Java OOP design

- **Encapsulation** — har entity private fields + getters/setters + `validate()`.
- **Layered architecture** — `web` (HTTP) → `service` (business rules) → `dao` (JDBC) → `db` (SQLite). Controllers kabhi SQL nahi likhte; DAOs kabhi HTTP nahi dekhte.
- **Polymorphism / abstraction** — `BaseDao` shared JDBC behaviour, `Router.Handler` functional interface, `Database.TxWork<T>` transaction callback.
- **Inheritance** — sab DAOs `BaseDao extends`; services composition se wired (`Main` composition root).
- **Single write-path** — frontend ka state document `PUT /api/sync` par aata hai aur `StateService` usay **ek transaction** me 14 normalized tables me likhta hai. `GET /api/bootstrap` tables se wahi document wapas banata hai.

## 4) Database (SQLite — `backend/apex.db`, first run par auto-create + seed)

| Table | Kaam |
|---|---|
| `cars` | Fleet (13 seed cars) — normalized columns + raw JSON |
| `drivers` | Chauffeur roster |
| `users` | Customer / partner accounts |
| `bookings` | Reservations — totals flattened for SQL reporting |
| `booking_items` | Har booking ke vehicle lines (dates, city, service, driver) |
| `owner_applications` | "List your car" onboarding applications |
| `notifications` | In-app notifications |
| `chats` | Support chat threads |
| `banned_cnic` | Banned CNIC list (checkout block) |
| `wallet` | Admin wallet balance (all payments first) |
| `owner_wallets` | Per-owner 90% payout balances |
| `wallet_transactions` | Wallet audit ledger (add-cash / withdraw) |
| `sessions` | Admin bearer tokens (12h expiry) |
| `settings` | Company / payment config document |

## 5) REST API (sab JSON)

Public reads: `GET /api/health · /api/bootstrap · /api/stats · /api/settings ·
/api/cars · /api/cars/{id} · /api/drivers · /api/bookings · /api/applications ·
/api/users · /api/notifications · /api/chats · /api/banned-cnic`

Auth: `POST /api/auth/login {username,password} → {token}` · `POST /api/auth/logout` · `GET /api/auth/me`

Admin (Bearer token) writes:
- `PUT /api/sync` — full state sync (frontend adapter automatically)
- `POST|PUT|DELETE /api/cars[/{id}]`, same for `/api/drivers`, `/api/bookings`, `/api/applications`, `/api/users`, `/api/notifications`, `/api/chats`
- `POST /api/banned-cnic` · `DELETE /api/banned-cnic/{cnic}`
- `GET /api/wallet` · `POST /api/wallet/add-cash {amount,note}` · `POST /api/wallet/withdraw {amount,note}`
- `PUT /api/settings`

Example:
```bash
TOKEN=$(curl -s -X POST localhost:3030/api/auth/login -H 'Content-Type: application/json' \
        -d '{"username":"admin","password":"admin1234"}' | python3 -c 'import json,sys;print(json.load(sys.stdin)["token"])')
curl -s localhost:3030/api/stats -H "Authorization: Bearer $TOKEN"
```

## 6) Frontend ↔ backend sync

`customer.js` ke end me adapter hai:
- Page load par `GET /api/bootstrap` se poora state (fleet, bookings, wallet…) database se aata hai.
- Har `persist()` ke baad 700ms debounce se `PUT /api/sync` (admin token ke sath) — yani admin login ke baad customer + admin dono pages ka har change database me save hota hai.
- Admin login `POST /api/auth/login` se token leta hai; logout par token clear.
- Server na mile (file:// double-click) to original localStorage mode — kuch nahi tootta.

## 7) Deploy

**Docker (sab se asaan)**
```bash
docker build -t apex-fullstack .
docker run -d -p 3030:3030 -v apex-data:/app/backend --name apex apex-fullstack
```

**Render (one-click)**
```
GitHub push → Render "New +" → Blueprint → render.yaml select → Deploy
```
Deploy ke baad `APEX_ADMIN_PASS` env var apna strong password set karein.

**VPS (Ubuntu) + systemd** — see `DEPLOY_GUIDE.txt` (scp zip, JDK install, service file, Nginx proxy).

**Render / Railway** — Dockerfile detect ho jata hai; start command `java -cp "out:lib/*" com.apex.Main` with `APEX_HOME=backend`.

Static-only deploy (bina backend) chahiye to original single-file previews alag maujood hain; is zip me full-stack hai.

## 8) Security notes (hardened demo build)

- **Bearer-token authorization** — sab mutating endpoints (sync, CRUD, wallet, bans, settings) admin token mangte hain; token 12h expiry ke sath `sessions` table me.
- **Brute-force lockout** — `/api/auth/login` par 5 ghalat attempts = 5 minute IP lock; sliding-window rate limit (10/min).
- **Rate limiting** — public `POST /api/payments/record` par 20/min; amount cap PKR 1,000,000; type whitelist (`payment`, `cancellation-fee`). Wallet ops par PKR 100M cap.
- **Sanitized public bootstrap** — bina token ke `GET /api/bootstrap` customer CNIC/phone aur wallet balances **hide** kerta hai; full data sirf admin token ke sath.
- **Security headers** — `X-Content-Type-Options: nosniff`, `X-Frame-Options: SAMEORIGIN`, `Referrer-Policy: no-referrer`, `Permissions-Policy` har response par.
- **Input hardening** — 5 MB body cap, strict amount validation, path traversal + dotfile block, SQLite transactions (rollback on error).
- Credentials env se: `APEX_ADMIN_USER` / `APEX_ADMIN_PASS` (local default admin/admin1234).
- Production checklist: HTTPS (Render/Docker-proxy automatic), strong env password, persistent disk, aur agar customer accounts ko server-side auth chahiye to sessions table extend karein. Demo frontend ka customer login client-side hai — isi liye public bootstrap sanitize kia gaya hai.

## 9) v2 updates (is build me)

- **Full car images** — cards / gallery / thumbnails ab `object-fit: contain` use karte hain; car kabhi crop/zoom nahi hoti.
- **Admin wallet samne** — admin panel kholte hi **Payments** tab (wallet panel) khulta hai; login bhi seedha wallet par le jata hai.
- **Withdrawal with account** — withdraw ab modal se hota hai: amount + account type (Bank / JazzCash / easypaisa) + account number + title **required**; server ledger me destination account record hota hai (`wallet_transactions.account`). Backend bina account ke withdrawal ko 400 se reject karta hai.
- **Customer payments → admin wallet (server)** — checkout par online payment hote hi frontend `POST /api/payments/record` call karta hai; amount **database ke admin wallet** me jama hota hai (ledger entry ke sath), chahe admin login ho ya na ho. Cancellation fee (5%) bhi record hoti hai. Wallet panel me **Server ledger ↗** button se live ledger dekhi ja sakti hai.
- **Owner full verification** — "List your car" form ab **CNIC (format-checked), driving licence, registration + declaration** mangta hai; banned CNIC block hota hai. Admin review me CNIC / licence / verification status dikhta hai; **Approved for onboarding** sirf tab chalta hai jab documents **Verified** hon aur CNIC banned na ho.
- **Clickable notifications** — har notification relevant page par le jati hai (customer: bookings/account/home; admin: Reservations / Payments / Messages / Partner applications).

Developed for APEX — Spidy red footer frontend me shamil hai.
