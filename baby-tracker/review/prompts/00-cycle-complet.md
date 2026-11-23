# Prompt — Cycle complet : relire, corriger, livrer

Copiez ce prompt tel quel dans une IA de code ayant accès au dépôt (Claude Code, Codex, Cursor…).

---

Tu vas faire une **relecture complète puis une correction** de l'application Android **Petit Rythme**
(dossier `baby-tracker/` du dépôt), et livrer un nouvel APK signé.

1. Lis `baby-tracker/AGENTS.md` en entier, puis `baby-tracker/review/DECISIONS.md` et
   `baby-tracker/review/PARCOURS.md`.
2. Si le SDK Android manque, lance `baby-tracker/scripts/setup-android-sdk.sh`. Puis lance
   `baby-tracker/scripts/verify.sh` et note le résultat.
3. Fais successivement les quatre relectures décrites dans :
   - `baby-tracker/review/prompts/01-revue-code.md`
   - `baby-tracker/review/prompts/04-revue-synchro-securite.md`
   - `baby-tracker/review/prompts/02-revue-ux.md`
   - `baby-tracker/review/prompts/03-revue-design.md`
   Écris un rapport par relecture dans `baby-tracker/review/reports/` (format `RAPPORT_MODELE.md`).
   Sois exigeant·e mais factuel·le : chaque constat a un scénario et une preuve.
4. Montre-moi un **résumé** des constats (Bloquant / Majeur / Mineur / À discuter) et **attends mon
   accord** avant de corriger si un constat touche aux données, à la synchro ou au contenu médical.
   Sinon, enchaîne.
5. Applique `baby-tracker/review/prompts/05-corriger-et-livrer.md` : corrections testées, nouvelle
   version, `verify.sh` vert, `release.sh`, commit sur une branche de travail.
6. Termine par : APK livré (chemin + version), corrections faites, points non traités et pourquoi,
   et ce que je dois tester moi-même sur le téléphone.
