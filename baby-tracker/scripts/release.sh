#!/usr/bin/env bash
# Publie l'APK de la version déclarée dans app/build.gradle.kts dans release/.
# À lancer APRÈS scripts/verify.sh (qui construit l'APK et vérifie la signature).
set -euo pipefail
cd "$(dirname "$0")/.."

NAME="$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)"
CODE="$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts)"
APK=app/build/outputs/apk/release/app-release.apk
[ -f "$APK" ] || { echo "✗ $APK absent : lancez scripts/verify.sh" >&2; exit 1; }
[ -f "release/PetitRythme-$NAME.apk" ] && cmp -s "$APK" "release/PetitRythme-$NAME.apk" && { echo "Déjà publié : $NAME"; exit 0; }

grep -q "^## $NAME" CHANGELOG.md || { echo "✗ Ajoutez une section « ## $NAME » dans CHANGELOG.md" >&2; exit 1; }

mkdir -p release
find release -name 'PetitRythme-*.apk' ! -name "PetitRythme-$NAME.apk" -delete
cp "$APK" "release/PetitRythme-$NAME.apk"
sed -i "s#release/PetitRythme-[0-9.]*\.apk#release/PetitRythme-$NAME.apk#g" README.md
echo "✓ release/PetitRythme-$NAME.apk (versionCode $CODE)"
