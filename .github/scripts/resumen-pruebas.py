"""Resume las pruebas fallidas de Maven (informes de Surefire) en pocas líneas.

Escribe una anotación ::error:: por prueba (clase, método y primer renglón del error) y una tabla en el resumen del
trabajo. Así se lee el fallo sin descargar el registro entero.
Uso: python3 resumen-pruebas.py backend/target/surefire-reports
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

folder = sys.argv[1] if len(sys.argv) > 1 else "target/surefire-reports"
failures = []
for path in sorted(glob.glob(os.path.join(folder, "TEST-*.xml"))):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    for case in root.iter("testcase"):
        for kind in ("failure", "error"):
            node = case.find(kind)
            if node is None:
                continue
            message = (node.get("message") or node.text or "").strip().splitlines()
            first = next((line.strip() for line in message if line.strip()), "(sin mensaje)")
            cls = case.get("classname", "").rsplit(".", 1)[-1]
            failures.append((cls, case.get("name", ""), first[:300]))

if not failures:
    print("No hay pruebas fallidas en los informes (el fallo es de compilación o de otro paso).")
    sys.exit(0)

for cls, name, first in failures[:30]:
    clean = first.replace("%", "%25").replace("\r", "").replace("\n", " ")
    print(f"::error title={cls}.{name}::{clean}")

summary = os.environ.get("GITHUB_STEP_SUMMARY")
if summary:
    with open(summary, "a", encoding="utf-8") as out:
        out.write(f"### {len(failures)} pruebas fallidas\n\n| Prueba | Error |\n|---|---|\n")
        for cls, name, first in failures[:30]:
            out.write(f"| `{cls}.{name}` | {first.replace('|', '/')} |\n")
