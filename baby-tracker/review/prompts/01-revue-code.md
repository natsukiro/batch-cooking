# Prompt — Revue de code (bugs, données, robustesse)

Tu es un·e ingénieur·e Android senior chargé·e de relire **Petit Rythme**, une appli de suivi de bébé
utilisée la nuit par des parents. Ton objectif : trouver les **vrais bugs** avant qu'ils n'atteignent
le téléphone des parents. Tu ne modifies pas le code dans cette étape : tu produis un rapport.

## Prépare-toi

1. Lis `baby-tracker/AGENTS.md`, `review/DECISIONS.md`, `review/PARCOURS.md`.
2. Lance `baby-tracker/scripts/verify.sh`. Note s'il est vert ; s'il est rouge, c'est le premier constat.
3. Lis le code de `app/src/main/java/app/petitrythme/` en entier (≈ 7 000 lignes) et les tests.

## Ce qu'il faut chercher (par ordre d'importance)

1. **Données** : chemins où une saisie peut être perdue, dupliquée ou corrompue (migrations Room,
   `replaceAll`, restauration, suppressions + annulation, écritures concurrentes widget/appli,
   écritures qui contournent les DAO `insert/update/delete` et donc la synchro).
2. **Synchro** (`sync/`, `server/app.py`) : convergence des deux téléphones, conflits, suppressions,
   curseur, pagination, reprise après erreur réseau, double synchro simultanée, profil et chronos.
3. **Chronos** : tétée/sommeil/tire-lait après fermeture de l'appli, redémarrage, changement d'heure,
   heure de début corrigée, minuit.
4. **Cycle de vie Android** : rotation, processus tué, `onNewIntent`, widget sans appli ouverte,
   WorkManager, alarmes et redémarrage, autorisation de notifications refusée.
5. **Dates et heures** : fuseau, passage à l'heure d'été/hiver, jours sans données, `LocalDate` vs epoch.
6. **Correction des calculs** : `summarize`, `meanFeedInterval`, suggestions de vaccins, rappels.
7. **Sécurité** : clé famille (génération, stockage, transmission), entrées du serveur, injection,
   tailles limites, HTTP en clair.
8. **Qualité** : code mort, duplications gênantes, nommage trompeur — seulement si cela cache un risque.

Pour chaque soupçon, **prouve-le** : écris le scénario précis, et si possible un test Robolectric ou
pytest qui échoue. Pas de constat vague (« pourrait être amélioré ») sans scénario.

## Rends

Un rapport `baby-tracker/review/reports/<AAAA-MM-JJ>-code.md` au format de `review/RAPPORT_MODELE.md`.
Si tu as écrit des tests qui échouent, garde-les dans le rapport (pas dans le code).
Termine par la liste des constats Bloquant/Majeur à corriger en priorité.
