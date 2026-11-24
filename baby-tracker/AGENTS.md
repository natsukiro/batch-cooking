# Petit Rythme — guide pour les agents IA

Ce fichier est la référence pour toute IA (Claude, Codex, Cursor, Gemini…) qui relit, corrige
ou fait évoluer l'application. Lisez-le en entier avant de toucher au code.
Les prompts prêts à l'emploi sont dans [`review/prompts/`](review/prompts/).

## Le produit en une minute

Appli Android (français) pour suivre le rythme d'un bébé : tétées (chrono par sein), biberons,
couches, sommeil, soins, croissance, rendez-vous et vaccins. Utilisée **d'une main, la nuit, par
des parents épuisés** : chaque geste fréquent doit tenir en un ou deux appuis. Deux parents peuvent
partager le suivi via **leur propre serveur** (`server/`). Aucun service tiers, aucune pub, aucun
traqueur.

Utilisateur réel : un couple de parents en France (carnet de santé, calendrier vaccinal français).

## Commandes

| But | Commande (depuis `baby-tracker/`) |
|---|---|
| Installer le SDK Android (une fois) | `scripts/setup-android-sdk.sh` (réseau : `dl.google.com`) |
| **Tout vérifier** (tests serveur + appli + synchro bout en bout + APK + signature + contrastes + captures) | `scripts/verify.sh` |
| Tests appli seuls | `./gradlew testDebugUnitTest` |
| APK signé | `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk` |
| Publier une version | modifier `versionCode`/`versionName` + `CHANGELOG.md`, puis `scripts/verify.sh && scripts/release.sh` |
| Tests serveur | `cd server && .venv/bin/python -m pytest -q tests` |

`scripts/verify.sh` doit finir par « ✓ Tout est vert » avant toute livraison. Il copie les captures
d'écran dans `review/screenshots/` : c'est la matière des relectures UX et design.

## Architecture

```
app/src/main/java/app/petitrythme/
  BabyApp.kt            Application : base, stores, synchro ; rafraîchit widget/rappels/synchro
                        à chaque écriture (InvalidationTracker sur events/appointments/tombstones)
  MainActivity.kt       Compose ; liens d'invitation ; synchro toutes les 20 s appli ouverte
  data/
    Event.kt            Évènement du journal (une table, champs optionnels par type) + enums
    Appointment.kt      Rendez-vous + Tombstone (suppressions à propager) + DAO
    AppDatabase.kt      Room v3, DAO des évènements, MIGRATION_2_3 (v1→v2 automatique)
    Codec.kt            JSON des enregistrements (sauvegarde ET synchro)
    Backup.kt           Sauvegarde/restauration JSON, export CSV
    ProfileStore.kt     Prénom/naissance (synchronisés), thème/rappels (locaux)
    TimerStore.kt       Chronos tétée/sommeil/tire-lait (persistés, synchronisés)
    Drafts.kt           Saisies pré-remplies
    VaccineSchedule.kt  Calendrier vaccinal (SUGGESTIONS, sourcé et daté)
  sync/                 SyncManager (moteur), SyncConfig (clé, invitation), HttpTransport, SyncWorker
  notify/               Notification des chronos, rappel de repas, rappels de RDV, redémarrage
  widget/               Widget Glance + actions + annulation
  quick/                Fenêtre de saisie rapide (widget, raccourcis) + raccourcis de l'icône
  ui/                   Compose : home, entry (fiche de saisie), journal, stats (Rythme),
                        appointments (RDV + vaccins), settings (+ SyncSection), theme, components
server/                 Serveur de synchro Flask + SQLite (+ Docker, Caddy pour HTTPS)
app/schemas/            Schémas Room exportés (1.json, 2.json, 3.json) — NE JAMAIS les modifier
app/src/test/           Robolectric : LogicTest, MigrationTest, SyncTest, ScreenshotTest, WidgetScreenshotTest
```

Pile : Kotlin 2.2, AGP 8.11, Compose BOM 2025.07 (Material 3), Room 2.7 (KSP), Glance 1.1,
WorkManager 2.10, compileSdk/targetSdk 36, minSdk 32. Tests : Robolectric 4.16 + Roborazzi.

## Règles à ne jamais enfreindre

1. **Données des parents sacrées.** Toute modification de schéma Room = nouvelle version +
   migration (automatique si possible, sinon écrite à la main) + extension de `MigrationTest`
   (base réelle d'une ancienne version → ouverture par la nouvelle). Jamais de
   `fallbackToDestructiveMigration`. Ne jamais éditer `app/schemas/*.json` existants.
2. **Écritures locales uniquement via `insert`/`update`/`delete`/`clear`/`replaceAll` des DAO** :
   elles horodatent (`updatedAt`), marquent `dirty` et créent les tombstones. Les méthodes `*Raw`
   sont réservées au moteur de synchro (`SyncManager.apply`).
3. **Signature** : APK toujours signé avec `keystore/petitrythme.jks` (empreinte SHA-256
   `0d5d67ff…ce76e`, vérifiée par `verify.sh`). Sinon les parents ne peuvent plus mettre à jour
   sans désinstaller (= perte de données).
4. **Version** : `versionCode` +1 et `versionName` à chaque APK publié, section dans `CHANGELOG.md`.
5. **Contenu médical** : ne rien inventer. Les seuils/alertes existants (fièvre ≥ 38 °C, selles
   décolorées/sang, calendrier vaccinal) sont sourcés ; toute modification doit citer une source
   officielle française (ministère de la Santé, HAS, Santé publique France, carnet de santé) et
   garder la mention « à confirmer avec le médecin ». Si la source est inaccessible, le dire.
6. **Protocole de synchro** : `server/app.py` et `sync/` évoluent ensemble ; garder la compatibilité
   (champ `protocol` de `/api/v1/health`). Tests : `server/tests`, `SyncTest` (faux serveur + vrai
   serveur via `verify.sh`).
7. **Pas de dépendance réseau tierce** (analytics, crash reporting, Firebase…) sans accord explicite.
8. Ne pas « corriger » ce qui est listé dans [`review/DECISIONS.md`](review/DECISIONS.md) sans en
   discuter : ce sont des choix volontaires.

## Conventions d'interface

- **Français** partout, tutoiement interdit (vouvoiement ou tournures impersonnelles), écriture
  inclusive légère (« Éveillé·e »). Heures `HH:mm`, durées via `Fmt` (`ui/Format.kt`), jamais à la main.
- **Ergonomie de nuit** : cibles ≥ 48 dp (boutons principaux 56–72 dp), action la plus fréquente
  la plus grosse, confirmation par message avec « Annuler » plutôt que boîte de dialogue, haptique
  sur les gestes rapides, thème sombre peu lumineux.
- **Couleurs** : jamais de couleur en dur dans les écrans ; utiliser `MaterialTheme.colorScheme`
  et `Category.color()/container()` (`ui/theme/Theme.kt`). Les couleurs de catégorie ont été
  validées pour le daltonisme et les contrastes ; `scripts/check_contrast.py` doit rester vert.
- **Emojis** comme icônes de catégorie : n'utiliser que des emojis disponibles depuis Android 12
  (Unicode ≤ 13). Les captures Robolectric affichent un carré vide pour les emojis trop récents.
- Les écrans sont testés par captures (`ScreenshotTest`) : tout nouvel écran ou état important
  doit y avoir sa capture.

## Pièges connus (tests Robolectric)

- Peupler la base **avant** de lancer l'activité (`ActivityScenario.launch` après `seed`), et
  fermer le scénario en fin de test, sinon les requêtes visent un ancien écran.
- `rule.waitUntil` fait avancer une horloge virtuelle : utiliser `waitFor()` de `ScreenshotTest`.
- Cliquer hors écran : `performScrollTo()` d'abord.
- La fabrique de ViewModel par défaut garde la première `Application` en statique : `MainActivity`
  passe l'`Application` explicitement (ne pas revenir à `viewModel()` sans fabrique).
- Ne pas utiliser `Dispatchers.Main` dans du code appelé depuis `runBlocking` en test (blocage).
- Maven Central limite parfois le débit (429) : `settings.gradle.kts` passe d'abord par le miroir
  Google de Maven Central ; Robolectric aussi (`robolectric.dependency.repo.url`).

## Production

Le serveur de synchro tourne sur `https://rythme.natsukiro.info` (Docker derrière Zoraxy, sauvegarde
`backup-rebuild`). Avant toute modification de `server/` ou de son déploiement, lire
[`server/PRODUCTION.md`](server/PRODUCTION.md). Ne jamais écrire d'identifiants dans le dépôt.

## Environnement

- Réseau nécessaire : `dl.google.com` (SDK Android + Maven Google), registres Maven/PyPI.
- Le site du ministère de la Santé bloque les robots (captcha) : pour le calendrier vaccinal,
  recouper plusieurs sources et le signaler.
- Docker n'est pas forcément disponible : le serveur se teste aussi avec gunicorn (`verify.sh`).
