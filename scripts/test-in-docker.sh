#!/usr/bin/env bash
set -euo pipefail

IMAGE_NAME=${IMAGE_NAME:-transloadit-android-sdk-dev}
CACHE_ROOT=.android-docker
GRADLE_CACHE_DIR="$CACHE_ROOT/gradle"
HOME_DIR="$CACHE_ROOT/home"
DOCKER_PLATFORM=${DOCKER_PLATFORM:-linux/amd64}

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required to run this script." >&2
  exit 1
fi

# Attempt to connect to Docker, falling back to Colima's socket when available.
if ! docker info >/dev/null 2>&1; then
  if [[ -z "${DOCKER_HOST:-}" && -S "$HOME/.colima/default/docker.sock" ]]; then
    export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
  fi
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker daemon is not reachable. Start Docker (or Colima) and retry." >&2
  exit 1
fi

mkdir -p "$GRADLE_CACHE_DIR" "$HOME_DIR/.android"

# Build the image (cached after the first run)
docker build --platform "$DOCKER_PLATFORM" -t "$IMAGE_NAME" -f Dockerfile .

if [[ $# -eq 0 ]]; then
  GRADLE_ARGS=(test)
else
  GRADLE_ARGS=("$@")
fi

GRADLE_CMD=("./gradlew" "--no-daemon")
GRADLE_CMD+=("${GRADLE_ARGS[@]}")
printf -v GRADLE_CMD_STRING '%q ' "${GRADLE_CMD[@]}"

ANDROID_SDK_ROOT=/opt/android-sdk
CONTAINER_HOME=/workspace/$HOME_DIR

DOCKER_ARGS=(
  --rm
  --platform "$DOCKER_PLATFORM"
  --user "$(id -u):$(id -g)"
  -e ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT"
  -e ANDROID_HOME="$ANDROID_SDK_ROOT"
  -e GRADLE_USER_HOME=/workspace/$GRADLE_CACHE_DIR
  -e HOME="$CONTAINER_HOME"
  -v "$PWD":/workspace
  -w /workspace
)

HOST_JAVA_SDK="$(cd "$(dirname "$PWD")" && pwd)/java-sdk"
if [[ -d "$HOST_JAVA_SDK" ]]; then
  DOCKER_ARGS+=(-v "$HOST_JAVA_SDK":/workspace/../java-sdk)
fi

exec docker run "${DOCKER_ARGS[@]}" "$IMAGE_NAME" bash -lc "$GRADLE_CMD_STRING"
