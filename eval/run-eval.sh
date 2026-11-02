#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../backend"
./mvnw -q compile spring-boot:run -Dspring-boot.run.main-class=com.banquemisr.copilot.evaluation.EvaluationRunner
