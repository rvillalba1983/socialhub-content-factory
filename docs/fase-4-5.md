# SocialHub — Fases 4 y 5: Google Drive y orquestador de IA

Estado: **plan aprobado por respuestas de Raúl** · 2 nov 2026

## Fase 4 · Google Drive (carpeta sincronizada G:)

| # | Tema | Decisión |
|---|---|---|
| 1 | Estructura de carpetas | No se crea ni se mueve nada. Raúl indica qué carpeta de G: hace cada papel: material, borradores, generados, aprobados, publicados, informes y encargos. SocialHub solo usa lo mapeado |
| 2 | Nivel del mapeo | En la marca. Cada promoción lo hereda y puede cambiar el papel que necesite |
| 3 | Archivos y publicaciones | Cada pieza del spec.json puede llevar `publicacion` con el id de la publicación. Si no lo lleva, Raúl la asocia desde la app. Nada se adivina |
| 4 | Publicaciones en modo real | Alta y edición desde el centro de control: fecha, hora, plataforma, tipo, título y vía. Los estados siguen su ciclo de vida y nunca pasan a «publicada» sin confirmación |
| 5 | Si G: no está disponible | La tarea queda bloqueada con el motivo, sin gastar intentos, y vuelve a la cola sola cuando la carpeta aparece. Drive se ve en la armería y en los avisos |

## Fase 5 · Orquestador de IA

| # | Tema | Decisión |
|---|---|---|
| 1 | Quién recoge los encargos | Una tarea programada nueva de Claude, «Encargos de SocialHub», en la nube y con el conector de Drive. Las tareas programadas actuales no se tocan hasta validar SocialHub |
| 2 | Frecuencia | 3 veces al día, hacia las 9:40, 14:40 y 20:40 (hora de España) |
| 3 | Primeros encargos | spec.json de la semana (encadenado con la generación de piezas), textos y hashtags, balance semanal e investigación |
| 4 | Modelo | Por tipo de encargo: investigación, textos y spec con Sonnet; balance con Opus. Si falla dos veces, sube un nivel. Se puede cambiar por tipo y por tarea |
| 5 | Límite | 10 encargos a Claude al día por defecto, configurable. Si se llega al límite, el resto espera en la cola con el motivo a la vista. Tokens y coste figuran como «desconocido», porque Claude Pro no los expone |

## Cómo encaja

- SocialHub **no llama a la API de Claude**. Escribe el encargo (`contracts/task-request.v1.schema.json`) en la carpeta de intercambio de Drive.
  - La tarea queda «en espera · encargada a Claude (modelo)».
  - Cuando aparece el resultado (`task-result.v1`), SocialHub lo valida y completa la tarea, la marca con error o la pasa a Raúl (`needs_human`).
- **Proveedor, modelo y agente** van separados: Claude Pro (proveedor), Haiku/Sonnet/Opus (modelos) y «tarea programada en la nube» (agente o runtime). OpenCode podrá añadirse más adelante como otro adaptador, sin tocar campañas ni publicaciones.
- **Registro de cada encargo:** proveedor, modelo, agente, tipo, inicio, fin, duración, estado, error e intentos. Tokens y coste figuran como desconocidos.

## Bloques (una rama y una PR por bloque)

1. **Fase 4 · carpetas y publicaciones:**
   - Papeles de carpeta en marca y promoción.
   - Alta y edición de publicaciones.
   - Asociación de archivos a publicaciones.
   - Comprobación de Drive y tareas bloqueadas por falta de carpeta.
2. **Fase 5 · intercambio con Claude:**
   - Encargos y resultados con validación de los contratos.
   - Tareas en espera de Claude y límite diario.
   - Modelo por tipo, con subida tras dos fallos.
   - Registro de ejecuciones.
3. **Fase 5 · encargos concretos:**
   - spec semanal (al llegar, crea solo la tarea de piezas).
   - Textos y hashtags.
   - Balance semanal.
   - Investigación.
   - La tarea programada «Encargos de SocialHub».
