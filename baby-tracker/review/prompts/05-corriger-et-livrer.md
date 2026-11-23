# Prompt — Corriger et livrer une nouvelle version

Tu es l'ingénieur·e chargé·e de **corriger Petit Rythme et de produire un nouvel APK** à partir
des rapports de relecture. Tu travailles avec rigueur : chaque correction est testée, rien n'est
cassé, les données des parents sont protégées.

## Entrées

- Les rapports dans `baby-tracker/review/reports/` (les plus récents), ou la liste de problèmes
  donnée par l'utilisateur.
- `AGENTS.md` (règles absolues), `review/DECISIONS.md`, `review/PARCOURS.md`.

## Méthode

1. Lance `scripts/verify.sh` pour partir d'un état vert (sinon, corrige d'abord ce qui est rouge).
2. Trie les constats : Bloquant → Majeur → Mineur. Les points « À discuter » et ceux qui contredisent
   `DECISIONS.md` ne sont **pas** traités : liste-les pour l'utilisateur.
3. Pour chaque constat retenu :
   a. Reproduis-le par un **test qui échoue** (Robolectric dans `app/src/test`, pytest dans
      `server/tests`, ou capture dans `ScreenshotTest` pour un défaut visuel).
   b. Corrige de la façon **la plus petite et la plus locale** possible, dans le style du code existant.
   c. Le test passe ; les autres aussi.
   d. Si tu touches au schéma Room : nouvelle version + migration + extension de `MigrationTest`.
   e. Si tu touches au protocole de synchro : serveur et client ensemble, compatibilité ascendante.
4. Pour un défaut visuel : régénère les captures et **regarde-les** avant/après.
5. Version : `versionCode` +1, `versionName` (correctif : 1.3 → 1.3.1 ; nouveauté : 1.4) dans
   `app/build.gradle.kts`, et une section dans `CHANGELOG.md` (en français, orientée parents).
6. `scripts/verify.sh` → doit finir par « ✓ Tout est vert ». Puis `scripts/release.sh` (copie
   l'APK dans `release/PetitRythme-<version>.apk` et met à jour le README).
7. Mets à jour la documentation si un comportement change (`README.md`, `review/PARCOURS.md`,
   `AGENTS.md`, `server/README.md`).
8. Commit (message en anglais : titre court + liste des corrections), sur une branche de travail ;
   ne pousse pas sur la branche principale sans accord.

## Interdits

- Désactiver, ignorer ou affaiblir un test pour obtenir du vert.
- Changer la clé de signature, `applicationId`, ou supprimer des données/migrations.
- Ajouter un service tiers (analytics, cloud) ou modifier un contenu médical sans source officielle.
- Refondre ce qui n'est pas demandé.

## Rends à l'utilisateur

- Ce qui a été corrigé (une ligne par constat, avec le test associé), ce qui ne l'a pas été et pourquoi.
- Le chemin de l'APK publié et sa version.
- Ce qui reste à vérifier sur un vrai téléphone (les tests tournent sous Robolectric, sans émulateur).
