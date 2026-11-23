# Kit de relecture et de correction par une IA

Ce dossier permet de confier à une autre IA (ou à un·e développeur·se) la **vérification** de
Petit Rythme — code, UX, design, synchro — puis la **correction** avec une **nouvelle build**.

## Le plus simple

Ouvrez une IA de code qui a accès à ce dépôt (Claude Code, Codex, Cursor, Gemini CLI…) et collez
le contenu de [`prompts/00-cycle-complet.md`](prompts/00-cycle-complet.md). Elle relit tout, vous
montre les problèmes, corrige, et livre `release/PetitRythme-<version>.apk`.

Avec Claude Code, depuis la racine du dépôt : tapez `/petit-rythme-revue`.

## À la carte

| Besoin | Prompt | Commande Claude Code |
|---|---|---|
| Chercher des bugs (données, chronos, cycle de vie) | [`01-revue-code.md`](prompts/01-revue-code.md) | `/petit-rythme-code` |
| Ergonomie, parcours, textes | [`02-revue-ux.md`](prompts/02-revue-ux.md) | `/petit-rythme-ux` |
| Rendu visuel, couleurs, mode sombre | [`03-revue-design.md`](prompts/03-revue-design.md) | `/petit-rythme-design` |
| Synchronisation et sécurité du serveur | [`04-revue-synchro-securite.md`](prompts/04-revue-synchro-securite.md) | `/petit-rythme-synchro` |
| Corriger les rapports et livrer un APK | [`05-corriger-et-livrer.md`](prompts/05-corriger-et-livrer.md) | `/petit-rythme-corriger` |
| Mettre à jour le calendrier vaccinal (chaque année) | [`06-calendrier-vaccinal.md`](prompts/06-calendrier-vaccinal.md) | — |

Les relectures écrivent un rapport dans `reports/` (modèle : [`RAPPORT_MODELE.md`](RAPPORT_MODELE.md)) ;
la correction part de ces rapports. Vous pouvez aussi faire relire par une IA et corriger par une autre.

## Ce que l'IA utilise

- [`../AGENTS.md`](../AGENTS.md) : architecture, commandes, règles absolues (données, signature,
  contenu médical), conventions d'interface, pièges connus.
- [`PARCOURS.md`](PARCOURS.md) : ce que chaque écran doit faire, en nombre d'appuis — la référence
  pour dire « c'est un bug ».
- [`DECISIONS.md`](DECISIONS.md) : choix volontaires à ne pas « corriger » sans vous en parler.
- [`../scripts/verify.sh`](../scripts/verify.sh) : vérification complète en une commande
  (tests du serveur, tests de l'appli dont migration et synchro de bout en bout, APK signé,
  signature, contrastes, captures d'écran copiées dans `screenshots/`).
- [`../scripts/release.sh`](../scripts/release.sh) : publie l'APK de la nouvelle version dans `release/`.

## Prérequis de l'environnement de l'IA

- Java 17+, Python 3.10+, accès réseau à `dl.google.com` (SDK Android) et aux registres Maven/PyPI.
- Le SDK s'installe avec `scripts/setup-android-sdk.sh` (≈ 300 Mo).
- Pas besoin d'émulateur : les écrans sont rendus par Robolectric (captures PNG).
- Ce qui ne peut pas être vérifié ainsi (vrai téléphone, notifications réelles, widget sur un vrai
  lanceur, serveur exposé sur Internet) doit être listé à la fin pour un test manuel.
