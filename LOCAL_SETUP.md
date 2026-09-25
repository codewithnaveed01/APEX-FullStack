# APEX Renting — apne computer par chalane ka tareeqa

Is ZIP mein customer website, admin panel, Java 17 backend, PostgreSQL migrations,
13 cars aur 7 branch-city drivers ka **seed data**, tasveerain/fonts, Docker setup
aur source code maujood hain. **Live website ki database, bookings, users ya CNIC
photos is ZIP mein shamil nahi hain.** Nayi local database pe pehli dafa seed data
banega.

## Sab se asaan: Docker (Windows, macOS ya Linux)

Docker Desktop install/start karein (Linux par Docker Engine + Compose plugin),
ZIP extract karein, aur us folder ke andar terminal/PowerShell kholein jahan
`docker-compose.yml` aur `Dockerfile` nazar aa rahe hon. Pehli dafa Docker ko images
download karne ke liye internet chahiye.

```sh
docker compose up --build -d
docker compose ps
```

Browser mein website **http://localhost:3030** aur admin panel
**http://localhost:3030/admin.html** kholein. Local admin ke default credentials:
`admin` / `admin1234`. Yeh sirf local demo ke liye hain; agar app ko network par
expose karna ho to `docker-compose.yml` mein `APEX_ADMIN_PASS` **pehli run se
pehle** strong password par change karein. Admin account first boot par ek martaba
create hota hai; baad mein Compose variable change karne se uska password reset
nahi hota.

Agar app khul na rahi ho to `docker compose logs --tail=100 app` check karein.
`http://localhost:3030/health` par `"db":"up"` aana chahiye. Port 3030 kisi aur
app ne use kiya ho to Compose file mein app ki `ports` line `"3031:3030"` kar dein
aur `http://localhost:3031` kholein.

```sh
docker compose down          # app aur DB band; data safe rahega
docker compose up -d         # saved data ke saath dobara start
docker compose down -v       # WARNING: local bookings, accounts, uploads DELETE
```

Bookings waghera Docker volumes mein save rehte hain; `down -v` tabhi karein jab
bilkul fresh local database chahiye.

## Docker ke baghair (optional)

**JDK 17 ya naya** (`java` aur `javac`), **PostgreSQL 14 ya naya** aur us mein `apex`
naam ki database chahiye. Apne PostgreSQL user/password se database banayein;
misal ke taur par `createdb -U YOUR_PG_USER apex`. PostgreSQL username/password
neeche URL mein use karein. `backend/.env.example` sirf reference hai; Java app
`.env` ko automatically load nahi karta.

**macOS/Linux terminal:**

```sh
export DATABASE_URL='postgres://YOUR_PG_USER:YOUR_PG_PASSWORD@127.0.0.1:5432/apex'
cd backend
bash build.sh
bash run.sh
```

**Windows PowerShell** (folder ke andar se):

```powershell
$env:DATABASE_URL = 'postgres://YOUR_PG_USER:YOUR_PG_PASSWORD@127.0.0.1:5432/apex'
cd backend
.\build.bat
.\run.bat
```

Special characters wale password ko URL-encode karein. Backend `frontend/` ko
khud serve karta hai — website aur API ke liye alag frontend server ki zaroorat
nahi. Pehli run par SQL migrations khud lagti hain aur seed fleet/driver list
load hoti hai. Running server band karne ke liye `Ctrl+C` dabayein.

## ZIP mein kya hai?

- `frontend/` — customer/admin pages, JavaScript/CSS, saari images aur fonts.
- `backend/src/`, `backend/lib/`, `backend/migrations/`, `backend/seed.json` — Java
  backend, bundled Gson jar, database schema aur sample fleet/drivers.
- `backend/build.*`, `backend/run.*`, `backend/.env.example` — manual run tools.
- `Dockerfile`, `docker-compose.yml`, `.dockerignore` — one-command local full stack.
- `README.md` — complete project documentation; `backend/test/` — optional tests.

`.git`, real `.env` secrets, compiled output aur purane uploads is package mein
shamil nahi. Production se data chahiye ho to uska alag authorized DB backup
zaroori hoga; is ZIP mein kisi real user ka private data nahi rakha gaya.
