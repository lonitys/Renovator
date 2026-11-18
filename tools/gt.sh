#!/usr/bin/env bash
# Runs the GameTests and prints only compile errors, failures and the summary line.
cd "$(dirname "$0")/.." || exit 1
rm -f run/logs/latest.log
out=$(timeout 1200 ./gradlew runGameTestServer --console=plain "$@" 2>&1)
if echo "$out" | grep -q "compileJava FAILED"; then
  echo "$out" | grep -B1 -A3 -E "Fehler|error:" | grep -v "^--$" | head -60
  echo "COMPILE FAILED"; exit 1
fi
log=run/logs/latest.log
grep -E "failed at|failed!|Exception" "$log" | grep -v "^\s*at " | head -40
grep -E "required tests passed|required tests failed|tests failed" "$log" | tail -3
echo "$out" | grep -E "^BUILD" | tail -1
