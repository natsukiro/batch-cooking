# Prompt — Déployer le serveur de synchro sur le serveur de la famille

À coller dans un agent IA (Claude Code, Codex, Antigravity…) **lancé sur le serveur lui-même**
(Linux, accès `sudo`). L'agent installe `baby-tracker/server/` sans perturber ce qui tourne déjà.

---

Tu es administrateur·rice système prudent·e. Tu déploies le serveur de synchronisation de
l'appli **Petit Rythme** sur cette machine. Règles : tu ne casses aucun service existant, tu ne
stockes aucun mot de passe en clair (hors le fichier `.env` créé par le script, en 600), tu montres
chaque modification de configuration avant de l'appliquer, et tu t'arrêtes pour me demander si
une décision dépend de moi (nom de domaine, ouverture de ports, installation de Docker).

1. **Récupérer les fichiers** : dépôt `natsukiro/batch-cooking` (branche indiquée par
   l'utilisateur, sinon la plus récente contenant `baby-tracker/server/deploy.sh`), ou archive
   `petit-rythme-server.tar.gz` fournie par l'utilisateur. Lis `baby-tracker/server/README.md`.
2. **État des lieux** (lecture seule) : OS, `docker` et `docker compose`, ports en écoute
   (`ss -ltnp`), serveur web existant (nginx, Apache, Caddy, Traefik — conteneurisé ou non) et où
   sont ses hôtes virtuels, certificats (certbot ?), pare-feu (`ufw status` / `nft list ruleset`),
   et si le sous-domaine prévu (proposer `bebe.<domaine>`) résout déjà vers cette machine
   (`dig +short`). Résume-moi tout ça.
3. **Choisir le mode** :
   - un reverse proxy occupe déjà 80/443 → `--behind-proxy` + ajout d'un hôte virtuel pour le
     sous-domaine vers `127.0.0.1:8080` (sauvegarde du fichier de conf, `nginx -t` avant rechargement,
     certificat via certbot ou l'outil en place) ;
   - 80/443 libres → `--domain <sous-domaine>` (Caddy gère HTTPS) ;
   - pas de nom de domaine → `--lan` (et prévenir : synchro seulement sur le Wi-Fi de la maison).
   Si le DNS du sous-domaine manque, donne-moi l'enregistrement exact à créer et attends.
   Recommande `--create-token` avec un jeton aléatoire (`openssl rand -hex 16`) car le serveur est public.
4. **Déployer** : `sudo ./deploy.sh <mode> --dry-run`, montre la sortie, puis la vraie commande.
5. **Vérifier depuis l'extérieur** : `curl -fsS https://<sous-domaine>/api/v1/health`,
   page `https://<sous-domaine>/join` ; puis un aller-retour de synchro avec une clé de test
   aléatoire (POST `/api/v1/family` puis `/api/v1/sync`, cf. README), et supprime ensuite cette
   famille de test de la base (table `families`, suppression en cascade).
6. **Sauvegarde** : propose (sans l'imposer) une copie quotidienne de
   `/opt/petit-rythme-sync/data/petitrythme.db` (`sqlite3 … ".backup …"` via cron).
7. **Rends-moi** : l'adresse à saisir dans l'appli, le jeton de création s'il y en a un, la liste
   exacte de ce qui a été modifié sur la machine, et comment mettre à jour (relancer `deploy.sh`)
   ou tout retirer (`docker compose -p petit-rythme-sync down`, supprimer l'hôte virtuel).
