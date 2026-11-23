# Décisions volontaires (à ne pas « corriger » sans en parler)

Chaque point ci-dessous est un choix assumé. Un relecteur peut le remettre en question dans son
rapport (section « À discuter »), mais ne doit pas le modifier de lui-même.

## Produit

| Décision | Pourquoi |
|---|---|
| Une seule fiche bébé (pas de multi-enfants) | Simplicité ; le besoin exprimé concerne un bébé. |
| Synchro via **le serveur des parents** (pas Firebase/cloud tiers) | Choix explicite de l'utilisateur : données chez lui. |
| Pas de courbes de croissance OMS | Lourd à intégrer correctement ; risque d'erreur médicale. |
| Calendrier vaccinal en **suggestions** seulement, étapes en retard de plus de 3 semaines masquées | Ne pas culpabiliser ni se substituer au médecin ; un bébé installé tard ne doit pas voir 5 alertes. |
| Calendrier vaccinal codé en dur, vérifié en novembre 2026 | Pas de source officielle lisible par machine. **À revérifier chaque année** (avril, publication du nouveau calendrier). |
| Pas de vaccins/examens au-delà de 18 mois | Périmètre « bébé ». |
| Onglet « RDV » (abrégé) | 5 onglets : « Rendez-vous » ne tient pas en largeur. |
| Le widget affiche des heures (14:05) et non « il y a 2 h » | Un widget ne se rafraîchit pas en continu ; une heure ne devient jamais fausse. |
| Rappels (repas, RDV) par alarme **inexacte** (quelques minutes de marge) | L'alarme exacte exige une autorisation spéciale d'Android. |
| Pas de rappel de repas pendant une tétée en cours | Évident, mais à préserver. |
| « Tout effacer » efface aussi sur l'autre téléphone quand la synchro est active | Sémantique « suivi commun » ; le texte l'indique. |
| Restaurer une sauvegarde remplace le suivi commun (synchro active) | Idem. |

## Technique

| Décision | Pourquoi |
|---|---|
| Clé de signature **dans le dépôt** (`keystore/`, mot de passe dans `app/build.gradle.kts`) | Projet personnel : garantit que n'importe quelle future build s'installe par-dessus. Risque accepté : quiconque a accès au dépôt peut signer une mise à jour. |
| HTTP non chiffré autorisé (`network_security_config.xml`) | Serveur sur le Wi-Fi de la maison possible ; l'appli avertit si `http://` vise Internet. |
| Conflits de synchro : **la modification la plus récente gagne** (horloge des téléphones) | Simple et suffisant pour deux parents ; pas de fusion champ par champ. |
| Les chronos en cours sont partagés (un seul état pour la famille) | Si l'un lance une tétée, l'autre la voit et peut l'arrêter. |
| Thème, rappels et prénom « vu par l'autre » sont propres à chaque téléphone | Préférences personnelles. |
| Données en clair sur le serveur (pas de chiffrement de bout en bout) | Le serveur appartient aux parents ; HTTPS protège le transport. À rediscuter si hébergement tiers. |
| Emojis comme icônes de catégorie | Lisibles, chaleureux, sans dépendance d'icônes ; limités à Unicode ≤ 13. |
| Pas de Hilt/Navigation : un ViewModel, onglets par état | Appli petite ; moins de dépendances. |
| Tests d'interface par captures Robolectric (pas d'émulateur) | L'environnement de build n'a pas d'accélération matérielle. |
