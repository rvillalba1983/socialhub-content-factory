#!/bin/bash
# Espera a que terminen todas las comprobaciones de una PR (o de una rama) y las resume en pocas líneas.
# En los fallos muestra las anotaciones (prueba y primer renglón del error), no el registro entero.
# Uso: scripts/estado-pr.sh 31            (número de PR)
#      scripts/estado-pr.sh mi-rama       (último commit de la rama)
#      scripts/estado-pr.sh 31 --sin-esperar
set -uo pipefail
repo=rvillalba1983/socialhub-content-factory
ref=${1:?Indica el número de PR o la rama}
if [[ "$ref" =~ ^[0-9]+$ ]]; then
  sha=$(gh api "repos/$repo/pulls/$ref" --jq .head.sha)
else
  sha=$(gh api "repos/$repo/commits/$ref" --jq .sha)
fi
checks() { gh api "repos/$repo/commits/$sha/check-runs?per_page=100" --jq '.check_runs[] | [.id, .name, .status, (.conclusion // "-")] | @tsv'; }
if [ "${2:-}" != "--sin-esperar" ]; then
  for _ in $(seq 1 120); do   # hasta 60 minutos
    out=$(checks)
    [ -n "$out" ] && ! grep -qv $'\tcompleted\t' <<<"$out" && break
    sleep 30
  done
fi
echo "Commit ${sha:0:7}"
checks | while IFS=$'\t' read -r id name status conclusion; do
  case "$conclusion" in success) mark="✅";; skipped|neutral) mark="➖";; -) mark="⏳ $status";; *) mark="❌ $conclusion";; esac
  echo "$mark  $name"
  if [ "$conclusion" = failure ]; then
    gh api "repos/$repo/check-runs/$id/annotations" --jq '.[] | select(.annotation_level=="failure") | "     · \(.title // "") \(.message | split("\n")[0] | .[0:300])"' | head -12
  fi
done
