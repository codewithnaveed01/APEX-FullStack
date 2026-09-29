#!/usr/bin/env python3
"""Owner marketplace acceptance tests against a disposable running APEX server."""
import base64
import json
import os
import time
import urllib.error
import urllib.request
import uuid

BASE = os.environ.get("APEX_TEST_BASE", "http://127.0.0.1:3030").rstrip("/")
ADMIN_PASS = os.environ.get("APEX_TEST_ADMIN_PASS", "admin1234")
PASSED = 0
FAILED = 0


def request(method, path, body=None, token=None, raw=False):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            payload = response.read()
            return response.status, payload if raw else (json.loads(payload) if payload else None)
    except urllib.error.HTTPError as error:
        payload = error.read()
        try:
            payload = json.loads(payload)
        except Exception:
            pass
        return error.code, payload


def check(name, condition, detail=""):
    global PASSED, FAILED
    if condition:
        PASSED += 1
        print("  PASS", name)
    else:
        FAILED += 1
        print("  FAIL", name, detail)


def register(prefix):
    suffix = uuid.uuid4().hex[:8]
    username = prefix + suffix
    status, result = request("POST", "/api/auth/register", {
        "username": username,
        "email": username + "@example.test",
        "name": prefix.title() + " Test",
        "phone": "03001234567",
        "password": "owner-test-1234",
    })
    assert status == 201, result
    return result["token"], result["user"]["id"], result["user"]


PNG = "data:image/png;base64," + base64.b64encode(base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
)).decode()


def photos(token, count=3):
    ids = []
    for _ in range(count):
        status, result = request("POST", "/api/uploads", {
            "dataUrl": PNG, "kind": "photo", "ownerType": "user"
        }, token)
        assert status == 201, result
        ids.append(result["id"])
    return ids


def application(owner_token, user, photo_ids, registration):
    return request("POST", "/api/applications", {
        "owner": user["name"], "phone": user["phone"], "email": user["email"],
        "brand": "Suzuki", "model": "Alto", "year": 2024,
        "registration": registration, "cnic": "3520212345678", "license": "LHR-OWNER-123",
        "city": "Lahore", "mileage": 12000, "condition": "Excellent", "rate": 5200,
        "preference": "Either", "availableFrom": time.strftime("%Y-%m-%d"),
        "notes": "Owner flow test", "photoIds": photo_ids,
    }, owner_token)


print("== Owner vehicle marketplace ==")
status, admin_login = request("POST", "/api/auth/login", {"username": "admin", "password": ADMIN_PASS})
assert status == 200, admin_login
admin = admin_login["token"]
owner, owner_id, owner_user = register("owner")
other, other_id, _ = register("other")
renter, renter_id, renter_user = register("renter")

first_photos = photos(owner)
status, bad = application(owner, owner_user, first_photos[:2], "OWN-2IMG")
check("exactly three images enforced by backend", status == 422 and "Exactly 3" in bad.get("error", ""), f"{status} {bad}")

status, app1 = application(owner, owner_user, first_photos, "OWN-001")
check("three-image application saved", status == 201 and len(app1.get("photoIds", [])) == 3, f"{status} {app1}")
app1_id = app1.get("id")
status, persisted = request("GET", "/api/applications/" + app1_id, token=owner)
check("image references persist in application", status == 200 and persisted.get("photoIds") == first_photos, f"{status} {persisted}")
for image_id in first_photos:
    s, doc = request("GET", "/api/documents/" + str(image_id), token=owner)
    check("PostgreSQL image bytes remain readable", s == 200 and doc.get("dataUrl", "").startswith("data:image/png"), f"{s}")

status, approved1 = request("PUT", "/api/applications/" + app1_id, {
    "verification": "Verified", "status": "Approved for onboarding", "note": "approved"
}, admin)
car1 = (approved1 or {}).get("car", {})
check("approval creates live fleet car", status == 200 and car1.get("applicationId") == app1_id and car1.get("ownerId") == owner_id, f"{status} {approved1}")
car1_id = car1.get("id")
status, public_image = request("GET", "/api/vehicle-images/" + str(first_photos[0]), raw=True)
check("approved original image has durable public endpoint", status == 200 and public_image.startswith(b"\x89PNG"), f"{status}")
status, duplicate = request("PUT", "/api/applications/" + app1_id, {
    "verification": "Verified", "status": "Approved for onboarding"
}, admin)
check("same application cannot be approved twice", status == 409, f"{status} {duplicate}")

second_photos = photos(owner)
status, app2 = application(owner, owner_user, second_photos, "OWN-002")
app2_id = app2.get("id")
status, approved2 = request("PUT", "/api/applications/" + app2_id, {
    "verification": "Verified", "status": "Approved for onboarding"
}, admin)
car2 = (approved2 or {}).get("car", {})
check("same owner/model creates a separate listing", status == 200 and car2.get("id") != car1_id and car2.get("name") == car1.get("name"), f"{status} {approved2}")
car2_id = car2.get("id")
status, fleet = request("GET", "/api/fleet")
check("both same-model application IDs are in fleet", sum(1 for c in fleet if c.get("applicationId") in (app1_id, app2_id)) == 2, "")

status, denied = request("PUT", f"/api/owner/cars/{car1_id}/edit", {"rate": 9900}, other)
check("unauthorized owner cannot edit another listing", status == 403, f"{status} {denied}")
status, denied = request("DELETE", f"/api/owner/cars/{car1_id}", token=other)
check("unauthorized owner cannot delete another listing", status == 403, f"{status} {denied}")

status, pending_edit = request("PUT", f"/api/owner/cars/{car1_id}/edit", {
    "model": "Alto Updated", "rate": 6100
}, owner)
check("owner edit returns to pending approval", status == 200 and pending_edit.get("status") == "Submitted" and pending_edit.get("editStatus") == "Pending approval", f"{status} {pending_edit}")
status, still_live = request("GET", f"/api/fleet/{car1_id}")
check("pending owner edit does not change live fleet", status == 200 and still_live.get("name") == "Suzuki Alto" and still_live.get("rate") == 5200, f"{still_live}")
status, approved_edit = request("PUT", "/api/applications/" + app1_id, {
    "verification": "Verified", "status": "Approved for onboarding"
}, admin)
status2, now_live = request("GET", f"/api/fleet/{car1_id}")
check("admin approval publishes owner edit", status == 200 and status2 == 200 and now_live.get("name") == "Suzuki Alto Updated" and now_live.get("rate") == 6100, f"{status} {now_live}")

start = time.strftime("%Y-%m-%d", time.gmtime(time.time() + 86400 * 60))
end = time.strftime("%Y-%m-%d", time.gmtime(time.time() + 86400 * 61))
status, booking = request("POST", "/api/orders", {
    "name": renter_user["name"], "phone": renter_user["phone"], "email": renter_user["email"],
    "identityType": "CNIC", "identity": "3520212345679", "pickupMode": "Office",
    "destination": "Lahore", "payment": "Cash on pickup",
    "items": [{"carId": car1_id, "start": start, "end": end, "city": "Lahore",
               "service": "Self-drive", "startDt": start + "T09:00", "endDt": end + "T09:00"}],
}, renter)
booking_id = booking.get("id") if status == 201 else ""
check("owner car can be booked", status == 201, f"{status} {booking}")
status, blocked = request("DELETE", f"/api/owner/cars/{car1_id}", token=owner)
check("owner deletion blocked by active/pending booking", status == 409 and "booking" in blocked.get("error", "").lower(), f"{status} {blocked}")
status, owner_live = request("GET", "/api/notifications/mine", token=owner)
check("booking notification includes car, booking, status and earning", status == 200 and any(
    booking_id in n.get("msg", "") and "Suzuki Alto Updated" in n.get("msg", "") and "Expected earning" in n.get("msg", "")
    for n in owner_live), f"{owner_live}")

status, _ = request("POST", f"/api/bookings/{booking_id}/pickup", {}, admin)
check("admin starts owner rental", status == 200, "")
status, completed = request("POST", f"/api/bookings/{booking_id}/return", {"actualReturn": end + "T09:00"}, admin)
check("rental completes and settles", status == 200 and completed.get("ownerPayoutDone") is True and completed.get("paymentStatus") == "Settled", f"{status} {completed}")
status, wallet1 = request("GET", "/api/owner/wallet", token=owner)
status2, wallet2 = request("GET", "/api/owner/wallet", token=owner)
expected = round(booking["items"][0]["price"]["rental"] * 0.9)
check("owner 90 percent wallet persisted", status == 200 and status2 == 200 and wallet1.get("availableBalance", 0) >= expected and wallet2 == wallet1, f"{wallet1}")
check("completed payout history is backend-driven", any(p.get("bookingId") == booking_id and p.get("status") == "Completed" for p in wallet1.get("payoutHistory", [])), f"{wallet1}")

status, saved_account = request("PUT", "/api/owner/payout-account", {
    "method": "JazzCash", "accountTitle": "Owner Test", "phone": "03001234567",
    "accountNumber": "", "iban": ""
}, owner)
status2, loaded_account = request("GET", "/api/owner/payout-account", token=owner)
check("payout account persists securely through API", status == 200 and status2 == 200 and loaded_account.get("phone") == "03001234567", f"{saved_account} {loaded_account}")
status, admin_account = request("GET", f"/api/admin/owners/{owner_id}/payout-account", token=admin)
check("admin can view owner payout account for settlement", status == 200 and admin_account.get("method") == "JazzCash", f"{status} {admin_account}")

status, deleted = request("DELETE", f"/api/owner/cars/{car1_id}", token=owner)
check("owner deletion succeeds after booking and payout clear", status == 200 and deleted.get("status") == "deleted", f"{status} {deleted}")
status, _ = request("GET", f"/api/fleet/{car1_id}")
check("deleted car is removed from backend fleet", status == 404, f"{status}")
status, deleted2 = request("DELETE", f"/api/owner/cars/{car2_id}", token=owner)
check("owner can delete never-booked clear listing", status == 200, f"{status} {deleted2}")

print(f"\nOwner marketplace: {PASSED} passed, {FAILED} failed")
raise SystemExit(1 if FAILED else 0)
