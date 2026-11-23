# Serveur de synchro Petit Rythme

Petit serveur (Flask + SQLite) qui permet aux deux parents de partager le même suivi.
Il ne comprend pas les données : il stocke des documents JSON regroupés par « espace famille »
(en clair sur le disque du serveur, qui est le vôtre ; chiffrés pendant le transport si HTTPS).
Chaque espace est protégé par une clé secrète générée par l'appli et transmise via le lien
d'invitation (le serveur n'en garde que l'empreinte).

## Installer

Prérequis : une machine allumée en permanence avec Docker (VPS, NAS, mini-PC…).

### Option A — Internet avec HTTPS (recommandé)

Pour que la synchro marche aussi hors de la maison :

1. Un nom de domaine (ou sous-domaine) qui pointe vers la machine, ex. `bebe.example.fr`.
2. Ports 80 et 443 ouverts vers la machine.
3. Lancer :
   ```bash
   cd baby-tracker/server
   DOMAIN=bebe.example.fr docker compose --profile https up -d
   ```
4. Dans l'appli : Réglages → Synchronisation → Créer, adresse `https://bebe.example.fr`.

Caddy obtient et renouvelle le certificat HTTPS tout seul.

### Option B — Réseau de la maison seulement

```bash
cd baby-tracker/server
docker compose up -d
```

Adresse dans l'appli : `http://<IP-de-la-machine>:8080` (ex. `http://192.168.1.20:8080`).
La connexion n'est pas chiffrée : à réserver au Wi-Fi de la maison. La synchro ne se fait
alors que quand le téléphone est sur ce Wi-Fi.

### Limiter la création d'espaces (facultatif)

Si le serveur est public, n'importe qui pourrait y créer un espace vide. Pour l'éviter :

```bash
PR_CREATE_TOKEN=un-mot-de-passe DOMAIN=… docker compose --profile https up -d
```

L'appli demandera alors ce mot de passe au moment de « Créer ». Rejoindre un espace
existant n'en a pas besoin.

## Sauvegarder

Tout est dans `server/data/petitrythme.db` (SQLite). Copier ce fichier suffit
(de préférence serveur arrêté, ou avec `sqlite3 data/petitrythme.db ".backup copie.db"`).
Chaque téléphone garde aussi sa copie complète : si le serveur est perdu, les données restent
dans les téléphones.

## API (protocole 1)

| Méthode | Chemin | Rôle |
|---|---|---|
| GET | `/api/v1/health` | vérifier que le serveur répond |
| POST | `/api/v1/family` | créer l'espace (en-tête `Authorization: Bearer <clé>`, `X-Create-Token` si exigé) |
| POST | `/api/v1/sync` | envoyer ses changements et recevoir ceux de l'autre |
| GET | `/join#k=<clé>` | page d'invitation qui ouvre l'appli |

Corps de `/sync` : `{"since": <curseur>, "changes": [{"uid","kind","updatedAt","deleted","data"}]}`.
Réponse : `{"cursor", "changes", "more"}`. En cas de conflit, la modification la plus récente
(`updatedAt`) gagne. `kind` ∈ `event`, `appt`, `profile`, `timers`.

## Développement

```bash
python3 -m venv .venv && .venv/bin/pip install -r requirements-dev.txt
.venv/bin/python -m pytest -q tests
PR_DB=./dev.db .venv/bin/flask --app app run --port 8080
```
