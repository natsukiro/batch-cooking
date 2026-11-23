# Rapport de relecture — <type> — <AAAA-MM-JJ>

- **Relecteur** : <IA / modèle>
- **Version relue** : <versionName (versionCode)> — commit <hash court>
- **`scripts/verify.sh`** : <vert / rouge (quoi)>

## Résumé

<3 à 5 lignes : état général, les 3 problèmes les plus importants.>

## Constats

Un constat par bloc, du plus grave au moins grave. Sévérités :
- **Bloquant** : perte/corruption de données, plantage, faille de sécurité, information médicale fausse.
- **Majeur** : fonctionnalité cassée ou trompeuse, parcours de `PARCOURS.md` non respecté, régression.
- **Mineur** : gêne réelle mais contournable, incohérence visible.
- **Suggestion** : amélioration, à discuter.

### [Bloquant|Majeur|Mineur|Suggestion] <titre court>

- **Où** : `chemin/fichier.kt:ligne` (ou capture `review/screenshots/xx.png`)
- **Constat** : <ce qui ne va pas, factuel>
- **Scénario** : <étapes concrètes / données qui le déclenchent → résultat obtenu vs attendu>
- **Preuve** : <extrait de code, test qui échoue, mesure de contraste, capture…>
- **Correction proposée** : <précise, minimale>
- **Test à ajouter** : <nom et contenu du test qui empêchera la régression>

## À discuter

<Points qui touchent à `DECISIONS.md` ou au produit : ne pas corriger sans accord.>

## Vérifié sans problème

<Liste courte de ce qui a été contrôlé et trouvé correct (pour ne pas refaire le travail).>
