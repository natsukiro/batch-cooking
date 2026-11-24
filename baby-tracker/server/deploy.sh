#!/usr/bin/env bash
# Installe ou met à jour le serveur de synchro Petit Rythme sur CETTE machine (Linux + Docker).
# À lancer en root (sudo) depuis le dossier server/ (copie du dépôt ou archive).
#
#   sudo ./deploy.sh --domain bebe.exemple.fr   # HTTPS automatique (Caddy) — ports 80/443 libres
#   sudo ./deploy.sh --behind-proxy             # un nginx/Caddy/Traefik existe déjà : écoute sur 127.0.0.1:8080
#   sudo ./deploy.sh --lan                      # réseau local seulement, http://IP:8080
#   options : --port 8080  --dir /opt/petit-rythme-sync  --create-token MOTDEPASSE  --dry-run
#
# Le script ne touche à rien d'autre que son dossier d'installation et ses conteneurs.
# Les données (data/), le jeton de création et un éventuel docker-compose.override.yml
# (réseau du reverse proxy, nom du conteneur…) sont conservés lors d'une mise à jour.
set -euo pipefail

MODE=""; DOMAIN=""; PORT=8080; DIR=/opt/petit-rythme-sync; TOKEN=""; DRY=0
while [ $# -gt 0 ]; do
  case "$1" in
    --domain) MODE=https; DOMAIN="$2"; shift 2 ;;
    --behind-proxy) MODE=proxy; shift ;;
    --lan) MODE=lan; shift ;;
    --port) PORT="$2"; shift 2 ;;
    --dir) DIR="$2"; shift 2 ;;
    --create-token) TOKEN="$2"; shift 2 ;;
    --dry-run) DRY=1; shift ;;
    -h|--help) sed -n '2,15p' "$0"; exit 0 ;;
    *) echo "Option inconnue : $1" >&2; exit 2 ;;
  esac
done
[ -n "$MODE" ] || { sed -n '2,14p' "$0"; exit 2; }

run() { if [ "$DRY" = 1 ]; then echo "[essai] $*"; else eval "$@"; fi; }
say() { echo "→ $*"; }
die() { echo "✗ $*" >&2; exit 1; }
port_busy() { (ss -ltnH 2>/dev/null || netstat -ltn 2>/dev/null) | awk '{print $4}' | grep -Eq "[:.]$1\$"; }

SRC="$(cd "$(dirname "$0")" && pwd)"
[ -f "$SRC/app.py" ] && [ -f "$SRC/docker-compose.yml" ] || die "Lancez ce script depuis le dossier server/ de Petit Rythme."
[ "$DRY" = 1 ] || [ "$(id -u)" = 0 ] || die "À lancer en root (sudo ./deploy.sh …)."

# 1. Docker
if ! command -v docker >/dev/null 2>&1; then
  die "Docker n'est pas installé. Installez-le (ex. : curl -fsSL https://get.docker.com | sh) puis relancez."
fi
docker compose version >/dev/null 2>&1 || die "Le plugin « docker compose » manque (paquet docker-compose-plugin)."

# 2. Ports : on ne prend jamais la place d'un service existant.
case "$MODE" in
  https)
    for p in 80 443; do
      if port_busy "$p" && ! docker ps --format '{{.Names}}' 2>/dev/null | grep -q '^petit-rythme-sync'; then
        die "Le port $p est déjà utilisé (site existant ?). Utilisez --behind-proxy et ajoutez un hôte virtuel (voir README)."
      fi
    done
    BIND=127.0.0.1 ;;
  proxy) BIND=127.0.0.1 ;;
  lan) BIND=0.0.0.0 ;;
esac
if port_busy "$PORT" && ! docker ps --format '{{.Names}}' 2>/dev/null | grep -q '^petit-rythme-sync'; then
  die "Le port $PORT est déjà utilisé : choisissez-en un autre avec --port."
fi

# Mise à jour : on garde le jeton de création déjà en place si aucun n'est donné.
if [ -z "$TOKEN" ] && [ -f "$DIR/.env" ]; then
  TOKEN="$(sed -n 's/^PR_CREATE_TOKEN=//p' "$DIR/.env")"
fi

# 3. Copie des fichiers (les données, .env existant et docker-compose.override.yml sont conservés)
say "Installation dans $DIR"
run "mkdir -p '$DIR/data'"
for f in app.py requirements.txt Dockerfile docker-compose.yml Caddyfile README.md; do
  run "install -m 0644 '$SRC/$f' '$DIR/$f'"
done
run "cat > '$DIR/.env' <<ENV
PR_BIND=$BIND
PR_PORT=$PORT
PR_CREATE_TOKEN=$TOKEN
DOMAIN=$DOMAIN
ENV"
run "chmod 600 '$DIR/.env'"

# 4. Lancement
PROFILE=""; [ "$MODE" = https ] && PROFILE="--profile https"
run "cd '$DIR' && docker compose -p petit-rythme-sync $PROFILE up -d --build"

# 5. Vérification
URL_LOCAL="http://127.0.0.1:$PORT/api/v1/health"
if [ "$DRY" = 0 ]; then
  ok=0
  for _ in $(seq 1 30); do curl -fsS "$URL_LOCAL" >/dev/null 2>&1 && { ok=1; break; }; sleep 1; done
  [ "$ok" = 1 ] || die "Le serveur ne répond pas sur $URL_LOCAL (docker compose -p petit-rythme-sync logs)."
fi
say "Serveur local OK ($URL_LOCAL)"

case "$MODE" in
  https)
    PUBLIC="https://$DOMAIN"
    say "HTTPS : le certificat est obtenu au premier accès (le nom $DOMAIN doit pointer vers cette machine)."
    [ "$DRY" = 1 ] || { sleep 5; curl -fsS "$PUBLIC/api/v1/health" >/dev/null 2>&1 && say "Accès public OK" || say "Accès public pas encore OK : vérifiez le DNS et les ports 80/443."; } ;;
  proxy)
    PUBLIC="https://<votre-sous-domaine>"
    cat <<NGINX
→ Ajoutez ceci à votre reverse proxy (exemple nginx), puis rechargez-le :

server {
    server_name bebe.exemple.fr;           # votre sous-domaine
    listen 443 ssl;                        # certificat : certbot --nginx -d bebe.exemple.fr
    client_max_body_size 8m;
    location / {
        proxy_pass http://127.0.0.1:$PORT;
        proxy_set_header Host \$host;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
}
NGINX
    ;;
  lan)
    IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
    PUBLIC="http://${IP:-<IP-de-la-machine>}:$PORT" ;;
esac

echo
echo "✓ Terminé. Dans l'appli : Réglages → Partage avec l'autre parent → Créer l'espace"
echo "  Adresse du serveur : $PUBLIC"
[ -n "$TOKEN" ] && echo "  Mot de passe de création : celui passé avec --create-token"
echo "  Mise à jour plus tard : relancez ce script avec les mêmes options."
