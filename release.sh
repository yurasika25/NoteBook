#!/usr/bin/env bash
set -euo pipefail

readonly APP_ID="com.notes.easynotebook"
readonly REQUIRED_KEY_ALIAS="key_old_lake"
readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly REPO_PARENT="$(cd "$REPO_ROOT/.." && pwd)"
readonly DEFAULT_KEYSTORE_PATH="$HOME/Desktop/work_table/2026/projects/easy_note_book/key_store_market.jks"
readonly DEFAULT_PLAY_CREDENTIALS_PATH="$HOME/.config/easynotebook/play-service-account.json"
readonly ARCHIVE_ROOT="${EASY_NOTEBOOK_RELEASE_ARCHIVE_DIR:-$REPO_PARENT/EasyNotebook-release-archive}"
readonly PRIOR_OUTPUT_ROOT="${EASY_NOTEBOOK_RELEASE_OUTPUT_DIR:-$REPO_PARENT/EasyNotebook-release-output}"

usage() {
  cat <<'USAGE'
Usage: ./release.sh internal [--dry-run]

Builds and verifies a signed Android App Bundle. Only `internal` is supported.
`--dry-run` performs all local build and verification work but never archives or uploads.

Required configuration:
  EASY_NOTEBOOK_KEYSTORE_PATH       Optional override for the local upload keystore.
  EASY_NOTEBOOK_KEYSTORE_PASSWORD   Or Keychain service "EasyNotebook Release Keystore Password".
  EASY_NOTEBOOK_KEY_PASSWORD        Or Keychain service "EasyNotebook Release Key Password".
  PLAY_SERVICE_ACCOUNT_JSON         Optional override for the local Play JSON; required for upload.

By default the script uses the configured local keystore and Play JSON paths. The
Keychain entries must use account `key_old_lake`. No credentials are read from Git.
USAGE
}

fail() {
  printf 'release.sh: %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "required command is unavailable: $1"
}

keychain_or_env_secret() {
  local env_name="$1"
  local keychain_service="$2"
  local value="${!env_name:-}"
  if [[ -n "$value" ]]; then
    printf '%s' "$value"
    return
  fi
  require_command security
  security find-generic-password -a "$REQUIRED_KEY_ALIAS" -s "$keychain_service" -w 2>/dev/null || \
    fail "configure $env_name or Keychain service '$keychain_service' for account $REQUIRED_KEY_ALIAS"
}

aapt2_path() {
  if [[ -n "${AAPT2_PATH:-}" && -x "${AAPT2_PATH}" ]]; then
    printf '%s' "$AAPT2_PATH"
    return
  fi
  local sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
  [[ -d "$sdk_root" ]] || fail "set AAPT2_PATH, ANDROID_SDK_ROOT, or ANDROID_HOME"
  local candidate
  candidate="$(find "$sdk_root/build-tools" -type f -name aapt2 -perm -111 2>/dev/null | sort | tail -n 1)"
  [[ -n "$candidate" ]] || fail "could not locate aapt2"
  printf '%s' "$candidate"
}

manifest_tree() {
  local aab="$1"
  local work_dir="$2"
  local manifest="$work_dir/AndroidManifest.xml"
  local manifest_apk="$work_dir/manifest.apk"
  unzip -p "$aab" base/manifest/AndroidManifest.xml >"$manifest"
  (cd "$work_dir" && zip -q "$manifest_apk" AndroidManifest.xml)
  "$(aapt2_path)" dump xmltree --file AndroidManifest.xml "$manifest_apk"
}

manifest_value() {
  local tree="$1"
  local name="$2"
  sed -nE "s/.*${name}.*Raw: \"([^\"]+)\".*/\1/p" <<<"$tree" | head -n 1
}

release_metadata() {
  local aab="$1"
  local metadata_dir="$2"
  local tree
  tree="$(manifest_tree "$aab" "$metadata_dir")"
  RELEASE_PACKAGE="$(sed -nE 's/.*A: package="([^"]+)".*/\1/p' <<<"$tree" | head -n 1)"
  RELEASE_VERSION_CODE="$(manifest_value "$tree" versionCode)"
  RELEASE_VERSION_NAME="$(manifest_value "$tree" versionName)"
}

find_existing_version_code() {
  local version_code="$1"
  local scan_dir="$2"
  [[ -d "$scan_dir" ]] || return 1
  local aab metadata_dir candidate_code
  local saved_package="$RELEASE_PACKAGE"
  local saved_version_code="$RELEASE_VERSION_CODE"
  local saved_version_name="$RELEASE_VERSION_NAME"
  while IFS= read -r -d '' aab; do
    metadata_dir="$(mktemp -d "${TMPDIR:-/tmp}/easynotebook-archive-check.XXXXXX")"
    candidate_code=""
    if release_metadata "$aab" "$metadata_dir" 2>/dev/null; then
      candidate_code="$RELEASE_VERSION_CODE"
    fi
    rm -rf "$metadata_dir"
    if [[ "$candidate_code" == "$version_code" ]]; then
      RELEASE_PACKAGE="$saved_package"
      RELEASE_VERSION_CODE="$saved_version_code"
      RELEASE_VERSION_NAME="$saved_version_name"
      printf '%s\n' "$aab"
      return 0
    fi
  done < <(find "$scan_dir" -type f -name '*.aab' -print0)
  RELEASE_PACKAGE="$saved_package"
  RELEASE_VERSION_CODE="$saved_version_code"
  RELEASE_VERSION_NAME="$saved_version_name"
  return 1
}

[[ $# -ge 1 && $# -le 2 ]] || { usage; exit 2; }
[[ "$1" == "internal" ]] || fail "only the Internal testing track is supported"
DRY_RUN=false
if [[ $# -eq 2 ]]; then
  [[ "$2" == "--dry-run" ]] || { usage; exit 2; }
  DRY_RUN=true
fi

cd "$REPO_ROOT"
[[ "$(git branch --show-current)" == "main" ]] || fail "releases must start from main"
[[ -z "$(git status --porcelain)" ]] || fail "release requires a clean Git working tree"
KEYSTORE_PATH="${EASY_NOTEBOOK_KEYSTORE_PATH:-$DEFAULT_KEYSTORE_PATH}"
PLAY_CREDENTIALS_PATH="${PLAY_SERVICE_ACCOUNT_JSON:-$DEFAULT_PLAY_CREDENTIALS_PATH}"
[[ -f "$KEYSTORE_PATH" && -r "$KEYSTORE_PATH" ]] || fail "configured upload keystore is not a readable file"
[[ "${EASY_NOTEBOOK_KEY_ALIAS:-$REQUIRED_KEY_ALIAS}" == "$REQUIRED_KEY_ALIAS" ]] || fail "release alias must be $REQUIRED_KEY_ALIAS"
if [[ "$DRY_RUN" == false ]]; then
  [[ -r "$PLAY_CREDENTIALS_PATH" ]] || fail "configured Play service-account JSON is not readable"
fi

require_command bundle
require_command jarsigner
require_command keytool
require_command unzip
require_command zip
require_command shasum

STORE_PASSWORD="$(keychain_or_env_secret EASY_NOTEBOOK_KEYSTORE_PASSWORD 'EasyNotebook Release Keystore Password')"
KEY_PASSWORD="$(keychain_or_env_secret EASY_NOTEBOOK_KEY_PASSWORD 'EasyNotebook Release Key Password')"
BUILD_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/easynotebook-release-build.XXXXXX")"
trap 'unset STORE_PASSWORD KEY_PASSWORD; rm -rf "$BUILD_ROOT"' EXIT

printf 'Running release tests…\n'
EASY_NOTEBOOK_KEYSTORE_PASSWORD="$STORE_PASSWORD" \
EASY_NOTEBOOK_KEY_PASSWORD="$KEY_PASSWORD" \
EASY_NOTEBOOK_KEY_ALIAS="$REQUIRED_KEY_ALIAS" \
EASY_NOTEBOOK_KEYSTORE_PATH="$KEYSTORE_PATH" \
bash ./gradlew --no-daemon -PeasyNotebookBuildDir="$BUILD_ROOT/app" :app:testDebugUnitTest :app:bundleRelease

AAB_PATH="$BUILD_ROOT/app/outputs/bundle/release/app-release.aab"
MAPPING_PATH="$BUILD_ROOT/app/outputs/mapping/release/mapping.txt"
[[ -s "$AAB_PATH" ]] || fail "signed AAB was not produced at the isolated build path"

METADATA_DIR="$(mktemp -d "${TMPDIR:-/tmp}/easynotebook-release-metadata.XXXXXX")"
trap 'unset STORE_PASSWORD KEY_PASSWORD; rm -rf "$BUILD_ROOT" "$METADATA_DIR"' EXIT
release_metadata "$AAB_PATH" "$METADATA_DIR"
[[ "$RELEASE_PACKAGE" == "$APP_ID" ]] || fail "AAB package mismatch: $RELEASE_PACKAGE"
[[ -n "$RELEASE_VERSION_NAME" && -n "$RELEASE_VERSION_CODE" ]] || fail "could not derive version metadata from signed AAB"
SHA256="$(shasum -a 256 "$AAB_PATH" | awk '{print $1}')"

EXISTING_AAB=""
EXISTING_AAB="$(find_existing_version_code "$RELEASE_VERSION_CODE" "$ARCHIVE_ROOT" || true)"
if [[ -z "$EXISTING_AAB" ]]; then
  EXISTING_AAB="$(find_existing_version_code "$RELEASE_VERSION_CODE" "$PRIOR_OUTPUT_ROOT" || true)"
fi
if [[ -n "$EXISTING_AAB" && "$DRY_RUN" == false ]]; then
  fail "versionCode $RELEASE_VERSION_CODE is already archived at $EXISTING_AAB; refusing to upload it again"
fi

RELEASE_SOURCE_COMMIT="$(git rev-parse HEAD)"
export RELEASE_AAB_PATH="$AAB_PATH"
export RELEASE_MAPPING_PATH="$MAPPING_PATH"
export RELEASE_SOURCE_COMMIT
export RELEASE_VERSION_CODE
export RELEASE_VERSION_NAME
export VERIFIED_AAB_SHA256="$SHA256"
export AAPT2_PATH="$(aapt2_path)"
printf 'Verifying signed AAB…\n'
bundle exec fastlane android verify_release

if [[ "$DRY_RUN" == true ]]; then
  [[ -z "$EXISTING_AAB" ]] || printf 'Dry run: versionCode %s already exists at %s; no archive or upload was attempted.\n' "$RELEASE_VERSION_CODE" "$EXISTING_AAB"
  printf 'DRY RUN PASS: %s %s (%s), SHA-256 %s\n' "$RELEASE_PACKAGE" "$RELEASE_VERSION_NAME" "$RELEASE_VERSION_CODE" "$SHA256"
  exit 0
fi

ARCHIVE_DIR="$ARCHIVE_ROOT/${RELEASE_VERSION_NAME}-${RELEASE_VERSION_CODE}"
[[ ! -e "$ARCHIVE_DIR" ]] || fail "archive destination already exists: $ARCHIVE_DIR"
PLAY_SERVICE_ACCOUNT_JSON="$PLAY_CREDENTIALS_PATH" CONFIRM_INTERNAL_UPLOAD=YES bundle exec fastlane android upload_internal
umask 077
mkdir -p "$ARCHIVE_DIR"
cp -p "$AAB_PATH" "$ARCHIVE_DIR/app-release.aab"
[[ ! -f "$MAPPING_PATH" ]] || cp -p "$MAPPING_PATH" "$ARCHIVE_DIR/mapping.txt"
printf 'RELEASE PASS: Internal draft uploaded — %s %s (%s), SHA-256 %s\n' "$RELEASE_PACKAGE" "$RELEASE_VERSION_NAME" "$RELEASE_VERSION_CODE" "$SHA256"
