#!/usr/bin/env bash

set -eu

base_ref="${1:-HEAD^}"
head_ref="${2:-HEAD}"

if ! git rev-parse --verify "${base_ref}^{commit}" >/dev/null 2>&1; then
  echo "Vercel build enabled: no previous commit is available."
  exit 1
fi

relevant_paths=(
  ".dockerignore"
  "Dockerfile.vercel"
  "vercel.json"
  "scripts/vercel-ignore-build.sh"
  "services/assistant"
  "apps/mobile/shared"
  "apps/mobile/build.gradle.kts"
  "apps/mobile/settings.gradle.kts"
  "apps/mobile/gradle.properties"
  "apps/mobile/gradle"
  "apps/mobile/gradlew"
  "apps/mobile/gradlew.bat"
)

if git diff --quiet "${base_ref}" "${head_ref}" -- "${relevant_paths[@]}"; then
  echo "Vercel build skipped: the assistant service and its dependencies did not change."
  exit 0
fi

echo "Vercel build enabled: an assistant-service dependency changed."
exit 1
