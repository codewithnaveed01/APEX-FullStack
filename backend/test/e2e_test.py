#!/usr/bin/env python3
"""APEX backend end-to-end test suite (runs against 127.0.0.1:3030)."""
import json, urllib.request, urllib.error, uuid, sys, concurrent.futures, time, os

BASE = os.environ.get("APEX_TEST_BASE", "http://127.0.0.1:3030").rstrip("/")
ADMIN_PASS = os.environ.get("APEX_TEST_ADMIN_PASS", "admin1234")
PASS, FAIL = 0, 0
FAILURES = []

def req(method, path, body=None, token=None, raw=False):
    url = BASE + path
    data = None
    headers = {"Content-Type": "application/json"}
    if body is not None:
        data = body if isinstance(body, bytes) else json.dumps(body).encode()
    if token:
        headers["Authorization"] = "Bearer " + token
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            payload = resp.read()
            return resp.status, (payload if raw else (json.loads(payload) if payload else None))
    except urllib.error.HTTPError as e:
        payload = e.read()
        try:
            return e.code, json.loads(payload)
        except Exception:
            return e.code, payload

def check(name, cond, extra=""):
    global PASS, FAIL
    if cond:
        PASS += 1
        print(f"  PASS {name}")
    else:
        FAIL += 1
        FAILURES.append(name)
        print(f"  FAIL {name} {extra}")

def png_bytes():
    # 1x1 red PNG
    import base64
    return base64.b64decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")

print("== 1. Health & static ==")
s, b = req("GET", "/health")
check("GET /health 200 + db up", s == 200 and b.get("status") == "ok" and b.get("db") == "up", f"{s} {b}")
s, b = req("GET", "/api/health")
check("GET /api/health 200", s == 200, f"{s}")
s, b = req("GET", "/", raw=True)
check("GET / serves index.html", s == 200 and b"customer.js" in b, f"{s}")
s, b = req("GET", "/customer.js", raw=True)
check("GET /customer.js", s == 200 and len(b) > 100000, f"{s} len={len(b) if isinstance(b, bytes) else '?'}")
s, b = req("GET", "/../etc/passwd", raw=True)
check("path traversal no leak", s in (200, 404) and b"root:" not in (b if isinstance(b, bytes) else b""), f"{s}")
s, b = req("GET", "/api/nope")
check("unknown api 404 + error json", s == 404 and isinstance(b, dict) and "error" in b, f"{s} {b}")

print("== 2. Auth ==")
s, b = req("POST", "/api/auth/login", {"username": "admin", "password": ADMIN_PASS})
check("admin login", s == 200 and b.get("role") == "admin" and b.get("token"), f"{s} {b if s != 200 else 'ok'}")
admin = b.get("token") if s == 200 else None
s, b = req("POST", "/api/auth/login", {"username": "admin", "password": "wrong"})
check("bad password 401", s == 401, f"{s} {b}")

uname = "cust_" + uuid.uuid4().hex[:6]
s, b = req("POST", "/api/auth/register", {
    "username": uname, "email": f"{uname}@test.com", "name": "Test Customer",
    "phone": "03001234567", "password": "secret123", "cnic": "3520212345678"})
check("register customer 201", s == 201 and b.get("role") == "customer" and b.get("token"), f"{s}")
cust = b.get("token") if s == 201 else None
cust_id = (b.get("user") or {}).get("id") if s == 201 else None
check("register returns user id", bool(cust_id), f"{b if s != 201 else ''}")
s, b = req("POST", "/api/auth/register", {
    "username": uname, "email": f"x{uname}@test.com", "name": "Dup", "phone": "03005551111",
    "password": "secret123"})
check("duplicate username 409", s == 409, f"{s} {b}")
s, b = req("POST", "/api/auth/register", {
    "username": uname + "2", "email": f"{uname}@test.com", "name": "Dup", "phone": "03005551112",
    "password": "secret123"})
check("duplicate email 409", s == 409, f"{s} {b}")
s, b = req("POST", "/api/auth/register", {
    "username": "bad user!", "email": "bad", "name": "X", "phone": "1", "password": "123"})
check("invalid register 422", s == 422, f"{s} {b}")
s, b = req("GET", "/api/auth/me", token=cust)
check("GET /me customer", s == 200 and b.get("username") == uname, f"{s}")
s, b = req("GET", "/api/auth/me", token="garbage")
check("garbage token 401", s == 401, f"{s}")
s, b = req("GET", "/api/auth/me")
check("no-token /me 401", s == 401, f"{s}")
s, b = req("GET", "/api/notifications")
check("admin endpoint without token 401", s == 401, f"{s}")
s, b = req("GET", "/api/notifications", token=cust)
check("customer on admin endpoint 403", s == 403, f"{s}")

print("== 3. Bootstrap ==")
s, b = req("GET", "/api/bootstrap")
check("public bootstrap fleet", s == 200 and len(b.get("fleet", [])) == 13, f"{s} fleet={len(b.get('fleet', [])) if isinstance(b, dict) else b}")
check("public bootstrap config", isinstance(b.get("config"), dict) and b["config"].get("driverRate", 0) > 0, f"{b.get('config') if isinstance(b, dict) else b}")
s, b = req("GET", "/api/bootstrap", token=admin)
check("admin bootstrap users+orders+wallets", s == 200 and "users" in b and "orders" in b and "adminWallet" in b and "bannedCNICs" in b, f"{s}")
check("admin bootstrap has admin user", any(u.get("username") == "admin" for u in b.get("users", [])), "")
s, b = req("GET", "/api/bootstrap", token=cust)
check("customer bootstrap own-slice", s == 200 and b.get("adminWallet") == {}, f"{s}")
users_pub = b.get("users", [])
check("customer bootstrap public users (no password)", all("password" not in u for u in users_pub), f"{users_pub[:1]}")
check("customer bootstrap no banned leak ok", "bannedCNICs" in b, "")

print("== 4. Fleet CRUD (admin) ==")
s, fleet = req("GET", "/api/fleet")
check("GET /api/fleet public 13", s == 200 and len(fleet) == 13, f"{s}")
car = fleet[0]
car_id = car["id"]
check("fleet car shape", all(k in car for k in ("id", "name", "rate", "status")), f"{list(car)[:8]}")
s, b = req("POST", "/api/fleet", {"name": "Temp Car", "brand": "Toyota", "category": "Sedan",
    "rate": 10000, "hourlyRate": 1000, "deposit": 5000, "seats": 5, "status": "Active",
    "ownerId": "owner_786"}, token=admin)
check("admin create car 201", s == 201 and b.get("name") == "Temp Car" and b.get("id"), f"{s} {b}")
temp_id = b.get("id") if s == 201 else None
s, b = req("PUT", f"/api/fleet/{temp_id}", {"name": "Temp Car 2", "rate": 11000, "status": "Active"}, token=admin)
check("admin update car", s == 200 and b.get("name") == "Temp Car 2", f"{s} {b}")
s, b = req("POST", "/api/fleet", {"name": "Nope"}, token=cust)
check("customer create car 403", s == 403, f"{s}")
s, b = req("GET", f"/api/fleet/{car_id}")
check("GET car by id", s == 200 and b.get("id") == car_id, f"{s}")

print("== 5. Availability + booking + double-booking ==")
today = time.strftime("%Y-%m-%d")
d1 = time.strftime("%Y-%m-%d", time.gmtime(time.time() + 86400 * 3))
d2 = time.strftime("%Y-%m-%d", time.gmtime(time.time() + 86400 * 4))
s, b = req("GET", f"/api/availability?carId={car_id}&start={d1}&end={d2}")
check("availability free", s == 200 and b.get("available") is True, f"{s} {b}")
s, b = req("GET", f"/api/availability?carId={car_id}&start={d1}T09:00&end={d2}T09:00")
check("exact hourly availability free", s == 200 and b.get("available") is True, f"{s} {b}")

def mk_order(car_ids, start, end, token, service="Self-drive", cnic="3520212345678", payment="JazzCash", extra=None):
    body = {
        "name": "Test Customer", "phone": "03001234567", "email": f"{uname}@test.com",
        "identityType": "CNIC", "identity": cnic, "pickupMode": "Office",
        "destination": "Lahore", "payment": payment,
        "items": [{"carId": c, "start": start, "end": end, "city": "Faisalabad",
                   "service": service, "startDt": start + "T09:00", "endDt": end + "T09:00"}
                  for c in car_ids],
    }
    if extra: body.update(extra)
    return req("POST", "/api/orders", body, token)

s, order1 = mk_order([car_id], d1, d2, cust)
check("create booking 201", s == 201 and order1.get("id") and order1.get("status") == "Pending Verification", f"{s} {order1 if s != 201 else ''}")
oid = order1.get("id") if s == 201 else None
items = order1.get("items", []) if s == 201 else []
price = items[0].get("price", {}) if items else {}
# verify server-side quote: 1 day (24h): hours=24, days=1, extra=0, base=rate, no saving, deposit
check("server quote fields present", all(k in price for k in ("n", "hours", "days", "base", "saving", "driver", "rental", "deposit", "total")), f"{price}")
check("quote hours=24", price.get("hours") == 24, f"{price}")
check("quote base=daily rate", price.get("base") == car.get("rate"), f"base={price.get('base')} rate={car.get('rate')}")
check("quote deposit for self-drive", price.get("deposit") == car.get("deposit"), f"{price.get('deposit')} vs {car.get('deposit')}")
check("booking identity masked", order1.get("identityMasked", "").startswith("35202") and "3520212345678" != order1.get("identityMasked"), f"{order1.get('identityMasked')}")
totals = order1.get("totals", {})
check("totals consistent", totals.get("rental", -1) + totals.get("deposit", 0) == totals.get("total", -2), f"{totals}")

s, b = req("GET", f"/api/availability?carId={car_id}&start={d1}&end={d2}")
check("availability now clash", s == 200 and b.get("available") is False and b.get("clashWith") == "Reserved", f"{s} {b}")
s, b = req("GET", f"/api/availability?carId={car_id}&start={d1}T09:00&end={d2}T09:00")
check("hourly availability detects booked window without exposing customer", s == 200 and b.get("available") is False and b.get("clashWith") == "Reserved" and (not oid or oid not in str(b)) and "Test Customer" not in str(b), f"{s} {b}")
s, b = req("GET", f"/api/availability?carId={car_id}&start={d1}T07:00&end={d1}T09:00")
check("hourly availability allows window ending at pickup", s == 200 and b.get("available") is True, f"{s} {b}")
s, b = req("GET", f"/api/availability?carId={car_id}&start={d2}T09:00&end={d2}T10:00")
check("hourly availability allows window starting at return", s == 200 and b.get("available") is True, f"{s} {b}")
for invalid_start, invalid_end in [(d2+"T10:00", d2+"T09:00"), (d1, d2+"T09:00"), ("2026-02-30T09:00", d2+"T09:00"), ("2026-02-30", d2)]:
    s, b = req("GET", f"/api/availability?carId={car_id}&start={invalid_start}&end={invalid_end}")
    check("invalid availability window rejected", s == 422, f"{s} {b}")
s, summary = req("GET", f"/api/availability/fleet?start={d1}T09:00&end={d2}T09:00")
check("public fleet availability flags rented car", s == 200 and car_id in summary.get("rentedIds", []), f"{s} {summary}")
check("public availability exposes no booking details", s == 200 and set(summary) == {"rentedIds"}, f"{summary}")
s, b = req("GET", "/api/availability/fleet?start=bad&end=bad")
check("invalid fleet window rejected", s == 422, f"{s} {b}")

# overlapping window must clash (back-to-back is allowed, overlap is not)
s, b = mk_order([car_id], d1, time.strftime("%Y-%m-%d", time.gmtime(time.time() + 86400 * 6)), cust)
check("partial overlap rejected 409 without booking identity", s == 409 and "clash" in str(b.get("error", "")).lower() and "Test Customer" not in str(b) and (not oid or oid not in str(b)), f"{s} {b}")

# concurrency: 4 parallel bookings, same car & window (using a different car)
conc_car = fleet[1]["id"]
def try_book(_):
    return mk_order([conc_car], d1, d2, cust)[0]
with concurrent.futures.ThreadPoolExecutor(4) as ex:
    results = list(ex.map(try_book, range(4)))
wins = sum(1 for s in results if s == 201)
losses = sum(1 for s in results if s == 409)
check("concurrent double-booking: exactly 1 win", wins == 1 and losses == 3, f"results={results}")

# multi-car booking
s, order2 = mk_order([fleet[1]["id"], fleet[2]["id"]],
                     time.strftime("%Y-%m-%d", time.gmtime(time.time() + 86400 * 10)),
                     time.strftime("%Y-%m-%d", time.gmtime(time.time() + 86400 * 11)), cust)
check("multi-car booking 201", s == 201 and len(order2.get("items", [])) == 2, f"{s}")
oid2 = order2.get("id") if s == 201 else None

# validation errors
s, b = mk_order([car_id], d1, d2, cust, cnic="123")
check("bad CNIC 422", s == 422, f"{s} {b}")
s, b = req("POST", "/api/orders", {"name": "X Y", "phone": "03001234567", "identity": "3520212345678", "items": []}, cust)
check("empty items 422", s == 422, f"{s} {b}")
s, b = mk_order([999999], d1, d2, cust)
check("unknown car 404", s == 404, f"{s} {b}")
# customer cannot see another customer's booking - create second customer
uname2 = "cust2_" + uuid.uuid4().hex[:6]
s, b2 = req("POST", "/api/auth/register", {"username": uname2, "email": f"{uname2}@t.com", "name": "Second", "phone": "03011234567", "password": "secret123"})
cust2 = b2.get("token")
s, b = req("GET", f"/api/orders/{oid}", token=cust2)
check("other customer's booking 403", s == 403, f"{s} {b}")
s, b = req("GET", f"/api/orders/{oid}", token=cust)
check("own booking 200", s == 200 and b.get("id") == oid, f"{s}")

print("== 5a. Recipient-scoped notifications and live feed ==")
s, b = req("GET", "/api/live")
check("live feed requires sign-in", s == 401, f"{s} {b}")
s, customer_live = req("GET", "/api/live", token=cust)
check("customer live feed has own booking alert", s == 200 and any(n.get("link") == f"booking/{oid}" for n in customer_live.get("notifications", [])), f"{s} {customer_live}")
check("customer cannot see admin notifications", s == 200 and all(n.get("userId") == cust_id for n in customer_live["notifications"]), f"{customer_live.get('notifications')}")
s, second_live = req("GET", "/api/live", token=cust2)
check("second customer cannot see first customer's alerts", s == 200 and not second_live.get("notifications") and all(c.get("userId") != cust_id for c in second_live.get("chats", [])), f"{s} {second_live}")
s, admin_live = req("GET", "/api/live", token=admin)
check("admin sees its own booking alert only", s == 200 and any(n.get("link") == f"booking/{oid}" for n in admin_live.get("notifications", [])) and all(n.get("userId") in ("admin", "all") for n in admin_live["notifications"]), f"{s} {admin_live.get('notifications')}")
s, b = req("POST", "/api/notifications/read", {}, token=cust)
check("customer marks own alerts read", s == 200 and all(n.get("read") for n in req("GET", "/api/notifications/mine", token=cust)[1]), f"{s} {b}")
s, b = req("GET", "/api/notifications/mine", token=admin)
check("customer read does not clear admin alerts", s == 200 and any(not n.get("read") for n in b), f"{s} {b}")
s, application = req("POST", "/api/applications", {
    "owner": "Test Customer", "brand": "Toyota", "model": "Yaris", "city": "Lahore", "year": 2024,
    "status": "Submitted"}, token=cust)
app_id = application.get("id") if s == 201 else None
check("customer submits vehicle application", s == 201 and app_id, f"{s} {application}")
s, b = req("GET", "/api/live", token=cust)
check("customer application alert links to exact item", s == 200 and any(n.get("link") == f"application/{app_id}" for n in b.get("notifications", [])), f"{s} {b.get('notifications')}")
s, b = req("GET", f"/api/applications/{app_id}", token=cust)
check("customer opens own application", s == 200 and b.get("id") == app_id, f"{s} {b}")
s, b = req("GET", f"/api/applications/{app_id}", token=cust2)
check("another customer cannot open application", s == 403, f"{s} {b}")
s, b = req("GET", f"/api/applications/{app_id}", token=admin)
check("admin opens application", s == 200 and b.get("id") == app_id, f"{s} {b}")
s, b = req("GET", "/api/live", token=admin)
check("application alert links to exact application", s == 200 and any(n.get("link") == f"application/{app_id}" for n in b.get("notifications", [])), f"{s} {b.get('notifications')}")

print("== 6. Banned CNIC ==")
s, b = req("POST", "/api/banned-cnic", {"cnic": "9990001112223"}, token=admin)
check("ban cnic 201", s == 201, f"{s} {b}")
s, b = req("POST", "/api/banned-cnic", {"cnic": "123"}, token=admin)
check("ban bad cnic 422", s == 422, f"{s}")
d3 = time.strftime("%Y-%m-%d", time.gmtime(time.time() + 86400 * 20))
s, b = mk_order([fleet[3]["id"]], d3, time.strftime("%Y-%m-%d", time.gmtime(time.time() + 86400 * 21)), cust, cnic="9990001112223")
check("banned cnic booking 403", s == 403 and "banned" in str(b.get("error", "")).lower(), f"{s} {b}")
s, b = req("GET", "/api/banned-cnic")
check("public banned list", s == 200 and any(x.get("cnic") == "9990001112223" for x in b), f"{s} {b}")
s, b = req("DELETE", "/api/banned-cnic/9990001112223", token=admin)
check("unban 200", s == 200, f"{s}")

print("== 7. Documents ==")
data_url = "data:image/png;base64," + __import__("base64").b64encode(png_bytes()).decode()
s, doc = req("POST", "/api/uploads", {"dataUrl": data_url, "kind": "cnic_front", "ownerType": "user"}, cust)
check("upload 201", s == 201 and doc.get("id") and doc.get("status") == "Pending", f"{s} {doc}")
doc_id = doc.get("id") if s == 201 else None
s, b = req("GET", f"/api/documents/{doc_id}", token=cust)
check("owner views doc", s == 200 and b.get("dataUrl", "").startswith("data:image/png;base64,"), f"{s}")
s, b = req("GET", f"/api/documents/{doc_id}", token=cust2)
check("other customer 403", s == 403, f"{s}")
s, b = req("GET", "/api/documents", token=admin)
check("admin lists docs", s == 200 and any(d.get("id") == doc_id for d in b), f"{s}")
s, b = req("POST", "/api/uploads", {"dataUrl": "data:text/plain;base64,SGk=", "kind": "receipt"}, cust)
check("non-image upload rejected", s in (400, 422), f"{s} {b}")
s, b = req("POST", "/api/uploads", {"dataUrl": data_url, "kind": "xkcd"}, cust)
check("bad kind rejected", s == 422, f"{s}")
s, b = req("POST", f"/api/documents/{doc_id}/review", {"status": "Verified"}, token=admin)
check("admin review doc", s == 200, f"{s}")

print("== 8. Payments (never auto-verified) ==")
amount = order1["totals"]["total"]
s, pay = req("POST", "/api/payments/submit", {"bookingId": oid, "method": "JazzCash", "amount": amount, "tid": "TID123456", "receiptDoc": doc_id}, cust)
check("payment submit 201 Pending", s == 201 and pay.get("status") == "Pending Verification", f"{s} {pay}")
pay_id = pay.get("id") if s == 201 else None
s, b = req("POST", "/api/payments/submit", {"bookingId": oid, "method": "JazzCash", "amount": amount, "tid": "TID123456", "receiptDoc": doc_id}, cust)
check("duplicate pending payment 409", s == 409, f"{s} {b}")
s, ob = req("GET", f"/api/orders/{oid}", token=admin)
check("booking now Pending Verification", s == 200 and ob.get("status") == "Pending Verification", f"{s} {ob.get('status')}")
s, w0 = req("GET", "/api/wallet", token=admin)
bal0 = w0.get("balance", 0)
check("wallet before verify", s == 200 and "transactions" in w0, f"{s}")
s, b = req("POST", f"/api/payments/{pay_id}/review", {"action": "verify", "note": "ok"}, token=cust)
check("customer cannot review 403", s == 403, f"{s}")
s, b = req("POST", f"/api/payments/{pay_id}/review", {"action": "verify", "note": "checked"}, token=admin)
check("admin verify 200", s == 200 and b.get("status") == "Verified", f"{s} {b}")
s, w1 = req("GET", "/api/wallet", token=admin)
check("wallet credited", w1.get("balance") == bal0 + amount, f"before={bal0} after={w1.get('balance')} amount={amount}")
s, ob = req("GET", f"/api/orders/{oid}", token=admin)
check("booking -> Pickup Pending + Verified + paid", ob.get("status") == "Pickup Pending" and ob.get("paymentStatus") == "Verified" and ob.get("paid") == amount, f"{ob.get('status')}/{ob.get('paymentStatus')}/{ob.get('paid')}")
s, b = req("POST", f"/api/payments/{pay_id}/review", {"action": "verify"}, token=admin)
check("re-verify 409", s == 409, f"{s} {b}")
# reupload flow
d4 = time.strftime("%Y-%m-%d", time.gmtime(time.time() + 86400 * 30))
s, o3 = mk_order([fleet[4]["id"]], d4, time.strftime("%Y-%m-%d", time.gmtime(time.time() + 86400 * 31)), cust)
oid3 = o3.get("id")
s, p3 = req("POST", "/api/payments/submit", {"bookingId": oid3, "method": "easypaisa", "amount": o3["totals"]["total"], "tid": "TID999", "receiptDoc": doc_id}, cust)
s, b = req("POST", f"/api/payments/{p3['id']}/review", {"action": "reupload", "note": "blurry"}, token=admin)
check("reupload action", s == 200 and b.get("status") == "Reupload", f"{s} {b}")
s, ob3 = req("GET", f"/api/orders/{oid3}", token=admin)
check("booking Reupload Requested", ob3.get("paymentStatus") == "Reupload Requested", f"{ob3.get('paymentStatus')}")
s, p3b = req("POST", "/api/payments/submit", {"bookingId": oid3, "method": "easypaisa", "amount": o3["totals"]["total"], "tid": "TID999", "receiptDoc": doc_id}, cust)
check("re-upload after reupload allowed", s == 201, f"{s} {p3b}")
s, b = req("POST", f"/api/payments/{p3b['id']}/review", {"action": "reject", "note": "wrong amount"}, token=admin)
check("reject action", s == 200 and b.get("status") == "Rejected", f"{s} {b}")
s, ob3 = req("GET", f"/api/orders/{oid3}", token=admin)
check("booking Rejected paymentStatus", ob3.get("paymentStatus") == "Rejected", f"{ob3.get('paymentStatus')}")

print("== 9. Pickup / return / late fees / payout ==")
s, b = req("POST", f"/api/bookings/{oid}/pickup", {"driverId": 1}, token=cust)
check("customer pickup 403", s == 403, f"{s}")
s, b = req("POST", f"/api/bookings/{oid}/pickup", {"driverId": 1}, token=admin)
check("admin pickup", s == 200 and b.get("status") == "Active" and b.get("pickupAt"), f"{s} {b.get('status') if s == 200 else b}")
# temp car has ownerId owner_786 - verify 90/10 payout on its booking
s, ot = mk_order([temp_id], time.strftime("%Y-%m-%d", time.gmtime(time.time() + 86400 * 40)),
                 time.strftime("%Y-%m-%d", time.gmtime(time.time() + 86400 * 41)), cust)
# pay + pickup the temp-car booking
if s == 201:
    otid = ot["id"]
    s, pt = req("POST", "/api/payments/submit", {"bookingId": otid, "method": "Cash on pickup", "amount": ot["totals"]["total"], "tid": "TIDCASH1", "receiptDoc": doc_id}, cust)
    # cash-on-pickup booking: verify still possible? it's Pending Verification; verify it
    s, pv = req("POST", f"/api/payments/{pt['id']}/review", {"action": "verify"}, token=admin)
    s, b = req("POST", f"/api/bookings/{otid}/pickup", {}, token=admin)
    check("temp booking pickup", s == 200, f"{s} {b}")
    ow0 = req("GET", "/api/wallet", token=admin)[1].get("ownerWallets", {})
    bal_before = req("GET", "/api/wallet", token=admin)[1].get("balance")
    rental = ot["items"][0]["price"]["rental"]
    s, b = req("POST", f"/api/bookings/{otid}/return", {"actualReturn": ot["items"][0]["endDt"]}, token=admin)
    check("on-time return Completed", s == 200 and b.get("status") == "Completed" and b.get("paymentStatus") == "Settled", f"{s} {b.get('status') if s==200 else b}")
    expected_share = int(rental * 0.9 + 0.5)  # half-up, matching Java Math.round
    ow1 = req("GET", "/api/wallet", token=admin)[1].get("ownerWallets", {})
    check("owner wallet credited 90%", ow1.get("owner_786", 0) - ow0.get("owner_786", 0) == expected_share, f"delta={ow1.get('owner_786', 0) - ow0.get('owner_786', 0)} expected={expected_share}")
    bal_after = req("GET", "/api/wallet", token=admin)[1].get("balance")
    check("admin wallet deducted payout", bal_after == bal_before - expected_share, f"before={bal_before} after={bal_after}")
# late return on order1 (2h late, grace 30)
late_end = (time.gmtime(time.time() + 86400 * 3.083))
s, b = req("POST", f"/api/bookings/{oid}/return", {"actualReturn": d2 + "T11:30"}, token=admin)
check("late return Completed + Late Charges Due", s == 200 and b.get("status") == "Completed" and b.get("paymentStatus") == "Late Charges Due" and b.get("extraCharges", 0) > 0, f"{s} {b}")
hourly = car.get("hourlyRate") or max(1, car.get("rate", 0) // 10)
expected_charges = 2 * hourly  # 2.5h late - 30min grace = 2h -> 2 extra hours
check("late charges = 2 x hourly", b.get("extraCharges") == expected_charges, f"got={b.get('extraCharges')} expected={expected_charges} (hourly={hourly})")
check("finalAmount = total + charges", b.get("finalAmount") == order1["totals"]["total"] + expected_charges, f"{b.get('finalAmount')} vs {order1['totals']['total'] + expected_charges}")

print("== 10. Cancellation ==")
s, o4 = mk_order([fleet[5]["id"]], time.strftime("%Y-%m-%d", time.gmtime(time.time() + 86400 * 50)),
                 time.strftime("%Y-%m-%d", time.gmtime(time.time() + 86400 * 51)), cust)
oid4 = o4.get("id")
rental4 = o4["items"][0]["price"]["rental"]
fee4 = round(rental4 * 0.05)
wb = req("GET", "/api/wallet", token=admin)[1].get("balance")
s, b = req("POST", f"/api/bookings/{oid4}/cancel", {}, token=cust2)
check("cancel someone else 403", s == 403, f"{s}")
s, b = req("POST", f"/api/bookings/{oid4}/cancel", {}, token=cust)
check("owner cancel 200", s == 200 and b.get("status") == "Cancelled" and b.get("cancellationFee") == fee4, f"{s} {b.get('status') if s==200 else b}")
wa = req("GET", "/api/wallet", token=admin)[1].get("balance")
check("cancellation fee to wallet", wa == wb + fee4, f"before={wb} after={wa} fee={fee4}")
s, b = req("POST", f"/api/bookings/{oid4}/cancel", {}, token=admin)
check("double cancel 409", s == 409, f"{s}")
s, b = req("POST", f"/api/bookings/{oid}/cancel", {}, token=admin)
check("cancel completed 409", s == 409, f"{s}")

print("== 11. Reviews ==")
s, b = req("POST", "/api/reviews", {"bookingId": oid, "carId": car_id, "rating": 5, "body": "Great service!"}, cust)
check("review completed booking 201", s == 201 and b.get("status") == "Pending", f"{s} {b}")
s, b = req("POST", "/api/reviews", {"bookingId": oid, "carId": car_id, "rating": 4, "body": "again"}, cust)
check("second review same booking 409", s == 409, f"{s}")
s, b = req("POST", "/api/reviews", {"bookingId": oid2, "carId": car_id, "rating": 4, "body": "not done"}, cust)
check("review non-completed 409", s == 409, f"{s}")
s, b = req("POST", "/api/reviews", {"bookingId": oid, "carId": car_id, "rating": 9, "body": "x"}, cust)
check("rating >5 422", s == 422, f"{s}")
s, b = req("GET", f"/api/reviews/car/{car_id}")
check("public car reviews (pending hidden)", s == 200 and len(b) == 0, f"{s} {b}")
s, reviews = req("GET", "/api/reviews", token=admin)
rid = None
for r in reviews:
    if r.get("bookingId") == oid:
        rid = r.get("id")
check("admin sees pending review", rid is not None, f"{reviews[:1]}")
s, b = req("POST", f"/api/reviews/{rid}/review", {"status": "Approved"}, token=admin)
check("approve review", s == 200, f"{s}")
s, b = req("GET", f"/api/reviews/car/{car_id}")
check("public shows approved", s == 200 and len(b) == 1 and b[0].get("rating") == 5, f"{s} {b}")
s, b = req("POST", f"/api/reviews/{rid}/review", {"status": "Hidden"}, token=admin)
check("hide review", s == 200, f"{s}")
s, b = req("GET", f"/api/reviews/car/{car_id}")
check("public hides hidden", s == 200 and len(b) == 0, f"{s}")

print("== 12. Chat ==")
s, t = req("POST", "/api/chats/send", {"text": "Hello, when is my car ready?", "from": "user"}, cust)
check("customer chat 201", s == 201 and t.get("unreadAdmin") == 1 and len(t.get("messages", [])) == 1, f"{s} {t}")
first_msg_time = t["messages"][0]["time"] if s == 201 and t.get("messages") else ""
s, t = req("POST", "/api/chats/send", {"text": "Hi! It will be ready soon.", "from": "admin", "userId": cust_id, "userName": "APEX Admin"}, token=admin)
check("admin chat reply", s == 201 and t.get("unreadUser") == 1 and len(t.get("messages", [])) == 2, f"{s} {t}")
s, t = req("POST", "/api/chats/send", {"text": "Hello, when is my car ready?", "from": "user", "time": first_msg_time}, cust)
check("duplicate message deduped", s == 201 and len(t.get("messages", [])) == 2, f"{s} msgs={len(t.get('messages', []))}")
s, chats = req("GET", "/api/chats", token=admin)
check("admin lists chats", s == 200 and any(c.get("userId") == cust_id for c in chats), f"{s}")
s, b = req("POST", "/api/chats/send", {"text": "", "from": "user"}, cust)
check("empty chat 422", s == 422, f"{s}")
s, b = req("POST", "/api/chats/send", {"text": "Impersonated reply", "from": "admin", "userId": "someone-else"}, cust)
check("customer cannot impersonate admin", s == 403, f"{s} {b}")
s, b = req("POST", "/api/chats/send", {"text": "Impersonated user", "from": "user", "userId": "someone-else"}, cust)
check("customer cannot message as another user", s == 403, f"{s} {b}")
s, b = req("GET", "/api/chats/mine", token=cust)
check("customer can see own thread", s == 200 and b.get("userId") == cust_id and len(b.get("messages", [])) == 2, f"{s} {b}")
s, b = req("GET", "/api/chats", token=cust)
check("customer cannot list all threads", s == 403, f"{s} {b}")
s, b = req("GET", "/api/live", token=cust2)
check("other customer's live chat is isolated", s == 200 and all(c.get("userId") != cust_id for c in b.get("chats", [])), f"{s} {b}")
s, b = req("GET", "/api/live", token=cust)
check("customer sees reply notification for exact chat", s == 200 and any(n.get("link") == f"chat/{cust_id}" for n in b.get("notifications", [])), f"{s} {b.get('notifications')}")
s, b = req("POST", "/api/chats/read", {}, cust)
check("customer clears only their unread replies", s == 200 and b.get("unreadUser") == 0 and b.get("unreadAdmin") == 1, f"{s} {b}")
s, b = req("POST", "/api/chats/read", {"userId": cust_id}, token=admin)
check("admin clears only customer thread's admin unread", s == 200 and b.get("unreadAdmin") == 0, f"{s} {b}")

print("== 13. Wallet admin ops ==")
s, w = req("GET", "/api/wallet", token=admin)
check("wallet shape", s == 200 and "balance" in w and "ownerWallets" in w and "transactions" in w, f"{s}")
s, mine = req("GET", "/api/wallet/mine", token=cust)
check("customer only sees own owner balance", s == 200 and mine == {"balance": 0} and "ownerWallets" not in mine, f"{s} {mine}")
s, b = req("GET", "/api/wallet/mine")
check("private wallet needs sign-in", s == 401, f"{s} {b}")
s, b = req("POST", "/api/wallet/add-cash", {"amount": 5000, "note": "cash in"}, token=admin)
check("add cash", s == 200 and b.get("balance") == w.get("balance") + 5000, f"{s} {b}")
s, b = req("POST", "/api/wallet/withdraw", {"amount": w.get("balance") + 999999, "account": "owner_786"}, token=admin)
check("withdraw over balance 409", s == 409, f"{s} {b}")
s, b = req("POST", "/api/wallet/withdraw", {"amount": 1000, "account": "owner_786"}, token=admin)
check("withdraw", s == 200 and b.get("status") == "Withdrawn", f"{s} {b}")
s, b = req("POST", "/api/payments/record", {"amount": 2500, "type": "bank", "note": "bank deposit"}, token=admin)
check("record payment", s == 200, f"{s} {b}")

print("== 14. Stats ==")
s, b = req("GET", "/api/stats", token=cust)
check("stats customer 403", s == 403, f"{s}")
s, b = req("GET", "/api/stats", token=admin)
st = b.get("stats", {}) if s == 200 else {}
need = ["revenue", "walletBalance", "payPending", "payPendingAmount", "payVerified", "payVerifiedAmount",
        "bookings", "rentedNow", "pending", "completed", "depositHeld", "extraCollected", "cars",
        "users", "userListedCars", "driversApproved", "driversPending", "docsPending", "reviewsPending",
        "unreadMessages", "withdrawPending"]
check("stats all keys", s == 200 and all(k in st for k in need), f"missing={[k for k in need if k not in st]}")
check("stats cars=14 (13+temp)", st.get("cars") == 14, f"cars={st.get('cars')}")
check("stats users>=3", st.get("users", 0) >= 3, f"users={st.get('users')}")
check("stats completed>=2", st.get("completed", 0) >= 2, f"completed={st.get('completed')}")
check("stats revenue>0", st.get("revenue", 0) > 0, f"revenue={st.get('revenue')}")
check("stats docsPending==0 (verified)", st.get("docsPending") == 0, f"docsPending={st.get('docsPending')}")

print("== 15. Settings ==")
s, cfg = req("GET", "/api/settings")
check("public settings", s == 200 and cfg.get("officeAddress"), f"{s}")
s, b = req("PUT", "/api/settings", {**cfg, "driverRate": 4600}, token=cust)
check("customer settings 403", s == 403, f"{s}")
s, b = req("PUT", "/api/settings", {**cfg, "driverRate": 4600}, token=admin)
check("admin settings", s == 200, f"{s}")
s, cfg2 = req("GET", "/api/settings")
check("settings persisted", cfg2.get("driverRate") == 4600, f"{cfg2.get('driverRate')}")
req("PUT", "/api/settings", cfg, token=admin)  # restore

print("== 16. Sync (admin) ==")
s, bs = req("GET", "/api/bootstrap", token=admin)
state = {k: bs.get(k) for k in ("fleet", "drivers", "users", "orders", "applications", "notifications", "chats", "bannedCNICs", "config")}
# strip identity from orders for sync realism (admin UI keeps them; keep as-is)
state["adminWallet"] = {"balance": -1}  # must be ignored
state["ownerWallets"] = {"hacked": 999999}  # must be ignored
s, b = req("PUT", "/api/sync", state, token=cust)
check("customer sync 403", s == 403, f"{s}")
s, b = req("PUT", "/api/sync", state, token=admin)
check("sync 200", s == 200 and b.get("status") == "synced", f"{s} {b}")
s, w = req("GET", "/api/wallet", token=admin)
check("sync did NOT touch ownerWallets", w.get("ownerWallets", {}).get("hacked") is None, f"{w.get('ownerWallets')}")
s, bs2 = req("GET", "/api/bootstrap", token=admin)
check("sync preserved orders count", len(bs2.get("orders", [])) == len(bs.get("orders", [])), f"{len(bs2.get('orders', []))} vs {len(bs.get('orders', []))}")
check("sync preserved users", len(bs2.get("users", [])) == len(bs.get("users", [])), f"{len(bs2.get('users', []))} vs {len(bs.get('users', []))}")
# A stale browser can have invalid records in unrelated collections. The
# frontend sends only the edited keys; verify the backend accepts that patch.
s, b = req("PUT", "/api/sync", {"users": [{"id": "old-cache-without-username"}]}, token=admin)
check("invalid legacy user reports specific 422", s == 422 and "username" in b.get("error", ""), f"{s} {b}")
settings_before = req("GET", "/api/settings")[1]
s, b = req("PUT", "/api/sync", {"config": {**settings_before, "driverRate": 7600}}, token=admin)
check("config-only sync avoids unrelated invalid users", s == 200 and b.get("status") == "synced", f"{s} {b}")
check("config-only sync persisted", req("GET", "/api/settings")[1].get("driverRate") == 7600, "")
req("PUT", "/api/sync", {"config": settings_before}, token=admin)  # restore
# sync with an overlapping order must fail atomically
bad_state = dict(state)
bad_orders = [o for o in bs.get("orders", []) if o.get("id") != oid]
# order3 (fleet[4], +30d..+31d) is still Pending Verification -> non-terminal:
# a synced booking overlapping it must be rejected atomically.
c4 = fleet[4]["id"]
d30 = time.strftime("%Y-%m-%d", time.gmtime(time.time() + 86400 * 30))
d31 = time.strftime("%Y-%m-%d", time.gmtime(time.time() + 86400 * 31))
clash = {"id": "BCLASH1", "userId": cust_id, "name": "Clash", "phone": "03001234567",
         "identityType": "CNIC", "identity": "3520212345678", "payment": "JazzCash",
         "status": "Confirmed", "startDt": d30 + "T09:00", "endDt": d31 + "T09:00",
         "items": [{"carId": c4, "start": d30, "end": d31, "city": "Faisalabad", "service": "Self-drive",
                    "startDt": d30 + "T09:00", "endDt": d31 + "T09:00",
                    "price": {"rental": 100, "total": 100}}],
         "totals": {"rental": 100, "total": 100}}
bad_state["orders"] = bad_orders + [clash]
s, b = req("PUT", "/api/sync", bad_state, token=admin)
check("sync with double-booking 409", s == 409 and "clash" in str(b.get("error", "")).lower() or "double" in str(b.get("error", "")).lower(), f"{s} {b}")
s, bs3 = req("GET", "/api/bootstrap", token=admin)
check("failed sync rolled back (no BCLASH1)", all(o.get("id") != "BCLASH1" for o in bs3.get("orders", [])), "")

print("== 16a. Recipient-only notification cleanup ==")
s, b = req("GET", "/api/notifications/mine", token=admin)
admin_alerts_before = len(b) if s == 200 else 0
s, b = req("DELETE", "/api/notifications/mine", token=cust)
check("customer can clear own notifications", s == 200 and req("GET", "/api/notifications/mine", token=cust)[1] == [], f"{s} {b}")
s, b = req("GET", "/api/notifications/mine", token=admin)
check("customer clear does not touch admin inbox", s == 200 and len(b) == admin_alerts_before, f"{s} {b}")

print("== 17. Session invalidation ==")
s, b = req("POST", "/api/auth/logout", {}, token=cust2)
check("logout 200", s == 200, f"{s}")
s, b = req("GET", "/api/auth/me", token=cust2)
check("token dead after logout", s == 401, f"{s}")

print("== 18. Shared-IP polling does not exhaust the normal API quota ==")
for endpoint, label, token in [
    ("/api/live", "authenticated live feed", cust),
    (f"/api/availability/fleet?start={d1}T09:00&end={d2}T09:00", "fleet availability", None),
]:
    failed_at = None
    for n in range(305):  # above the 300/10min quota for ordinary requests
        status, _ = req("GET", endpoint, token=token)
        if status != 200:
            failed_at = (n + 1, status)
            break
    check(label + " has its own bounded polling quota", failed_at is None, f"first failure: {failed_at}")
status, _ = req("GET", "/api/settings")
check("ordinary API quota remains available after polling", status == 200, f"{status}")

print()
print(f"===== RESULTS: {PASS} passed, {FAIL} failed =====")
if FAILURES:
    print("Failed:", *FAILURES, sep="\n  - ")
sys.exit(1 if FAIL else 0)
