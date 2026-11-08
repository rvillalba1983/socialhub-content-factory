#!/bin/bash
# Pruebas del backend con resumen de fallos. Sin argumentos, todas; con un patrón, solo esas.
# Uso: bash scripts/entorno/pruebas.sh 'BackupIntegrationTest,ModularityTest'
# Salida completa en /tmp/socialhub-pruebas.txt
repo=$(cd "$(dirname "$0")/../.." && pwd)
chown -R claude "$repo"
cd "$repo/backend"
timeout 1500 /opt/jdk/mvnu -q verify ${1:+-Dtest=$1 -Dsurefire.failIfNoSpecifiedTests=false} > /tmp/socialhub-pruebas.txt 2>&1
code=$?
grep -E "Tests run:.*Fail|\[ERROR\]   [A-Za-z]" /tmp/socialhub-pruebas.txt | cut -c1-400 | head -20
grep -E "^Caused by|expected|Expected|Status expected|No value at" /tmp/socialhub-pruebas.txt | sort | uniq -c | sort -rn | cut -c1-500 | head -8
echo "Maven terminó con $code"
exit $code
