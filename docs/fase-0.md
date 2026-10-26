# SocialHub — Fase 0: especificación técnica inicial

Estado: **aprobado por Raúl** · 25 oct 2026

Este documento cierra la Fase 0 de la especificación maestra (`socialhub.txt`). Las decisiones marcadas «Decidido» ya las ha tomado Raúl. Las marcadas «Propuesta» esperan su aprobación.

---

## 1. Decisiones tomadas

| Tema | Decisión | Estado |
|---|---|---|
| Frontend | Propuesta visual «Moderna»: anillo de producción, interfaz monocroma y color solo para estados | Decidido |
| Repositorio | GitHub, privado: `rvillalba1983/socialhub-content-factory` (lo crea Claude desde el navegador, con la sesión de Raúl) | Decidido |
| Métricas | Opcionales. Se obtienen por API oficial o mediante tareas de Claude. **No se usa Metricool** | Decidido |
| Java | Java 25 LTS | Decidido |
| Tecnología del frontend | React + TypeScript + Vite; anillo en SVG propio, sin React Flow | Decidido |
| Forma de uso | Web en el navegador, servida por el backend en localhost | Decidido |
| PostgreSQL | Incluido y arrancado por SocialHub (binarios propios) | Decidido |
| Sistema prioritario | Windows; macOS en la fase de distribución | Decidido |
| Idioma | Código en inglés; documentación e interfaz en español | Decidido |
| IA | Claude sigue trabajando desde Claude (tareas programadas con Claude Pro). SocialHub no llama a la API de Claude | Decidido |
| Intercambio con Claude | Carpeta de Google Drive con encargos y resultados en JSON | Decidido |
| Publicación | Publer como vía principal, API oficial de respaldo | Decidido |
| Plataformas | Instagram en producción; TikTok en pruebas (publicaciones privadas); sin YouTube | Decidido |

---

## 2. Versiones (verificadas el 25 oct 2026)

- **Java 25 (LTS).** Java 27 salió en septiembre de 2026, pero no es LTS ([notas de JDK 27](https://www.oracle.com/java/technologies/javase/27all-relnotes.html)).
- **Spring Boot 4.1.x.** La versión 4.1.0 salió el 10 jun 2026 ([anuncio](https://spring.io/blog/2026/06/10/spring-boot-4/)).
  - Pendiente: comprobar en las notas de la versión el soporte exacto de Java 25. Se hará al crear el proyecto, con la última 4.1.x de parche.
- **PostgreSQL, Flyway, JUnit 5, Mockito, Maven:** se fijará la última versión estable compatible con Spring Boot 4.1 (las gestiona su BOM). Se anotan en el `README` del repositorio al crearlo.
- **Node y Vite:** Node LTS vigente para compilar el frontend. Raúl no lo necesita para usar SocialHub, solo para desarrollar.

---

## 3. Arquitectura resultante

```
Navegador (React)  ⇄  SocialHub en localhost (Spring Boot, monolito modular hexagonal)
                          │
                          ├─ PostgreSQL incluido (estado operativo, auditoría)
                          ├─ Herramientas locales (promo.py, FFmpeg, ImageMagick) con operaciones validadas
                          ├─ Google Drive: archivos, entregables y carpeta de intercambio con Claude
                          └─ Adaptadores de publicación (Publer vía Claude · API Instagram · API TikTok)

Claude (tareas programadas con Claude Pro)  ⇄  Carpeta de intercambio en Drive
```

- **SocialHub es el orquestador.** Planifica, guarda estados, aplica políticas y muestra el anillo.
- **Claude es un agente externo** que recoge encargos y deja resultados. SocialHub nunca usa la sesión de Claude Pro ni sus credenciales.

### 3.1. Contrato de intercambio con Claude (Drive)

Estructura propuesta, configurable:

```
SocialHub/
  intercambio/
    encargos/      ← SocialHub escribe un JSON por encargo
    en-curso/      ← Claude mueve el encargo al empezarlo
    resultados/    ← Claude escribe el resultado
    rechazados/    ← SocialHub mueve los resultados que no validan
```

Encargo (`task-request` v1):

```json
{
  "contract": "socialhub.task-request",
  "version": 1,
  "taskId": "uuid",
  "brand": "delito",
  "promotion": "lanzamiento-poemario",
  "type": "instagram.profiles.batch",
  "goal": "texto",
  "inputs": {},
  "rules": ["nunca enviar mensajes", "versos literales"],
  "model": "sonnet",
  "deadline": "2026-10-29T08:48:00+01:00",
  "idempotencyKey": "uuid"
}
```

Resultado (`task-result` v1):

```json
{
  "contract": "socialhub.task-result",
  "version": 1,
  "taskId": "uuid",
  "status": "completed | failed | needs_human",
  "outputs": {},
  "files": [{ "driveId": "…", "role": "carousel" }],
  "sources": [{ "title": "…", "url": "…" }],
  "warnings": [],
  "error": null,
  "agent": { "runtime": "claude-scheduled-task", "model": "sonnet" },
  "startedAt": "…",
  "finishedAt": "…"
}
```

- SocialHub valida cada resultado contra el esquema antes de usarlo. Si no cumple, lo mueve a `rechazados/` y registra el motivo.
- La IA nunca cambia estados directamente.

### 3.2. Publicación

Orden de vías por publicación:

1. **Publer** (principal), programado por una tarea de Claude con Chrome. Requiere el PC encendido.
2. **API oficial** (respaldo):
   - Instagram: API de Instagram con cuenta profesional y app de Meta ([documentación](https://developers.facebook.com/docs/instagram-platform/content-publishing)).
   - TikTok: Content Posting API ([documentación](https://developers.tiktok.com/doc/content-posting-api-get-started)).
3. **Manual con LEEME**, si fallan las dos.

Reglas:
- Una publicación solo pasa a «Publicada» con confirmación de la plataforma o de la vista de Publer.
- Cada intento lleva una clave de idempotencia, para que un reintento no duplique la publicación.

### 3.3. Máquinas de estados (resumen)

- **Tareas:** inactiva → en cola → en progreso → (en espera | bloqueada | error | completada | cancelada).
  - El reintento y la reanudación vuelven a «en cola».
  - Los reintentos tienen límite, sin bucles.
- **Publicaciones:** idea → propuesta → en producción → borrador → pendiente de revisión → aprobada → programada → publicando → publicada | error de publicación | cancelada.
  - El backend valida cada transición.

### 3.4. PostgreSQL incluido

Propuesta: usar binarios oficiales de PostgreSQL empaquetados para Windows (y luego macOS). SocialHub los inicializa en su carpeta de datos, los arranca al abrirse y los para al cerrarse.

- Se valida en Windows con un prototipo antes de la Fase 2.
- Existen paquetes Java de binarios embebidos, como `io.zonky.test.postgres`, pero están pensados para pruebas. Se evaluarán solo como fuente de binarios, no como dependencia de producción.

---

## 4. Riesgos y limitaciones conocidas

| Riesgo | Impacto | Mitigación |
|---|---|---|
| La API de Claude no está incluida en Claude Pro, y el Agent SDK pide clave de API para programas propios ([doc](https://code.claude.com/docs/en/agent-sdk/overview)) | SocialHub no puede llamar a Claude directamente sin coste | Decidido: Claude trabaja desde sus tareas programadas e intercambia por Drive |
| Publer funciona por Chrome y necesita el PC encendido con la pantalla activa | Programaciones que no se hacen | API oficial de respaldo, aviso en el anillo y reintento cuando vuelva el PC |
| La API de TikTok sin auditoría solo publica en privado ([doc](https://developers.tiktok.com/doc/content-posting-api-get-started)) | Sin publicación pública en TikTok | Encaja con el plan: TikTok solo para pruebas privadas |
| La API de Instagram pide cuenta profesional, app de Meta y medios en URL pública (o subida reanudable) | Trabajo inicial de configuración | Raúl crea la app; SocialHub usa la subida reanudable o enlaces de Drive válidos |
| Límite de unas 100 publicaciones al día por API en Instagram | Ninguno con el ritmo actual | El motor de cuotas lo vigila |
| Las métricas dependen de lo que exponga cada API (Instagram Insights, TikTok) | Datos incompletos o ausentes | Las métricas son opcionales: si no hay dato se muestra «Sin dato» y el análisis no se bloquea. Alternativa: tareas de Claude |
| PostgreSQL embebido en Windows (permisos, antivirus, puertos) | Fallos de arranque | **Validado el 26 oct 2026** en el PC de Raúl con PostgreSQL 18.6 portable. Queda pendiente macOS |

---

## 5. Plan inmediato

1. Raúl aprueba este documento. Claude crea el repositorio privado desde el navegador.
2. Se crea la estructura inicial del repositorio:
   - `backend/` (Spring Boot)
   - `frontend/` (React)
   - `docs/` (este documento y la especificación)
   - `contracts/` (esquemas JSON)
3. Se prueba PostgreSQL embebido en Windows.
4. **Fase 2:** dominio, migraciones Flyway, API y conexión del anillo moderno a datos persistidos.

## 6. Pendiente de decidir más adelante

- Estructura exacta de carpetas de Drive por marca. Se toma la de las promociones como punto de partida.
- Creación de la app de Meta y de las cuentas de TikTok de prueba (fase de publicación).
- Si se incorpora OpenCode y con qué proveedor.
- Qué métricas concretas se piden a cada API, y cuáles se dejan a tareas de Claude.
- Actualizar los datos de ejemplo del prototipo para quitar Metricool.
