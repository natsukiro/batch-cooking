# Prompt — Relecture, correction et redéploiement (appli + serveur en production)

À coller dans un agent IA de code (Claude Code, Codex, Antigravity…) qui a accès au dépôt
`natsukiro/batch-cooking`. Si l'agent tourne **sur le serveur** de la famille, il peut aussi faire
le redéploiement serveur ; sinon il prépare tout et me donne les commandes.

---

Tu reprends **Petit Rythme** : une appli Android de suivi de bébé (dossier `baby-tracker/`) et son
serveur de synchronisation entre parents, **en production** sur `https://rythme.natsukiro.info`.
Ta mission : relire, corriger, produire un nouvel APK signé et, si le serveur change, le redéployer
sans rien casser. Les parents utilisent l'appli tous les jours : leurs données passent avant tout.

## 1. Lis, dans cet ordre

1. `baby-tracker/AGENTS.md` — architecture, commandes, **règles absolues** (données, signature,
   contenu médical, protocole de synchro).
2. `baby-tracker/review/DECISIONS.md` — choix volontaires à ne pas « corriger ».
3. `baby-tracker/review/PARCOURS.md` — comportement attendu de chaque écran (critères d'acceptation).
4. `baby-tracker/server/PRODUCTION.md` — ce qui est déployé, où, et les contraintes (sauvegarde
   `backup-rebuild`, Zoraxy, documentation en 3 copies).
5. `baby-tracker/CHANGELOG.md` et les rapports existants dans `baby-tracker/review/reports/`.

## 2. Vérifie l'état de départ

- `baby-tracker/scripts/setup-android-sdk.sh` si le SDK manque, puis `baby-tracker/scripts/verify.sh`
  (doit finir par « ✓ Tout est vert »).
- Santé de la production : `curl -fsS https://rythme.natsukiro.info/api/v1/health`.

## 3. Relis (un rapport par relecture dans `baby-tracker/review/reports/`)

Applique successivement :
- `review/prompts/01-revue-code.md` (bugs, données, cycle de vie),
- `review/prompts/04-revue-synchro-securite.md` (synchro, serveur, sécurité — y compris l'exposition
  réelle derrière Zoraxy : en-têtes, `/join`, taille des requêtes, jeton de création),
- `review/prompts/02-revue-ux.md` et `review/prompts/03-revue-design.md` (captures dans
  `review/screenshots/` après `verify.sh`).

Puis **montre-moi un résumé** (Bloquant / Majeur / Mineur / À discuter) et **attends mon accord**
pour tout ce qui touche aux données, au protocole de synchro, au serveur de production ou au
contenu médical.

## 4. Corrige et livre l'appli

Applique `review/prompts/05-corriger-et-livrer.md` : un test qui échoue puis la correction, migration
+ `MigrationTest` si le schéma change, `versionCode` +1, `CHANGELOG.md`, `verify.sh` vert,
`release.sh` → `baby-tracker/release/PetitRythme-<version>.apk`, commit sur une branche de travail.

## 5. Redéploie le serveur (seulement si `baby-tracker/server/` a changé)

- Garde la **compatibilité** avec les APK déjà installés (ancien et nouveau protocole doivent marcher
  pendant la transition).
- Sur le serveur (via `sudo`) : sauvegarde de la base, puis
  `deploy.sh --behind-proxy --port 18080 --dir /mnt/data/docker/petit-rythme` (essai `--dry-run`
  d'abord), mise à jour de `source/`, contrôle de santé public, test d'aller-retour de synchro avec
  une famille de test supprimée ensuite.
- Respecte `PRODUCTION.md` : ne renomme rien, mets à jour `backup-rebuild` si un fichier apparaît,
  reporte le changement dans les 3 copies de la documentation du serveur.
- Si tu n'es pas sur le serveur : ne tente pas d'y accéder par un autre moyen ; donne-moi les
  commandes exactes à lancer.
- Ne demande jamais de mot de passe dans la conversation et n'en écris aucun dans un fichier.

## 6. Rends-moi

- APK livré (chemin, version) et ce que je dois tester sur le téléphone.
- Corrections faites (une ligne chacune, avec le test associé), points non traités et pourquoi.
- Changements sur le serveur (ou commandes à lancer), état de santé après redéploiement.
- Mise à jour de `server/PRODUCTION.md` si l'installation a changé.
