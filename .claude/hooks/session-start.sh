#!/bin/bash
# SessionStart hook for Claude Code cloud sessions: installs the Android SDK,
# makes the Meta VR CLI (metavr) work behind the cloud proxy, and warms Gradle,
# so agents can build, test and use metavr without manual setup.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "$CLAUDE_PROJECT_DIR"

SDK="${ANDROID_HOME:-$HOME/android-sdk}"
# Same command-line tools build that `metavr tools list` installs. Build-tools
# 35.0.0 is what AGP 8.13 uses; platform 36 matches compileSdk.
CMDLINE_TOOLS_VERSION=11076708
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"

# Node's built-in fetch ignores HTTPS_PROXY unless told otherwise; metavr's npm
# launcher downloads its native binary with it.
export NODE_USE_ENV_PROXY=1
CA_BUNDLE=/root/.ccr/ca-bundle.crt
if [ -f "$CA_BUNDLE" ]; then
  export NODE_EXTRA_CA_CERTS="$CA_BUNDLE"
fi

if [ ! -x "$SDKMANAGER" ]; then
  echo "Installing Android command-line tools $CMDLINE_TOOLS_VERSION" >&2
  tmp="$(mktemp -d)"
  curl -fsSL -o "$tmp/cmdline-tools.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
  unzip -q "$tmp/cmdline-tools.zip" -d "$tmp"
  mkdir -p "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi

# `yes` exits on SIGPIPE once sdkmanager stops reading; that is expected.
set +o pipefail
yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses >/dev/null
set -o pipefail
"$SDKMANAGER" --sdk_root="$SDK" --install \
  "platform-tools" "platforms;android-36" "build-tools;35.0.0" >/dev/null

export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"

{
  echo "export ANDROID_HOME=\"$SDK\""
  echo "export ANDROID_SDK_ROOT=\"$SDK\""
  echo "export PATH=\"$SDK/cmdline-tools/latest/bin:$SDK/platform-tools:\$PATH\""
  echo "export NODE_USE_ENV_PROXY=1"
  if [ -n "${NODE_EXTRA_CA_CERTS:-}" ]; then
    echo "export NODE_EXTRA_CA_CERTS=\"$NODE_EXTRA_CA_CERTS\""
  fi
} >> "$CLAUDE_ENV_FILE"

# Fetch metavr's native binary once so the first real command is fast.
npx -y metavr@latest --version >/dev/null 2>&1 || echo "metavr warm-up failed; run 'npx -y metavr@latest doctor'" >&2

# Download Gradle and all dependencies, and compile both modules. Maven Central
# rate-limits shared cloud IPs (HTTP 429), so retry with backoff; Gradle keeps
# what it already downloaded between attempts.
retry() {
  local attempt
  for attempt in 1 2 3 4; do
    if "$@"; then
      return 0
    fi
    echo "Attempt $attempt failed: $*; retrying in $((attempt * 20))s" >&2
    sleep $((attempt * 20))
  done
  "$@"
}
retry ./gradlew --quiet -p ha-client testClasses
retry ./gradlew --quiet :app:assembleDebug
