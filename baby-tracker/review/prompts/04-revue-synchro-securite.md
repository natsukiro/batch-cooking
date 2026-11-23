# Prompt — Revue synchronisation et sécurité

Tu es ingénieur·e back-end/sécurité. Tu relis la **synchronisation entre parents** de Petit Rythme :
le serveur `baby-tracker/server/` (Flask + SQLite) et le client `app/.../sync/` + DAO.
Tu ne modifies rien : rapport avec preuves.

## Prépare-toi

1. Lis `AGENTS.md`, `review/DECISIONS.md` (section Technique), `server/README.md`.
2. Lis `server/app.py`, `server/tests/`, `sync/*.kt`, `data/AppDatabase.kt`, `data/Appointment.kt`
   (DAO et tombstones), `data/Codec.kt`, `ProfileStore`/`TimerStore` (parties synchro),
   `app/src/test/.../SyncTest.kt`.
3. Lance `scripts/verify.sh` (inclut un test de bout en bout contre le vrai serveur).

## Points à éprouver (écris des tests pytest / SyncTest pour les démontrer)

- **Convergence** : deux téléphones, toutes les séquences d'opérations (ajout, modif, suppression,
  annulation, hors ligne prolongé, horloges décalées de quelques minutes) finissent-elles identiques ?
- **Perte silencieuse** : une modification faite **pendant** une synchro, un échec réseau entre
  l'envoi et le marquage « propre », une page `more=true` interrompue.
- **Égalités d'horodatage**, documents « profile » et « timers », suppression d'un rendez-vous lié
  à un vaccin.
- **Serveur** : authentification (clé, empreinte), isolation des familles, création d'espace
  (`PR_CREATE_TOKEN`), validation des entrées, taille maximale, concurrence SQLite (WAL, transactions),
  page `/join` (XSS via le fragment ?), en-têtes, journalisation de la clé.
- **Client** : stockage de la clé (SharedPreferences privées), lien d'invitation, HTTP en clair
  hors réseau local, comportement si le serveur est réinitialisé (curseur plus grand que le serveur).
- **Exploitation** : Dockerfile, docker-compose, Caddy, sauvegarde du serveur, mise à jour.

## Rends

`baby-tracker/review/reports/<AAAA-MM-JJ>-synchro.md` au format `review/RAPPORT_MODELE.md`, avec
pour chaque problème le test qui le démontre.
