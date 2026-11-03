#!/bin/bash
# Genera los SVG de los diagramas con D2 (https://d2lang.com), con los colores de la web (estilo.d2).
# Uso: docs/diagramas/generar.sh   (necesita el programa d2 instalado)
set -e
cd "$(dirname "$0")"
d2 --layout dagre anillo.d2 anillo.svg
for f in ciclo-publicacion vias-publicacion encargos-claude; do
  d2 --layout elk "$f.d2" "$f.svg"
done
