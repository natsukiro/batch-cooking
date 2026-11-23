#!/usr/bin/env bash
# Installe le SDK Android nécessaire au build (idempotent).
# Réseau requis : dl.google.com (SDK) ; Gradle/Maven passent par les registres habituels.
#   ANDROID_HOME=/chemin/sdk scripts/setup-android-sdk.sh
set -euo pipefail
cd "$(dirname "$0")/.."

SDK="${ANDROID_HOME:-/opt/android-sdk}"
TOOLS_ZIP="commandlinetools-linux-13114758_latest.zip"
PACKAGES=("platforms;android-36" "build-tools;36.0.0" "platform-tools")

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "→ Outils en ligne de commande Android dans $SDK"
  mkdir -p "$SDK/cmdline-tools"
  tmp="$(mktemp -d)"
  curl -fsSL -o "$tmp/tools.zip" "https://dl.google.com/android/repository/$TOOLS_ZIP"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi

missing=()
for p in "${PACKAGES[@]}"; do
  [ -d "$SDK/${p//;//}" ] || missing+=("$p")
done
if [ ${#missing[@]} -gt 0 ]; then
  echo "→ Paquets SDK : ${missing[*]}"
  yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses >/dev/null 2>&1 || true
  "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" "${missing[@]}" >/dev/null
fi

echo "sdk.dir=$SDK" > local.properties
echo "✓ SDK Android prêt dans $SDK (local.properties écrit)"
