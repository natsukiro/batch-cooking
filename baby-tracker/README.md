# Petit Rythme 🌙

Application Android pour suivre le rythme de bébé : tétées, biberons, couches, sommeil, soins, croissance.
Pensée pour être utilisée **d'une main, à 3 h du matin** : les gestes fréquents se font en un seul appui.

| Accueil (tétée en cours) | Journal | Rythme sur 24 h | Couche détaillée | Mode nuit |
|---|---|---|---|---|
| ![](docs/01_accueil.png) | ![](docs/02_journal.png) | ![](docs/03_rythme.png) | ![](docs/06_couche_details.png) | ![](docs/07_accueil_nuit.png) |

### Widget d'écran d'accueil

| Normal | Tétée en cours | Nuit, dodo en cours | Biberon depuis le widget |
|---|---|---|---|
| ![](docs/widget.png) | ![](docs/widget_tetee.png) | ![](docs/widget_nuit.png) | ![](docs/saisie_rapide_biberon.png) |

## Installer

1. Télécharger [`release/PetitRythme-1.1.apk`](release/PetitRythme-1.1.apk) sur le téléphone.
2. L'ouvrir et autoriser l'installation depuis cette source si Android le demande.

Android 12L (API 32) minimum. Les futures versions s'installent par-dessus sans perdre les données
(même clé de signature, dans `keystore/`).

## Ce que l'appli fait

**Accueil**
- Chronomètre de tétée : un appui sur *Gauche* ou *Droit* le lance. Appui sur l'autre sein pour changer,
  sur le sein actif pour mettre en pause. Le temps est compté par sein, et le sein suivant est suggéré.
- Couche en **un appui** (Pipi / Caca / Les deux), avec *Annuler* dans le message de confirmation.
- Dodo : un appui lance le chrono, *Réveillé·e* l'enregistre.
- « Depuis combien de temps » : dernier repas, dernière couche, éveillé·e depuis.
- Totaux du jour : repas, temps au sein, ml, pipis, cacas, sommeil.
- Les chronos survivent à la fermeture de l'appli et s'affichent dans une notification.
  L'heure de début se corrige si on a lancé le chrono en retard.
- Heure du dernier caca, même si la dernière couche n'était qu'un pipi.

**Widget** (appui long sur l'écran d'accueil → Widgets → Petit Rythme) : Pipi / Caca / Les deux en un appui
avec « Annuler » pendant 10 min, tétée Gauche/Droit (changement, pause, fin), dodo/réveil, et un bouton Biberon
qui ouvre une petite fenêtre pré-remplie avec le dernier biberon. Redimensionnable (version compacte sur une ligne).

**Raccourcis** (appui long sur l'icône de l'appli) : Pipi, Caca, Biberon, Tétée.

**Rappel de repas** (facultatif, dans Réglages) : notification 2 h à 4 h après le début du dernier repas.

**Saisie détaillée** (crayon ✎, ou toucher une entrée du journal) : heure modifiable avec raccourcis
(−5 min, −15 min…), biberon (lait maternel/infantile, quantité avec raccourcis, dernière quantité retenue),
repas solides, tire-lait, couleur et texture des selles (avec alerte selles décolorées / sang, d'après la carte du
carnet de santé), température (alerte fièvre), soins et médicaments (Vitamine D…), bain, éveil,
croissance (poids, taille, périmètre crânien), notes.

**Journal** : jour par jour, résumé en haut, intervalle depuis le repas précédent, balayer pour supprimer
(avec *Annuler*), bouton *Ajouter* pour un oubli, même sur un jour passé.

**Rythme** : vue 24 h sur 7 ou 14 jours (sommeil, repas, couches), moyennes sur les jours réellement suivis
(intervalle réel entre deux repas, plus long dodo…), histogrammes par jour, courbe de poids.

**Réglages** : prénom, date de naissance (âge affiché), thème clair/sombre/auto, sauvegarde et restauration
(JSON), export CSV pour un tableur ou le pédiatre.

Toutes les données restent sur le téléphone. L'appli ne remplace pas un avis médical.

## Développement

Kotlin, Jetpack Compose (Material 3), Room, Glance (widget). Aucune dépendance réseau.

```bash
./gradlew assembleRelease        # APK signé : app/build/outputs/apk/release/
./gradlew testDebugUnitTest      # tests + captures d'écran (Robolectric/Roborazzi)
```

Les captures sont générées dans `app/build/outputs/roborazzi/`.
