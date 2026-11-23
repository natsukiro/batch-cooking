# Parcours attendus (critères d'acceptation)

Chaque parcours décrit ce qu'un parent fait et ce qui doit se passer. Un relecteur vérifie chacun
dans le code et/ou sur les captures (`review/screenshots/`). Le nombre d'appuis est un critère :
il ne doit pas augmenter.

## Accueil (`ui/home/HomeScreen.kt`)

| # | Parcours | Attendu |
|---|---|---|
| A1 | Lancer une tétée | 1 appui sur « Gauche » ou « Droit ». Le sein conseillé (inverse du dernier) est mis en avant. Carte chrono en haut, notification persistante. |
| A2 | Changer de sein / pause | Appui sur l'autre sein = changement ; sur le sein actif = pause. Temps compté par sein. |
| A3 | Terminer une tétée | « Terminer et enregistrer » → évènement Tétée (durées G/D, dernier sein) + message avec « Modifier ». |
| A4 | Chrono lancé en retard | « Commencée à 13:08 ✎ » → choisir l'heure → l'écart est ajouté au sein en cours. Heure future impossible ; 23:50 choisi à 00:20 = la veille. |
| A5 | Noter une couche | 1 appui (Pipi / Caca / Les deux) → message « … noté à HH:mm » avec « Annuler » qui supprime. |
| A6 | Dodo | 1 appui lance le chrono ; « Réveillé·e » enregistre la période. |
| A7 | Biberon | Tuile → fiche pré-remplie avec la quantité et le lait du **dernier biberon** ; bouton « Enregistrer · 90 ml ». Nouvelle installation : « Quantité et type de lait » (pas de fausse valeur). |
| A8 | Tire-lait | Tuile → chrono ; « Terminer » → fiche ; après enregistrement le chrono **s'arrête** (régression couverte par un test). |
| A9 | En un coup d'œil | Cartes Repas / Couche / Éveillé·e : durée depuis, heure ; pendant une tétée « 7 min · Tétée en cours » ; dernier caca affiché s'il est plus ancien que la dernière couche. |
| A10 | Rendez-vous proche | Bandeau seulement si RDV dans les 3 jours ; le toucher ouvre la fiche. |

## Saisie détaillée (`ui/entry/EntrySheet.kt`)

| # | Parcours | Attendu |
|---|---|---|
| S1 | Heure | « Maintenant / −5 / −15 / −30 min / −1 h » pour une nouvelle saisie ; heure future refusée (message). |
| S2 | Selles | Couleurs ; « Pâle / blanche », « Avec du sang », « Noire » affichent l'avertissement correspondant. |
| S3 | Température | Pas de 0,1 °C ; ≥ 38 °C : couleur d'alerte + message (fièvre, < 3 mois = avis rapide). |
| S4 | Sommeil | Réveil avant l'endormissement refusé. |
| S5 | Modifier / supprimer | Depuis le journal ; suppression confirmée puis message « Annuler ». |

## Journal, Rythme

| # | Parcours | Attendu |
|---|---|---|
| J1 | Navigation | Jours précédents/suivants (pas au-delà d'aujourd'hui) ; la barre du bas rouvre toujours aujourd'hui. |
| J2 | Oubli | « Ajouter » sur un jour passé → heure pré-remplie ce jour-là. |
| J3 | Balayage | Balayer à gauche supprime avec « Annuler ». |
| J4 | Suivi partagé | Les saisies de l'autre parent affichent « par <prénom> ». |
| R1 | Rythme 24 h | 7/14 jours, sommeil (barres), repas (traits), couches (points pleins = caca, ronds = pipi) ; toucher un jour ouvre le journal. |
| R2 | Moyennes | Seulement sur les jours complets **avec données** ; intervalle réel entre repas (compléments < 30 min fusionnés, trous > 8 h ignorés). |

## Rendez-vous et vaccins (`ui/appointments/`)

| # | Parcours | Attendu |
|---|---|---|
| V1 | Suggestions | Sans date de naissance : invitation à la renseigner. Sinon 2 prochaines étapes non planifiées (retard > 3 semaines masqué). Mention « à confirmer avec le médecin ». |
| V2 | Planifier | « Planifier » → fiche RDV type Vaccin à la date prévue 10:00 (ou dans 7 jours si passée), avec la liste des vaccins ; une fois enregistré, l'étape disparaît des suggestions. |
| V3 | Calendrier complet | « Tout le calendrier » : chaque étape avec ✓ date planifiée / « passé » / « Planifier ». |
| R3 | RDV | Questions à poser (une par ligne, comptées sur la carte), rappels veille 19 h / 1 h avant, compte rendu, « Agenda » (agenda du téléphone), « Noter poids et taille » après la visite. |

## Widget, raccourcis

| # | Parcours | Attendu |
|---|---|---|
| W1 | Couche depuis l'écran d'accueil | 1 appui ; ligne « ✓ Noté : Pipi à 14:07 · Annuler » pendant 10 min. |
| W2 | Tétée depuis le widget | G/D démarre, change, met en pause ; « Fin tétée » enregistre. |
| W3 | Biberon depuis le widget | Ouvre une fenêtre transparente avec la fiche pré-remplie. |
| W4 | Mode sombre / compact | Couleurs nuit ; hauteur < 128 dp → une ligne de 4 boutons. |
| W5 | Raccourcis | Appui long sur l'icône : Pipi, Caca (enregistrent + message), Biberon (fiche), Tétée (lance le sein conseillé et ouvre l'appli). |

## Synchronisation (`sync/`, `ui/settings/SyncSection.kt`, `server/`)

| # | Parcours | Attendu |
|---|---|---|
| Y1 | Créer | Adresse du serveur + prénom (+ mot de passe de création si exigé) → vérification du serveur → espace créé → fenêtre « Inviter ». Les données existantes sont partagées. |
| Y2 | Inviter | Lien `https://serveur/join#k=CLÉ` (clé après `#`, jamais envoyée au serveur) partageable/copiable, avec mise en garde. |
| Y3 | Rejoindre | Lien ouvert → page du serveur → bouton qui ouvre l'appli → fenêtre « Rejoindre » pré-remplie. Code seul : demander l'adresse du serveur. Code faux → « Espace famille inconnu ». |
| Y4 | Au quotidien | Saisie sur un téléphone visible sur l'autre en ~20 s (appli ouverte) ; en arrière-plan dans les minutes qui suivent ; hors ligne, tout fonctionne et se rattrape. |
| Y5 | Conflits | Même saisie modifiée des deux côtés : la plus récente gagne partout ; suppression puis « Annuler » : la saisie revient partout. |
| Y6 | Chronos partagés | Tétée lancée sur A visible et arrêtable sur B. |
| Y7 | Arrêter | Garde les données locales ; il faut une nouvelle invitation pour reprendre. |
| Y8 | Sécurité | Adresse `http://` hors réseau local → avertissement visible. |

## Données

| # | Parcours | Attendu |
|---|---|---|
| D1 | Mise à jour de l'appli | Aucune perte (migrations v1→v2→v3 testées). |
| D2 | Sauvegarde / restauration | JSON avec journal + RDV ; une sauvegarde d'avant les RDV n'efface pas ceux du téléphone. |
| D3 | Export CSV | Lisible dans Excel (BOM UTF-8, séparateur « ; », décimales à virgule). |
