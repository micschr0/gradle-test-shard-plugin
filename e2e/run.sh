#!/usr/bin/env bash
# Runs the Shardwise e2e pipeline via Docker with host networking.
# Simulates GitLab CI stages (build → test → verify) in isolated containers
# using the official gradle:8.11-jdk17 image.
#
# Requires: Docker daemon running.
set -euo pipefail

cd "$(dirname "$0")/.."

if ! docker info >/dev/null 2>&1; then
    echo "ERROR: Docker is required but not available."
    exit 1
fi

exec bash e2e/run-docker.sh
