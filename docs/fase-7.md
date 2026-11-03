# SocialHub · Fase 7: publicación

Estado: **bloque 1 hecho, pendiente de PR** (rama `fase-7/publicacion`) · 3 nov 2026

Decisiones generales en `docs/fases-7-9.md`. Respuestas de Raúl al empezar esta fase:

| # | Tema | Decisión |
|---|---|---|
| 1 | Encargos que necesitan Chrome en el PC (programar en Publer, comprobar en Publer) | Van a `Encargos/para-claude-pc` y los recoge una tarea programada nueva en el PC de Raúl. Los resultados vuelven a `de-claude`. La tarea de la nube no los ve |
| 2 | Clave de API de Publora | Raúl la pega en la app. Se guarda en un archivo de la carpeta de datos de SocialHub en su PC, legible solo por su usuario. No va a la base de datos, ni a las copias de Drive, ni al repositorio |
| 3 | Límite diario | Los encargos de publicación no cuentan: tienen hora fija |

## Bloques (una rama y una PR por bloque)

1. **Motor de publicación y vía Publer:**
   - Al pasar una publicación a «Programada», SocialHub crea su tarea de publicación (estación «Programación»).
   - Prueba las vías en orden: Publer (encargo para Claude con Chrome), Publora y API oficial. Las dos últimas, mientras no estén configuradas, figuran como «no configurada».
   - Si fallan todas, reintenta cada hora mientras quede el margen de 6 h. Después prepara un LEEME para publicar a mano y avisa.
   - Pasada la hora, encarga la comprobación. «Publicada» llega con el enlace o el identificador; si no aparece, queda en «Error de publicación».
   - La tarea programada del PC se crea solo con el OK de Raúl.
2. **Vía Publora:** API REST con la clave de Raúl. Programa, consulta el estado y lee lo programado para el calendario.
3. **Vía API oficial:** Instagram Graph API y TikTok Content Posting API, con las credenciales que ponga Raúl.
4. **Calendario con las tres vías:** lo programado o publicado fuera de SocialHub en Publer, Publora o la red aparece en el calendario.

## Bloque 1: cómo funciona

```
Raúl pasa a «Programada»
  └─ Publer: encargo «Programar en Publer» en para-claude-pc (deadline: 8 h antes)
       ├─ Claude la programa → «Programada en Publer»
       ├─ Claude falla o no llega a 7 h antes → Publora → API oficial (hasta 6 h antes)
       └─ fallan todas → reintento cada hora, o LEEME para publicar a mano + aviso
A la hora → «Publicando»; 15 min después (2 h si es a mano) → encargo «Comprobar publicación»
  ├─ la ve publicada, con enlace o identificador → «Publicada»
  └─ no la ve, o no llega en 24 h → «Error de publicación» + aviso
```

- **Datos:** tabla `publish_job` (V16): estado, vía, encargo en curso, encargos abandonados, identificador, enlace, próximo intento, LEEME e historial.
- **Código:**
  - `PublishRoute` es el punto de extensión de cada vía. Publora y la API oficial figuran como «no configurada» hasta los bloques 2 y 3.
  - `PublishingEngine` revisa cada minuto las publicaciones en marcha.
  - `PublerScheduleHandler` y `PublishCheckHandler` aplican los resultados de Claude.
  - `AiTypeHandler.onFailed` avisa al motor cuando Claude falla, pide ayuda o devuelve algo que no cumple el contrato.
- **API:** `GET /api/publications/{id}/publish-job`, `DELETE /api/publications/{id}/flag` y avisos de tipo `PUBLICATION` en `/api/factory`.
- **Pantalla:** en la publicación, el panel «Publicación» (vía, ronda, próximo intento, enlace, LEEME e historial) y la incidencia con el botón «Resuelto». Los avisos de publicación abren la publicación.
- **Pruebas:** `PublishingEngineTest`, con un reloj que se adelanta: programar y comprobar, hora equivocada, reintento, sin tiempo (LEEME), resultado tardío, comprobación sin prueba y cancelación.

### Pendiente del bloque 1

- **Tarea programada en el PC** («Publicar con Chrome»): recoge `para-claude-pc` con Chrome. Las instrucciones propuestas están en `docs/tarea-programada-encargos.md`. Se crea solo con el OK de Raúl.
