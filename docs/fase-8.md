# SocialHub · Fase 8: métricas y autonomía

Estado: **bloque 1 (reintentos) en PR #18; bloque 2 (textos y revisión solos) hecho** (rama `fase-8/textos-solos`) · 3 nov 2026

Decisiones de Raúl en `docs/fases-7-9.md`.

## Bloque 1: reintentos automáticos

- Un encargo a Claude que falla se reintenta solo, sin pulsar «Reintentar», hasta el tercer intento. El tercero sube un nivel de modelo, igual que el reintento a mano.
- SocialHub espera **30 minutos** desde el fallo antes de reintentar, para no gastar los intentos seguidos por un problema pasajero (decisión técnica, a confirmar por Raúl).
- No se reintentan solos:
  - Los encargos de publicación con Chrome en el PC: el motor de publicación ya sigue por otra vía.
  - Los encargos en los que Claude pide ayuda: no son un fallo.
- Queda en el historial de la tarea: «Reintentado por SocialHub sin esperar a Raúl».
- Código: `AiAutoRetry` (cada 5 min) y `Task.autoRetry`. Prueba: `AiAutoRetryTest`.

## Respuestas de Raúl (3 nov, de madrugada)

1. **Textos solos:** para las publicaciones en producción que no tienen texto.
2. **Revisión sola:** al pasar a «Pendiente de revisión», si Claude aún no ha revisado ese texto.
3. **Métricas:** sin Metricool, por scripts (API de Publora) o con el navegador (Claude con Chrome). Cada publicación se mide una vez, a los 7 días.
4. **Resumen diario:** solo en la app, sin notificación.

## Bloque 2: textos y revisión solos

- **Textos:** cada 5 minutos, las publicaciones «En producción» o «Borrador» sin texto (salvo historias) reciben un encargo de textos, uno por promoción y como mucho 10 publicaciones por encargo.
- **Revisión:** las que están en «Pendiente de revisión» con texto (o historias) reciben un encargo de revisión de calidad.
  - Solo una vez por versión del texto: si Raúl cambia el texto y la vuelve a pasar, se revisa de nuevo.
  - La primera vez también se revisan las que ya estaban en «Pendiente de revisión».
- Cada encargo se apunta (V19, `auto_ai_request`) para no repetirlo. Cuentan en el límite diario.
- Código: `AutoRequests`. Prueba: `AutoRequestsTest`.
