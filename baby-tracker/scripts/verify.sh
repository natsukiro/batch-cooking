#!/usr/bin/env bash
# Vérification complète avant toute livraison :
#   1. tests du serveur de synchro (pytest)
#   2. serveur lancé pour le test de synchro de bout en bout
#   3. tests de l'appli (logique, migration, synchro, captures d'écran) + APK release signé
#   4. contrôles : signature, version, contrastes du thème
# Les captures sont copiées dans review/screenshots/ pour les relectures UX/design.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
fail() { echo "✗ $*" >&2; exit 1; }

[ -f local.properties ] || [ -n "${ANDROID_HOME:-}" ] || scripts/setup-android-sdk.sh
SDK="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' local.properties)}"
BT="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"

echo "== 1. Serveur de synchro : tests"
if [ ! -x server/.venv/bin/python ]; then python3 -m venv server/.venv; fi
server/.venv/bin/pip install -q -r server/requirements-dev.txt
(cd server && .venv/bin/python -m pytest -q tests) || fail "tests du serveur"

echo "== 2. Serveur de synchro : démarrage pour le test de bout en bout"
PORT="$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1])')"
DBDIR="$(mktemp -d)"
(cd server && PR_DB="$DBDIR/e2e.db" .venv/bin/gunicorn --bind "127.0.0.1:$PORT" --workers 1 --threads 4 app:app \
  >"$DBDIR/server.log" 2>&1 & echo $! >"$DBDIR/pid")
trap 'kill "$(cat "$DBDIR/pid")" 2>/dev/null || true; rm -rf "$DBDIR"' EXIT
for _ in $(seq 1 30); do
  curl -sf --noproxy '*' "http://127.0.0.1:$PORT/api/v1/health" >/dev/null && break
  sleep 0.5
done
curl -sf --noproxy '*' "http://127.0.0.1:$PORT/api/v1/health" >/dev/null || fail "le serveur ne démarre pas (voir $DBDIR/server.log)"

echo "== 3. Appli : tests + APK release"
rm -rf app/build/outputs/roborazzi
PR_SYNC_SERVER="http://127.0.0.1:$PORT" ./gradlew --console=plain -q :app:testDebugUnitTest :app:assembleRelease \
  || fail "tests ou build de l'appli (rapport : app/build/reports/tests/testDebugUnitTest/index.html)"

python3 - <<'PY'
import glob, re, sys
total = failed = skipped = 0
for f in glob.glob("app/build/test-results/testDebugUnitTest/*.xml"):
    t = open(f, encoding="utf-8").read()
    m = re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"', t)
    total += int(m[1]); skipped += int(m[2]); failed += int(m[3]) + int(m[4])
print(f"   tests appli : {total} dont {failed} en échec, {skipped} ignorés")
sys.exit(1 if failed else 0)
PY

echo "== 4. Contrôles"
APK=app/build/outputs/apk/release/app-release.apk
"$BT/apksigner" verify --print-certs "$APK" 2>/dev/null | grep -q "SHA-256 digest: 0d5d67ff854dc4665bf223482b3195fd5eab29821d3f225ec952f3b3782ce76e" \
  || fail "APK non signé avec la clé du projet (les mises à jour par-dessus seraient impossibles)"
"$BT/aapt2" dump badging "$APK" 2>/dev/null | grep -E "^package" | sed 's/^/   /'
python3 scripts/check_contrast.py || fail "contrastes du thème"

mkdir -p review/screenshots
rm -f review/screenshots/*.png
cp app/build/outputs/roborazzi/build/outputs/roborazzi/*.png review/screenshots/ 2>/dev/null || true
echo "   captures : $(ls review/screenshots | wc -l) fichiers dans review/screenshots/"
echo "✓ Tout est vert. APK : $APK"
