#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
classpath_file="$(mktemp)"
trap 'rm -f "$classpath_file"' EXIT

cd "$root/backend"
./mvnw -q -B dependency:build-classpath \
  -Dmdep.outputFile="$classpath_file" \
  -Dmdep.includeArtifactIds=pdfbox,pdfbox-io,fontbox,commons-logging

cd "$root"
java -cp "$(cat "$classpath_file")" tools/CorpusPdfGenerator.java corpus/pdf-sources corpus/pdf
