# SocialHub · Fases 7, 8 y 9: respuestas de Raúl

Estado: **preguntas respondidas** · 3 nov 2026. No hay código todavía.

## Fase 7 · Publicación

| # | Tema | Decisión |
|---|---|---|
| 1 | Vías, por orden | 1) Publer; si falla, 2) Publora; si falla, 3) API oficial de Instagram o TikTok. El calendario tiene en cuenta lo que haya programado o publicado en las tres |
| 2 | Cómo se programa en Publer | La API de Publer es de pago (planes Business y Enterprise), así que SocialHub no la usa. SocialHub deja un encargo «Programar en Publer» y una tarea programada de Claude con Chrome en el PC de Raúl lo programa en la web de Publer, como las tareas de ahora. Necesita el PC encendido |
| 3 | Cuándo se programa | Solo cuando Raúl pasa la publicación a «Programada»: ya aprobada, con el control de calidad pasado y 6 h de margen |
| 4 | Confirmar «Publicada» | Claude lo comprueba después de la hora en Publer, Publora o la red y deja el enlace o el identificador. Si no la encuentra, queda en «Error de publicación» |
| 5 | Si fallan las tres vías | Se reintenta más tarde mientras quede el margen de 6 h. Después pasa a manual con LEEME y aviso |

Datos comprobados:
- **Publer:** API solo en Business y Enterprise ([Publer · planes](https://publer.com/help/en/article/1sge3f7/)).
- **Publora gratis:** 15 publicaciones al mes, 3 cuentas, API REST y vídeos de hasta 50 MB ([FreeTier · Publora](https://freetier.co/directory/products/publora)). Con la cadencia actual (unas 20 al mes) no basta para todo, pero sirve de reserva.
- **API oficiales:** Instagram necesita una cuenta profesional y una app de Meta; TikTok, revisar la app. Las credenciales las pone Raúl y nunca se suben al repositorio.

## Fase 8 · Métricas y autonomía

| # | Tema | Decisión |
|---|---|---|
| 1 | Métricas | De Metricool (con su conector) y, si se puede, de Publora o la API oficial. Lo que no dé el plan gratuito se queda sin dato, sin inventarlo |
| 2 | Qué hace SocialHub solo | Reintentar encargos fallidos hasta el tercer intento, con subida de modelo, sin pulsar «Reintentar». Pedir textos y revisión de calidad para las publicaciones aprobadas que no los tengan. Todo lo demás lo decide Raúl |
| 3 | Avisos | En «Necesita atención» al momento y un resumen diario en la notificación de la tarea programada de Claude |

## Fase 9 · Distribución

| # | Tema | Decisión |
|---|---|---|
| 1 | Instalador | Windows y macOS (jpackage, gratis), con Java y PostgreSQL dentro |
| 2 | Copias de seguridad | Diaria en una carpeta de Drive con el papel nuevo «Copias», guardando las 14 últimas. Se restaura desde la app |
| 3 | Arranque | No arranca solo: Raúl lo abre con el acceso directo |
