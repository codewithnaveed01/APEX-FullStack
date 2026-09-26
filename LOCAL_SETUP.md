# APEX Renting — Windows par chalane ka asaan tareeqa

**Pehle ZIP par right-click karke `Extract All` karein.** ZIP ke andar se seedha
`.bat` double-click na karein: Windows kabhi sirf wohi file temporary folder mein
extract karta hai aur baqi frontend/backend milte nahi. Extracted folder ke andar
`START_APEX.bat`, `docker-compose.yml`, `frontend/` aur `backend/` saath hon.

## Windows: one-click start (recommended)

1. [Docker Desktop](https://www.docker.com/products/docker-desktop/) install karein,
   usay open karein aur **Engine running** hone dein. Pehli dafa images download
   karne ke liye internet chahiye; Linux containers mode use karein.
2. Extracted `APEX-Renting-Local` folder mein **`START_APEX.bat` double-click** karein.
   Yeh PostgreSQL aur Java app ko Docker mein build/start karta hai, website ready
   hone ka wait karta hai aur browser khol deta hai.
3. Website: **http://localhost:3030/** — admin: **http://localhost:3030/admin.html**.
   Local demo login `admin` / `admin1234` hai.

**Agar black window mein error aaye, ab window band nahi hogi.** Jo ERROR likha
hai uski screenshot bhej dein. `Docker command was not found` aaye to Docker
Desktop install karein; `engine is not running` aaye to Docker Desktop open karein.
`This script must be run from the FULL extracted ZIP folder` aaye to dobara
`Extract All` karein. Is app ko sirf `frontend/index.html` khol kar nahi chalaya
ja sakta; Java backend **aur** PostgreSQL dono chahiye.

Port 3030 busy ho to extracted root folder mein PowerShell khol kar:

```powershell
$env:APEX_LOCAL_PORT = '3031'
.\START_APEX.bat
```

Phir website `http://localhost:3031/` par hogi. Docker Compose ka local port bhi
isi variable se set hota hai. Admin password ko agar kisi network par expose
karna ho to `docker-compose.yml` mein **pehli run se pehle** change karein;
existing database mein environment variable change se seeded password reset nahi
hota. Docker setup website ko sirf is computer ke loopback address par publish
karta hai.

Stop/restart (extracted root folder ke terminal mein):

```sh
docker compose down          # band karein; accounts/bookings/photos safe rahenge
docker compose up -d         # saved data ke saath dobara start
docker compose down -v       # WARNING: local data aur uploads DELETE ho jayenge
```

## macOS/Linux: Docker ke saath

Docker Engine/Desktop aur Compose installed hon. Extracted folder mein:

```sh
docker compose up --build -d
# website http://localhost:3030/ | admin http://localhost:3030/admin.html
```

`docker compose logs --tail=100 app` error dekhne ke liye; `/health` par
`"db":"up"` aana chahiye.

## Docker ke baghair (advanced manual mode)

Is mode ke liye **JDK 17+** (`java` + `javac`) aur **PostgreSQL 14+** dono install
hon, PostgreSQL chal raha ho, aur us mein `apex` database bani ho. Apne DB user
se misal: `createdb -U YOUR_PG_USER apex`. `backend/.env.example` sirf example
hai; Java app `.env` ko automatically nahi padhti.

**Windows PowerShell:** `DATABASE_URL` set karne aur `run.bat` chalane ke liye
**wahi PowerShell window** istemal karein (Explorer se double-click karne par
PowerShell ka temporary variable inherit nahi hota):

```powershell
$env:DATABASE_URL = 'postgres://YOUR_PG_USER:YOUR_PG_PASSWORD@127.0.0.1:5432/apex'
cd backend
.\run.bat
```

`backend/run.bat` pehli dafa Java compile karta hai; JDK/DB missing ya startup
error hone par **window khuli rahegi aur exact error dikhayegi**. `DATABASE_URL`
na ho to yeh asaan Docker launcher `START_APEX.bat` chalata hai.

**macOS/Linux:**

```sh
export DATABASE_URL='postgres://YOUR_PG_USER:YOUR_PG_PASSWORD@127.0.0.1:5432/apex'
cd backend
bash run.sh
```

DB password mein special characters hon to URL-encode karein. Manual mode mein
running server band karne ke liye `Ctrl+C`.

## ZIP ka content

- `START_APEX.bat` — Windows par one-click Docker launcher, visible diagnostics.
- `frontend/` — customer/admin pages, JavaScript, CSS, images aur fonts.
- `backend/src/`, `backend/lib/`, `backend/migrations/`, `backend/seed.json` — Java
  server, included Gson JAR, automatic DB schema aur sample 13 cars / 7 drivers.
- `backend/build.*`, `backend/run.*`, `backend/.env.example` — manual mode.
- `Dockerfile`, `docker-compose.yml`, `.dockerignore` — app + PostgreSQL together.
- `README.md` — poori documentation; `backend/test/` — optional tests.

ZIP mein **live website ka database, real customers ki private photos, passwords,
bookings, `.env` secrets ya compiled artifacts shamil nahi**. Pehli local run par
sample fleet banegi; real production data ke liye alag authorized backup chahiye.
