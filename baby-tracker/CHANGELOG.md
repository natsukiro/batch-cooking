# Journal des versions

Chaque version publiée a une section ici (obligatoire pour `scripts/release.sh`).
`versionCode` augmente de 1 à chaque version ; la signature ne change jamais (`keystore/`).

## 1.3.1 (versionCode 5)

- L'adresse du serveur de la famille (`https://rythme.natsukiro.info`) est proposée par défaut pour
  créer ou rejoindre le partage ; un code d'invitation seul suffit désormais.

## 1.3 (versionCode 4)

- **Synchronisation entre parents** via un serveur personnel (`server/`, Flask + SQLite + Docker,
  HTTPS automatique en option) : journal, rendez-vous, profil et chronos en cours. Appairage par
  lien d'invitation, « par Papa / Maman » dans le journal, synchro toutes les 20 s appli ouverte,
  en arrière-plan après une saisie (widget) et toutes les 15 min.
- **Vaccins conseillés** dans l'onglet RDV d'après le calendrier vaccinal 2026 et la date de
  naissance ; « Planifier » crée un rendez-vous lié à l'étape.
- Base de données v3 (identifiants globaux, horodatage, suppressions propagées) avec migration
  testée depuis la v1.
- Sauvegarde JSON format 3 (identifiants de synchro inclus).
- Kit de relecture pour une autre IA (`AGENTS.md`, `review/`, `scripts/`).

## 1.2 (versionCode 3)

- Onglet **RDV** à part : rendez-vous à venir/passés, questions à poser, compte rendu, rappels
  (veille 19 h / 1 h avant), ajout à l'agenda du téléphone, lien vers la saisie de croissance.
- Bandeau discret sur l'accueil si un rendez-vous a lieu dans les 3 jours.
- Base de données v2 (migration automatique).

## 1.1 (versionCode 2)

- **Widget** d'écran d'accueil (Glance) : couches en un appui avec annulation, tétée G/D,
  dodo, biberon via une petite fenêtre.
- Raccourcis de l'icône, rappel de repas optionnel, correction de l'heure de début d'un chrono,
  dernier caca sur l'accueil.
- Corrections : chrono tire-lait jamais arrêté, prénom sans espaces, moyennes faussées par les
  jours vides, heures futures acceptées, etc.

## 1.0 (versionCode 1)

- Première version : accueil « 3 h du matin », chrono de tétée par sein, couche en un appui,
  saisies détaillées, journal, écran Rythme (24 h, moyennes, histogrammes), sauvegarde/CSV.
