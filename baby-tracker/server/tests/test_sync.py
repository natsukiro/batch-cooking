"""Tests du serveur de synchro : lancez `pytest` depuis le dossier server/."""
import os
import sys

import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

KEY_A = "A" * 32
KEY_B = "B" * 32


@pytest.fixture()
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("PR_DB", str(tmp_path / "test.db"))
    import importlib

    import app as server

    importlib.reload(server)
    server.app.config["TESTING"] = True
    with server.app.test_client() as c:
        yield c


def auth(key):
    return {"Authorization": f"Bearer {key}"}


def doc(uid, updated, data=None, deleted=False, kind="event"):
    return {"uid": uid, "kind": kind, "updatedAt": updated, "deleted": deleted, "data": data}


def sync(c, key, since=0, changes=()):
    r = c.post("/api/v1/sync", json={"since": since, "changes": list(changes)}, headers=auth(key))
    return r.status_code, r.get_json()


def test_health(client):
    assert client.get("/api/v1/health").get_json()["ok"] is True


def test_unknown_family_is_rejected(client):
    status, body = sync(client, KEY_A)
    assert status == 404 and body["error"] == "unknown_family"


def test_short_key_is_rejected(client):
    assert client.post("/api/v1/family", headers=auth("court")).status_code == 401


def test_two_devices_converge(client):
    assert client.post("/api/v1/family", headers=auth(KEY_A)).status_code == 200
    # Téléphone 1 envoie un biberon
    _, r1 = sync(client, KEY_A, 0, [doc("e1", 100, {"type": "BOTTLE"})])
    assert [d["uid"] for d in r1["changes"]] == ["e1"]
    # Téléphone 2 (curseur 0) récupère tout, puis envoie une couche
    _, r2 = sync(client, KEY_A, 0, [doc("e2", 110, {"type": "DIAPER"})])
    assert {d["uid"] for d in r2["changes"]} == {"e1", "e2"}
    # Téléphone 1 ne reçoit que la nouveauté
    _, r3 = sync(client, KEY_A, r1["cursor"])
    assert [d["uid"] for d in r3["changes"]] == ["e2"]


def test_last_writer_wins_and_deletes(client):
    client.post("/api/v1/family", headers=auth(KEY_A))
    sync(client, KEY_A, 0, [doc("e1", 200, {"v": "récent"})])
    # Une modification plus ancienne est ignorée
    _, r = sync(client, KEY_A, 0, [doc("e1", 150, {"v": "ancien"})])
    assert r["changes"][0]["data"] == {"v": "récent"}
    # Une suppression plus récente l'emporte
    _, r = sync(client, KEY_A, 0, [doc("e1", 300, deleted=True)])
    assert r["changes"][0]["deleted"] is True and r["changes"][0]["data"] is None


def test_families_are_isolated(client):
    client.post("/api/v1/family", headers=auth(KEY_A))
    client.post("/api/v1/family", headers=auth(KEY_B))
    sync(client, KEY_A, 0, [doc("secret", 1, {"x": 1})])
    _, r = sync(client, KEY_B, 0)
    assert r["changes"] == []


def test_paging(client):
    import app as server

    client.post("/api/v1/family", headers=auth(KEY_A))
    sync(client, KEY_A, 0, [doc(f"e{i}", i + 1, {"i": i}) for i in range(server.PAGE + 20)])
    _, p1 = sync(client, KEY_A, 0)
    assert p1["more"] is True and len(p1["changes"]) == server.PAGE
    _, p2 = sync(client, KEY_A, p1["cursor"])
    assert p2["more"] is False and len(p2["changes"]) == 20


def test_create_token(client, monkeypatch):
    monkeypatch.setenv("PR_CREATE_TOKEN", "jeton")
    assert client.post("/api/v1/family", headers=auth(KEY_A)).status_code == 403
    h = {**auth(KEY_A), "X-Create-Token": "jeton"}
    assert client.post("/api/v1/family", headers=h).status_code == 200


def test_join_page_does_not_need_key(client):
    r = client.get("/join")
    assert r.status_code == 200 and b"Petit Rythme" in r.data
