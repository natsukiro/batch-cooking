# Déploiement en production (serveur de la famille)

État au 24/11/2026, installé par un agent IA sur le serveur avec `deploy.sh --behind-proxy`.
**Aucun secret ici** : le jeton de création est dans `.env` sur le serveur (droits 600).

| Élément | Valeur |
|---|---|
| Adresse publique (à saisir dans l'appli) | `https://rythme.natsukiro.info` |
| Accès SSH | port 2222, `sudo` pour root (identifiants : demander à l'utilisateur, ne jamais les écrire) |
| Dossier d'installation | `/mnt/data/docker/petit-rythme/` |
| Sources livrées | `/mnt/data/docker/petit-rythme/source/` (copie du dossier `server/`) |
| Base de données | `/mnt/data/docker/petit-rythme/data/petitrythme.db` (SQLite, WAL : fichiers `-wal`/`-shm`) |
| Configuration | `.env` (PR_BIND=127.0.0.1, PR_PORT=18080, PR_CREATE_TOKEN) + `docker-compose.override.yml` |
| Conteneur / image | `petit-rythme-sync` / `petit-rythme-sync:local`, port `127.0.0.1:18080` |
| Réseau | `frontend` (partagé avec le reverse proxy), ajouté par l'override |
| Reverse proxy | **Zoraxy** : vhost `rythme.natsukiro.info` → `petit-rythme-sync:8080`, certificat, HSTS 1 an, mêmes protections que le vhost `bebe` (anti-exploits, anti-robots IA, pas de BasicAuth) |
| Sauvegarde | système `backup-rebuild` : base, config, sources et vhost dans `manifest.json` ; conteneur déclaré dans `runtime-sources.json` ; fichier `-shm` autorisé dans les 4 unités systemd (copies dans `/etc/systemd/system` et dans le dépôt de sauvegarde) |
| Documentation serveur | 3 copies identiques tenues à jour (vhost, inventaire, fiche service, liste des bases SQLite, journal daté) |

## Redéployer une nouvelle version du serveur

```bash
# depuis une copie à jour de baby-tracker/server/ sur la machine
sudo ./deploy.sh --behind-proxy --port 18080 --dir /mnt/data/docker/petit-rythme --dry-run
sudo ./deploy.sh --behind-proxy --port 18080 --dir /mnt/data/docker/petit-rythme
```

`deploy.sh` conserve `data/`, le jeton de création (`.env`) et `docker-compose.override.yml`.
Mettre aussi à jour `source/` avec la nouvelle copie. Puis vérifier :
`curl -fsS https://rythme.natsukiro.info/api/v1/health`.

## Contraintes à respecter

- **Ne pas renommer** le conteneur, le dossier, la base ni le vhost : la sauvegarde nocturne
  (`backup-rebuild`) les référence et **refuse la sauvegarde entière** si un conteneur actif n'est pas
  déclaré dans `runtime-sources.json`. Tout nouveau fichier (ex. autre base, autre suffixe SQLite)
  doit être ajouté au manifeste et aux unités systemd, puis `systemctl daemon-reload`.
- Modifier le vhost via l'API/interface de Zoraxy, avec les mêmes réglages que `bebe`.
- Toute modification de la machine doit être reportée dans les **3 copies** de la documentation
  du serveur (et une ligne datée dans son journal).
- Sauvegarder la base avant une migration de schéma côté serveur :
  `sqlite3 data/petitrythme.db ".backup data/avant-maj-$(date +%F).db"`.
- Le protocole de synchro doit rester compatible avec les APK déjà installés (champ `protocol`).
