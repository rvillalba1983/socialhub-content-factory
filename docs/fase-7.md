# SocialHub · Fase 7: publicación

Estado: **bloque 1 en marcha** (rama `fase-7/publicacion`) · 3 nov 2026

Decisiones generales en `docs/fases-7-9.md`. Respuestas de Raúl al empezar esta fase:

| # | Tema | Decisión |
|---|---|---|
| 1 | Encargos que necesitan Chrome en el PC (programar en Publer, comprobar en Publer) | Van a `Encargos/para-claude-pc` y los recoge una tarea programada nueva en el PC de Raúl. Los resultados vuelven a `de-claude`. La tarea de la nube no los ve |
| 2 | Clave de API de Publora | Raúl la pega en la app. Se guarda en un archivo de la carpeta de datos de SocialHub en su PC, legible solo por su usuario. No va a la base de datos, ni a las copias de Drive, ni al repositorio |

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
