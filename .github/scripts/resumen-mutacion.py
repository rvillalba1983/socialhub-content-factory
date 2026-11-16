"""Resume el informe de PIT (mutations.xml) por módulo y por clase.

Imprime una tabla Markdown (mutantes matados / total) y, si existe GITHUB_STEP_SUMMARY, la añade al resumen del trabajo.
Cuentan como «matados» KILLED y TIMED_OUT. Los mutantes sin cobertura (NO_COVERAGE) cuentan como vivos, igual que en
el porcentaje de PIT. Se ignoran los no viables.
Uso: python3 resumen-mutacion.py backend/target/pit-reports/mutations.xml
"""
import collections
import os
import sys
import xml.etree.ElementTree as ET

path = sys.argv[1] if len(sys.argv) > 1 else "target/pit-reports/mutations.xml"
if not os.path.exists(path):
    print("Sin informe de mutación (no había clases que mutar).")
    sys.exit(0)

KILLED = {"KILLED", "TIMED_OUT"}
modules = collections.OrderedDict()
classes = collections.defaultdict(lambda: [0, 0])
for m in ET.parse(path).getroot().iter("mutation"):
    status = m.get("status")
    if status in ("NOT_STARTED", "NON_VIABLE"):
        continue
    cls = m.findtext("mutatedClass", "")
    parts = cls.split(".")
    module = parts[2] if len(parts) > 3 else "(raíz)"
    top = cls.split("$")[0]
    for acc in (modules.setdefault(module, [0, 0]), classes[top]):
        acc[1] += 1
        if status in KILLED:
            acc[0] += 1


def pct(a):
    return 100.0 * a[0] / a[1] if a[1] else 0.0


total = [sum(a[0] for a in modules.values()), sum(a[1] for a in modules.values())]
lines = ["### Pruebas de mutación (PIT)", "", "| Módulo | Matados | Total | % |", "|---|---:|---:|---:|"]
for name, a in sorted(modules.items(), key=lambda kv: pct(kv[1])):
    lines.append(f"| {name} | {a[0]} | {a[1]} | {pct(a):.0f} % |")
lines.append(f"| **Total** | **{total[0]}** | **{total[1]}** | **{pct(total):.0f} %** |")
lines += ["", "<details><summary>Por clase (de menos a más)</summary>", "", "| Clase | Matados | Total | % |", "|---|---:|---:|---:|"]
for name, a in sorted(classes.items(), key=lambda kv: (pct(kv[1]), kv[0])):
    lines.append(f"| {name.replace('com.socialhub.', '')} | {a[0]} | {a[1]} | {pct(a):.0f} % |")
lines += ["", "</details>"]
text = "\n".join(lines)
print(text)
summary = os.environ.get("GITHUB_STEP_SUMMARY")
if summary:
    with open(summary, "a", encoding="utf-8") as f:
        f.write(text + "\n")
