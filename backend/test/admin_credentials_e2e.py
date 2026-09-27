#!/usr/bin/env python3
"""Destructive credential-rotation tests. Run ONLY against a disposable local DB.

Requires APEX_TEST_ALLOW_ADMIN_ROTATION=disposable-local-db and
APEX_TEST_BASE=http://127.0.0.1:<port>. Run once with 'initial' and again
with 'restart' after restarting the backend on the same disposable database.
"""
from concurrent.futures import ThreadPoolExecutor
import json
import os
from pathlib import Path
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

BASE = os.environ.get("APEX_TEST_BASE", "").rstrip("/")
assert os.environ.get("APEX_TEST_ALLOW_ADMIN_ROTATION") == "disposable-local-db", \
    "Credential tests are destructive; use a disposable local database only"
assert urllib.parse.urlparse(BASE).hostname in ("localhost", "127.0.0.1"), \
    "Credential tests must not run against a public host"
STATE = Path(os.environ.get("APEX_TEST_RUN_STATE", "/tmp/apex-admin-credentials-state.json"))
MODE = sys.argv[1] if len(sys.argv) > 1 else "initial"
OLD_PASSWORD = os.environ.get("APEX_TEST_ADMIN_PASS", "admin1234")
NEW_PASSWORD = "LocallyTestedAdminPass2026!"
SECOND_PASSWORD = "RotatedAgainForLocalTest2026!"
CUSTOMER_PASSWORD = "LocalCustomerPass2026!"
CRED_PATH = "/api/admin/credentials"


def req(method, path, body=None, token=None):
    data = None if body is None else json.dumps(body).encode("utf-8")
    headers = {"Content-Type": "application/json", "X-Forwarded-For": "192.0.2.87"}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        response = urllib.request.urlopen(request, timeout=30)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        payload = response.read()
        return response.status, json.loads(payload) if payload else {}


def expect(status, method, path, body=None, token=None):
    received, payload = req(method, path, body, token)
    assert received == status, f"{method} {path}: expected {status}, got {received}: {payload}"
    return payload


def no_passwords(value):
    if isinstance(value, dict):
        for key, item in value.items():
            assert key.lower() not in ("password", "passwordhash", "password_hash", "pass"), key
            no_passwords(item)
    elif isinstance(value, list):
        for item in value:
            no_passwords(item)


def login(username, password):
    return expect(200, "POST", "/api/auth/login", {"username": username, "password": password})


if MODE == "initial":
    nonce = uuid.uuid4().hex[:9]
    new_name = "qa_admin_" + nonce
    customer_name = "qa_customer_" + nonce
    token1 = login("admin", OLD_PASSWORD)["token"]
    token2 = login("admin", OLD_PASSWORD)["token"]
    customer = expect(201, "POST", "/api/auth/register", {
        "username": customer_name, "email": customer_name + "@example.test",
        "name": "QA Customer", "phone": "03001234567", "password": CUSTOMER_PASSWORD
    })
    no_passwords(customer)
    customer_token = customer["token"]
    change = {"username": new_name, "currentPassword": OLD_PASSWORD, "newPassword": NEW_PASSWORD}
    expect(401, "PUT", CRED_PATH, change)
    expect(403, "PUT", CRED_PATH, change, customer_token)
    expect(401, "PUT", CRED_PATH, {**change, "currentPassword": "wrong"}, token1)
    expect(422, "PUT", CRED_PATH, {**change, "username": "not a username"}, token1)
    expect(422, "PUT", CRED_PATH, {**change, "newPassword": "weak"}, token1)
    expect(409, "PUT", CRED_PATH, {**change, "username": customer_name}, token1)
    expect(422, "PUT", CRED_PATH, {**change, "username": "admin", "newPassword": ""}, token1)
    # Username-only change preserves the original password and revokes sessions.
    temporary_name = "qa_temp_" + nonce
    renamed = expect(200, "PUT", CRED_PATH, {"username": temporary_name,
        "currentPassword": OLD_PASSWORD, "newPassword": ""}, token1)
    expect(401, "GET", "/api/auth/me", token=token1)
    assert login(temporary_name, OLD_PASSWORD)["role"] == "admin"
    updated = expect(200, "PUT", CRED_PATH, {**change,
        "currentPassword": OLD_PASSWORD}, renamed["token"])
    assert updated["username"] == new_name and updated["role"] == "admin"
    assert updated["token"] not in (token1, token2)
    no_passwords(updated)
    expect(401, "GET", "/api/auth/me", token=token1)
    expect(401, "GET", "/api/auth/me", token=token2)
    assert expect(200, "GET", "/api/auth/me", token=updated["token"])["username"] == new_name
    expect(401, "POST", "/api/auth/login", {"username": "admin", "password": OLD_PASSWORD})
    assert login(new_name, NEW_PASSWORD)["role"] == "admin"
    assert login(customer_name, CUSTOMER_PASSWORD)["role"] == "customer"

    # A stale cached full-state sync cannot rename/reset the admin or add another.
    expect(200, "PUT", "/api/sync", {"users": [{"id":"UADMIN", "username":"admin",
        "role":"admin", "password":"forged-password"}]}, updated["token"])
    expect(403, "PUT", "/api/sync", {"users": [{"id":"UFAKE",
        "username":"forged-admin", "role":"admin", "password":"forged-password"}]}, updated["token"])
    assert login(new_name, NEW_PASSWORD)["role"] == "admin"
    data = expect(200, "GET", "/api/bootstrap", token=updated["token"])
    no_passwords(data)
    assert any(user.get("username") == new_name for user in data["users"])
    state = {"new_name": new_name, "customer_name": customer_name}
    STATE.write_text(json.dumps(state), encoding="utf-8")
    STATE.chmod(0o600)
    print("PASS: admin Settings API rejects unauthorized/weak/duplicate updates, revokes all old sessions, keeps customer login and protects sync")
elif MODE == "restart":
    state = json.loads(STATE.read_text(encoding="utf-8"))
    expect(401, "POST", "/api/auth/login", {"username": "admin", "password": OLD_PASSWORD})
    admin = login(state["new_name"], NEW_PASSWORD)
    assert admin["role"] == "admin"
    assert login(state["customer_name"], CUSTOMER_PASSWORD)["role"] == "customer"
    assert login("qa_legacy", "LegacyPlaintextPassword2026")["role"] == "customer"
    assert login("qa_legacy_json", "LegacyJsonPassword2026")["role"] == "customer"
    data = expect(200, "GET", "/api/bootstrap", token=admin["token"])
    no_passwords(data)
    assert sum(user.get("role") == "admin" for user in data["users"]) == 1
    # Competing logins must never mint an old-password session that survives
    # rotation. The login row lock is held until the new session is committed.
    with ThreadPoolExecutor(max_workers=6) as pool:
        old_logins = [pool.submit(req, "POST", "/api/auth/login", {
            "username": state["new_name"], "password": NEW_PASSWORD}) for _ in range(6)]
        password_only = expect(200, "PUT", CRED_PATH, {"username": state["new_name"],
            "currentPassword": NEW_PASSWORD, "newPassword": SECOND_PASSWORD}, admin["token"])
        old_results = [future.result() for future in old_logins]
    assert password_only["username"] == state["new_name"]
    expect(401, "GET", "/api/auth/me", token=admin["token"])
    for status, result in old_results:
        assert status in (200, 401), f"unexpected old-password login: {status} {result}"
        if status == 200:
            expect(401, "GET", "/api/auth/me", token=result["token"])
    expect(401, "POST", "/api/auth/login", {"username": state["new_name"], "password": NEW_PASSWORD})
    assert login(state["new_name"], SECOND_PASSWORD)["role"] == "admin"
    no_passwords(password_only)
    print("PASS: both username-only and password-only changes, concurrent logins revoked, restarts, legacy plaintext/JSON migration, one admin, customer logins")
else:
    raise ValueError("usage: admin_credentials_e2e.py [initial|restart]")
