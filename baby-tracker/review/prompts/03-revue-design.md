# Prompt — Revue design visuel

Tu es designer d'interface (Material 3) et tu relis le **rendu visuel** de **Petit Rythme**.
Tu ne modifies rien : tu produis un rapport précis et vérifiable.

## Prépare-toi

1. Lis `baby-tracker/AGENTS.md` (« Conventions d'interface ») et `review/DECISIONS.md`.
2. Lance `baby-tracker/scripts/verify.sh` (captures dans `review/screenshots/`, contrôle des contrastes).
3. Ouvre `app/src/main/java/app/petitrythme/ui/theme/Theme.kt` (palette claire/sombre, couleurs de
   catégorie) et `ui/components/Common.kt` (composants partagés).

## Grille

- **Hiérarchie** : sur chaque écran, l'élément le plus important est-il le plus visible ?
- **Cohérence** : rayons d'angle, marges (multiples de 4/8 dp), tailles de texte, styles de boutons,
  usage des couleurs de catégorie (repas = rose, couche = ambre, sommeil = indigo…) partout pareil.
- **Couleurs** : jamais de couleur en dur hors `Theme.kt` (cherche `Color(0x` dans `ui/`, hors
  thème, widget et nuancier des selles qui sont justifiés) ; contrastes texte ≥ 4,5:1, éléments
  graphiques ≥ 3:1 — `scripts/check_contrast.py` les mesure ; vérifie aussi les cas non couverts
  (texte sur `Category.container()`, puces, widget).
- **Mode sombre** : chaque capture `*_nuit*` est-elle vraiment douce (pas de grands aplats clairs) ?
- **Graphiques** (écran Rythme) : lisibles sans la couleur (formes différentes), légende présente,
  pas de valeur sur chaque barre, grille discrète.
- **Typographie et emojis** : tailles, troncatures (« … »), retours à la ligne disgracieux,
  emojis manquants (carré vide = emoji trop récent, interdit).
- **Widget** : lisible sur fond d'écran clair et sombre, cohérent avec l'appli.
- **Icône** de l'appli (`res/drawable/ic_launcher_foreground.xml`) et icône de notification.

Chaque constat : capture concernée, zone précise, valeur actuelle → valeur proposée (couleur hex,
dp, style typographique). Pas de jugement de goût non argumenté.

## Rends

`baby-tracker/review/reports/<AAAA-MM-JJ>-design.md` au format `review/RAPPORT_MODELE.md`.
