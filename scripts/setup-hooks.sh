#!/usr/bin/env bash
# Installe le pre-commit hook pour ce repo
set -euo pipefail

HOOK_SRC="$(dirname "$0")/pre-commit"
HOOK_DST="$(git rev-parse --git-dir)/hooks/pre-commit"

if [ ! -f "$HOOK_SRC" ]; then
  echo "❌ Hook source introuvable: $HOOK_SRC"
  exit 1
fi

chmod +x "$HOOK_SRC"
ln -sf "../../scripts/pre-commit" "$HOOK_DST"
echo "✅ Pre-commit hook installé: $HOOK_DST"
echo "   Test rapide: ./gradlew compileJava test"
echo "   Build complet: FULL_CHECK=1 git commit ..."
echo "   Java: depuis .env ou JAVA_HOME système"
