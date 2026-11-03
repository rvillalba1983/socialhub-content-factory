# SocialHub · Fase 8: métricas y autonomía

Estado: **bloque 1 (reintentos automáticos) hecho, pendiente de PR** (rama `fase-8/autonomia`) · 3 nov 2026

Decisiones de Raúl en `docs/fases-7-9.md`.

## Bloque 1: reintentos automáticos

- Un encargo a Claude que falla se reintenta solo, sin pulsar «Reintentar», hasta el tercer intento. El tercero sube un nivel de modelo, igual que el reintento a mano.
- SocialHub espera **30 minutos** desde el fallo antes de reintentar, para no gastar los intentos seguidos por un problema pasajero (decisión técnica, a confirmar por Raúl).
- No se reintentan solos:
  - Los encargos de publicación con Chrome en el PC: el motor de publicación ya sigue por otra vía.
  - Los encargos en los que Claude pide ayuda: no son un fallo.
- Queda en el historial de la tarea: «Reintentado por SocialHub sin esperar a Raúl».
- Código: `AiAutoRetry` (cada 5 min) y `Task.autoRetry`. Prueba: `AiAutoRetryTest`.

## Pendiente de preguntar a Raúl

1. **Pedir textos y revisión de calidad solo:** ¿qué publicaciones? Las «Aprobadas» ya han pasado el control de calidad, que exige texto. Lo más probable es que se refiera a las que están en producción (propuestas ya aprobadas) sin texto, o sin revisión de Claude antes de pasar a «Aprobada».
2. **Métricas de Metricool:** SocialHub no puede usar el conector de Metricool. Lo haría un encargo nuevo para la tarea de la nube, con su conector. Falta saber cada cuánto y qué métricas guardar. Publora tiene `post-statistics`, que se puede leer por API.
3. **Resumen diario:** ¿en qué tarea programada y a qué hora? Hay que cambiar las instrucciones de una tarea programada, y eso necesita el OK de Raúl.
