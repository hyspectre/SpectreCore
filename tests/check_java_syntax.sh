#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
javac -encoding UTF-8 -d "$TMP" "$ROOT/tests/JavaSyntaxSmoke.java"
find "$ROOT/src/main/java" -name '*.java' -print0 | sort -z | xargs -0 java -cp "$TMP" JavaSyntaxSmoke
