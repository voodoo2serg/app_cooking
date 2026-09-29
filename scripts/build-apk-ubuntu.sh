#!/usr/bin/env bash
set -euo pipefail

# Run from the project root on x86_64 Ubuntu with JDK 17, curl, unzip and ~8 GB free.
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
sdk_dir="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
gradle_dir="${GRADLE_HOME:-$HOME/.local/gradle-8.11.1}"
cmdline_zip="commandlinetools-linux-15859902_latest.zip"
cmdline_sha="4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583"

command -v java >/dev/null || { echo 'Install JDK 17 first.' >&2; exit 1; }
command -v curl >/dev/null || { echo 'Install curl first.' >&2; exit 1; }
command -v unzip >/dev/null || { echo 'Install unzip first.' >&2; exit 1; }
mkdir -p "$sdk_dir/cmdline-tools" "$(dirname "$gradle_dir")"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT

if [[ ! -x "$sdk_dir/cmdline-tools/latest/bin/sdkmanager" ]]; then
  curl -fL --retry 3 "https://dl.google.com/android/repository/$cmdline_zip" -o "$work_dir/$cmdline_zip"
  printf '%s  %s\n' "$cmdline_sha" "$work_dir/$cmdline_zip" | sha256sum -c -
  unzip -q "$work_dir/$cmdline_zip" -d "$work_dir"
  mv "$work_dir/cmdline-tools" "$sdk_dir/cmdline-tools/latest"
fi

if [[ ! -x "$gradle_dir/bin/gradle" ]]; then
  curl -fL --retry 3 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip' -o "$work_dir/gradle.zip"
  unzip -q "$work_dir/gradle.zip" -d "$(dirname "$gradle_dir")"
fi

export ANDROID_SDK_ROOT="$sdk_dir"
export ANDROID_HOME="$sdk_dir"
echo 'Review and accept the Android SDK licenses if prompted:'
"$sdk_dir/cmdline-tools/latest/bin/sdkmanager" --licenses
"$sdk_dir/cmdline-tools/latest/bin/sdkmanager" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
cd "$project_dir"
"$gradle_dir/bin/gradle" --no-daemon :app:assembleDebug
"$sdk_dir/build-tools/35.0.0/apksigner" verify --verbose --print-certs app/build/outputs/apk/debug/app-debug.apk
echo "APK: $project_dir/app/build/outputs/apk/debug/app-debug.apk"
