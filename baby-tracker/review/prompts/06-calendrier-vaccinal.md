# Prompt — Mise à jour annuelle du calendrier vaccinal

Le calendrier des vaccinations français est publié chaque année (en général au printemps).
`app/src/main/java/app/petitrythme/data/VaccineSchedule.kt` en contient la partie nourrisson
(naissance → 18 mois), sous forme de **suggestions**.

1. Trouve le calendrier officiel de l'année en cours (ministère de la Santé — sante.gouv.fr,
   Santé publique France — vaccination-info-service.fr, HAS). Si le site officiel est inaccessible
   (protection anti-robots fréquente), recoupe au moins trois sources sérieuses (Assurance Maladie,
   Institut Pasteur, Vidal, sociétés de pédiatrie) et **dis-le** dans ton compte rendu.
2. Compare âge par âge avec `VaccineSchedule.steps` : vaccins, numéro de dose, obligatoire ou
   recommandé, nouveaux vaccins (ex. prévention de la bronchiolite, nouveaux pneumocoques).
3. Si rien ne change : mets seulement à jour `SOURCE`/`CHECKED` et le commentaire d'en-tête.
4. Si quelque chose change : modifie les étapes **sans changer les clés existantes** (`m2`, `m3`…,
   elles sont liées aux rendez-vous déjà planifiés) ; une nouvelle étape reçoit une nouvelle clé.
   Mets à jour `LogicTest.vaccineSuggestions`, la capture `planVaccine`, `CHANGELOG.md`.
5. Livre via `review/prompts/05-corriger-et-livrer.md` (version correctif).
6. Rends : sources consultées (URL + date), différences trouvées, ce qui a été modifié.
