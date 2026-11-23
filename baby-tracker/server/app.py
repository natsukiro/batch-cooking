"""
Serveur de synchronisation de Petit Rythme.

Principe
--------
- Une « famille » est un espace partagé, identifié par une clé secrète générée par
  l'appli (le serveur n'en garde que l'empreinte SHA-256). La clé circule entre les
  parents via le lien d'invitation ; c'est elle qui donne accès aux données.
- Les données sont des documents JSON identifiés par un `uid`, avec un horodatage
  `updatedAt` : en cas de conflit, la modification la plus récente gagne.
- Chaque écriture acceptée reçoit un numéro de séquence croissant ; un téléphone
  demande « tout ce qui a changé depuis le numéro N » (curseur).

Le serveur ne comprend pas le contenu des documents : il stocke et redistribue.

Lancement local :  PR_DB=./data.db flask --app app run --port 8080
En production   :  voir Dockerfile / docker-compose.yml
"""

import hashlib
import json
import os
import sqlite3
import time

from flask import Flask, Response, g, jsonify, request

PROTOCOL = 1
PAGE = 500
KINDS = {"event", "appt", "profile", "timers"}

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = 8 * 1024 * 1024


def db_path():
    return os.environ.get("PR_DB", "/data/petitrythme.db")


def db():
    if "db" not in g:
        conn = sqlite3.connect(db_path(), timeout=10, isolation_level=None)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA journal_mode=WAL")
        conn.execute("PRAGMA foreign_keys=ON")
        g.db = conn
    return g.db


@app.teardown_appcontext
def close_db(_exc):
    conn = g.pop("db", None)
    if conn is not None:
        conn.close()


def init_db():
    conn = sqlite3.connect(db_path())
    conn.executescript(
        """
        CREATE TABLE IF NOT EXISTS families (
            id TEXT PRIMARY KEY,
            seq INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS docs (
            family TEXT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
            uid TEXT NOT NULL,
            kind TEXT NOT NULL,
            updated_at INTEGER NOT NULL,
            deleted INTEGER NOT NULL DEFAULT 0,
            data TEXT,
            seq INTEGER NOT NULL,
            PRIMARY KEY (family, uid)
        );
        CREATE INDEX IF NOT EXISTS docs_by_seq ON docs (family, seq);
        """
    )
    conn.close()


def error(status, code, message):
    return jsonify({"error": code, "message": message}), status


def family_from_request():
    """Renvoie l'identifiant de famille (empreinte de la clé), ou None."""
    auth = request.headers.get("Authorization", "")
    if not auth.startswith("Bearer "):
        return None
    key = auth[7:].strip()
    if len(key) < 20:
        return None
    return hashlib.sha256(key.encode("utf-8")).hexdigest()


@app.get("/api/v1/health")
def health():
    return jsonify({"ok": True, "app": "petit-rythme-sync", "protocol": PROTOCOL})


@app.post("/api/v1/family")
def create_family():
    """Crée l'espace d'une famille. Optionnellement protégé par PR_CREATE_TOKEN."""
    expected = os.environ.get("PR_CREATE_TOKEN")
    if expected and request.headers.get("X-Create-Token") != expected:
        return error(403, "create_forbidden", "Création d'espace non autorisée sur ce serveur.")
    fam = family_from_request()
    if fam is None:
        return error(401, "bad_key", "Clé famille absente ou trop courte.")
    conn = db()
    conn.execute(
        "INSERT OR IGNORE INTO families (id, seq, created_at) VALUES (?, 0, ?)",
        (fam, int(time.time() * 1000)),
    )
    return jsonify({"ok": True})


@app.post("/api/v1/sync")
def sync():
    """
    Corps : {"since": <curseur>, "changes": [{"uid", "kind", "updatedAt", "deleted", "data"}]}
    Réponse : {"cursor": <nouveau curseur>, "changes": [...], "more": bool}
    """
    fam = family_from_request()
    if fam is None:
        return error(401, "bad_key", "Clé famille absente ou trop courte.")
    body = request.get_json(silent=True)
    if not isinstance(body, dict):
        return error(400, "bad_request", "Corps JSON attendu.")
    since = body.get("since", 0)
    changes = body.get("changes", [])
    if not isinstance(since, int) or not isinstance(changes, list):
        return error(400, "bad_request", "Champs since/changes invalides.")

    conn = db()
    conn.execute("BEGIN IMMEDIATE")
    try:
        row = conn.execute("SELECT seq FROM families WHERE id = ?", (fam,)).fetchone()
        if row is None:
            conn.execute("ROLLBACK")
            return error(404, "unknown_family", "Espace famille inconnu : vérifiez le lien d'invitation.")
        seq = row["seq"]
        for c in changes:
            if not isinstance(c, dict):
                continue
            uid, kind, updated = c.get("uid"), c.get("kind"), c.get("updatedAt")
            if not isinstance(uid, str) or not uid or kind not in KINDS or not isinstance(updated, int):
                continue
            deleted = 1 if c.get("deleted") else 0
            data = c.get("data")
            data_text = None if deleted or data is None else json.dumps(data, ensure_ascii=False)
            cur = conn.execute(
                "SELECT updated_at FROM docs WHERE family = ? AND uid = ?", (fam, uid)
            ).fetchone()
            # La modification la plus récente gagne ; à égalité, on garde l'existant.
            if cur is not None and cur["updated_at"] >= updated:
                continue
            seq += 1
            conn.execute(
                "INSERT INTO docs (family, uid, kind, updated_at, deleted, data, seq) "
                "VALUES (?, ?, ?, ?, ?, ?, ?) "
                "ON CONFLICT(family, uid) DO UPDATE SET kind = excluded.kind, "
                "updated_at = excluded.updated_at, deleted = excluded.deleted, "
                "data = excluded.data, seq = excluded.seq",
                (fam, uid, kind, updated, deleted, data_text, seq),
            )
        conn.execute("UPDATE families SET seq = ? WHERE id = ?", (seq, fam))
        rows = conn.execute(
            "SELECT uid, kind, updated_at, deleted, data, seq FROM docs "
            "WHERE family = ? AND seq > ? ORDER BY seq LIMIT ?",
            (fam, since, PAGE + 1),
        ).fetchall()
        conn.execute("COMMIT")
    except Exception:
        conn.execute("ROLLBACK")
        raise

    more = len(rows) > PAGE
    rows = rows[:PAGE]
    out = [
        {
            "uid": r["uid"],
            "kind": r["kind"],
            "updatedAt": r["updated_at"],
            "deleted": bool(r["deleted"]),
            "data": None if r["data"] is None else json.loads(r["data"]),
        }
        for r in rows
    ]
    # Curseur = dernier document renvoyé (page suivante si `more`), sinon l'état courant.
    cursor = rows[-1]["seq"] if rows else seq
    return jsonify({"cursor": cursor, "changes": out, "more": more})


JOIN_PAGE = """<!doctype html>
<html lang="fr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Rejoindre Petit Rythme</title>
<style>
 body{font-family:system-ui,sans-serif;background:#FFF8F4;color:#221A1D;margin:0;padding:24px;max-width:520px}
 a.btn{display:block;text-align:center;background:#B4507E;color:#fff;padding:16px;border-radius:18px;
   text-decoration:none;font-weight:600;font-size:18px;margin:24px 0}
 code{background:#F3E4E8;padding:8px;border-radius:8px;display:block;word-break:break-all}
 @media (prefers-color-scheme: dark){body{background:#151216;color:#EBDFE3}code{background:#3A3237}}
</style></head><body>
<h1>🌙 Petit Rythme</h1>
<p>On vous invite à partager le suivi de bébé.</p>
<a id="open" class="btn" href="#">Ouvrir dans Petit Rythme</a>
<p>Si le bouton ne fait rien : installez l'appli, puis dans <b>Réglages → Synchronisation → Rejoindre</b>,
collez l'adresse complète de cette page.</p>
<p>Code d'invitation :</p><code id="code">—</code>
<script>
 var k = new URLSearchParams(location.hash.slice(1)).get('k') || '';
 document.getElementById('code').textContent = k || '(lien incomplet)';
 var deep = 'petitrythme://join?s=' + encodeURIComponent(location.origin) + '&k=' + encodeURIComponent(k);
 document.getElementById('open').href = 'intent://join?s=' + encodeURIComponent(location.origin) +
   '&k=' + encodeURIComponent(k) + '#Intent;scheme=petitrythme;package=app.petitrythme;end';
 if (!/Android/i.test(navigator.userAgent)) document.getElementById('open').href = deep;
</script>
</body></html>"""


@app.get("/join")
def join_page():
    # La clé est dans le fragment (#k=…) : elle n'est jamais envoyée au serveur ni journalisée.
    return Response(JOIN_PAGE, mimetype="text/html")


init_db()

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", "8080")))
