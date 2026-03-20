#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$ROOT_DIR/out"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

find "$ROOT_DIR/src/main/java" -name "*.java" -print0 \
  | xargs -0 javac --enable-preview --release 25 -d "$OUT_DIR"

echo "Compiled classes to $OUT_DIR"
