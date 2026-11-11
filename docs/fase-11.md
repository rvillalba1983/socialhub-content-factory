# Fase 11 · Arreglos de la primera semana real

Estado: **Plan pendiente del OK de Raúl** (11 nov 2026).

## Qué se ha visto

Raúl no ha podido contar todavía qué le pasó con «Test inicial» y la 0.9.2. Por eso, el 11 nov Claude probó en la nube, en modo real, la primera semana de dos marcas (Litoques y El DeLito). Una carpeta local hizo de Google Drive y Claude escribió a mano los resultados en `de-claude`.

**Funciona bien**
- La franja «Qué hacer ahora» recorre todos los estados en orden: retenidas → crear carpetas → «pedidas, llegarán sobre las 14:40» → «Tienes N propuestas» → revisadas. Cada fila desaparece al resolverla, sin recargar.
- El aviso «No ha llegado el resultado de los encargos de las 9:40» sale cuando toca y desaparece al llegar el resultado.
- Funcionan «No uso Publora» (y volver a activarla), la carpeta de copias y el alta de marca con carpeta base, con el paso siguiente.
- Los encargos cumplen `task-request.v1`. Los rechazos, con su nota, pasan a la memoria editorial.
- Dos piezas de la misma red el mismo día entran bien (`…-instagram-2`).
- Los resultados que no cumplen el contrato, o que vienen fallidos, salen en «Necesita atención» con el motivo, y se reintentan solos.
- Al reiniciar la app no se repiten encargos y la franja vuelve igual.

**Fallos**

| # | Qué pasa | Gravedad |
|---|---|---|
| 1 | Una promoción nueva se guarda «Planificada» por defecto. Solo las «Activas» reciben propuestas y nada las activa solas, ni el día que empiezan. Al guardar solo dice «Promoción guardada». | Alta: sin propuestas y sin saber por qué |
| 2 | «Crea las carpetas de…» y «Conecta Publora para…» abren la ficha de la marca, pero la tarjeta queda fuera de la pantalla: la página baja antes de que carguen la identidad y la memoria editorial. Las pruebas no lo ven porque simulan el desplazamiento. | Media |
| 3 | Se aceptan propuestas con fecha pasada. Una del día anterior entró, se aprobó y SocialHub pidió a Claude su texto (encargo gastado). | Media |
| 4 | Una sola propuesta fuera de la semana descarta todo el lote, incluidas las buenas. | Media |
| 5 | Si las propuestas no salen porque Drive no está disponible o porque se ha gastado el límite diario de encargos, la franja no dice nada. Lo de Drive solo sale en «Necesita atención» y lo del límite, solo en el historial de la tarea. | Media |
| 6 | Tras activar una promoción, la franja tarda hasta 10 min en decir que las propuestas están pedidas (ronda de `AiAutoOrders`). | Baja |

Vistos y dejados fuera por Raúl: la hora sin el día en «Próximas publicaciones», que «Necesita atención» solo enseñe 2 avisos y que el registro no tenga una línea por resultado inválido.

## Qué se va a hacer (decidido por Raúl el 11 nov)

### 1. Las promociones planificadas se activan solas
- Una promoción «Planificada» pasa sola a «Activa» el día en que empieza (`starts_on` ≤ hoy, hora de España), en la ronda de encargos automáticos. Queda anotado en su historial.
- Sus propuestas de la primera semana se piden igual que las de una activa: el lunes anterior a las 18:00 o, si esa hora ya pasó, en el acto. Las promociones «En pausa» y «Terminadas» siguen sin recibir nada.
- Al guardar una promoción, el mensaje dice qué pasará: «Propuestas pedidas: llegarán sobre las HH:MM», «Sus propuestas se pedirán el lunes D a las 18:00» o qué las retiene.
- La recomendación «Activa una promoción de {marca}» deja de salir para las planificadas que empiezan en el futuro, porque se activarán solas. Sigue saliendo si la marca solo tiene promociones en pausa o terminadas.

### 2. Los botones de la franja llevan a la tarjeta
Al abrir la ficha de la marca desde «Crea las carpetas» o «Conecta Publora», la página baja a la tarjeta cuando todo su contenido ha cargado, y no antes. Se comprueba con Playwright en un navegador real, no con la prueba simulada.

### 3. Propuestas no válidas: se quitan solo las malas
Al recibir las propuestas de Claude:
- Se descartan las no válidas:
  - fecha u hora ilegibles;
  - red distinta de Instagram o TikTok;
  - fuera de la semana pedida;
  - fuera de las fechas de la promoción;
  - **fecha y hora a menos de 6 horas de ahora**. Es el margen mínimo para programar que ya usa el proyecto: una propuesta para dentro de 2 horas tampoco se podría preparar. *Pendiente del OK de Raúl.*
- Las buenas entran.
- El resumen de la tarea dice cuántas se han descartado y por qué: «4 propuestas para revisar · 2 descartadas: 1 con fecha pasada, 1 fuera de la semana».
- Si no queda ninguna buena, el encargo falla como ahora y se reintenta solo.

### 4. La franja avisa también si las propuestas están paradas
En la misma fila «Las propuestas de {promoción} no pueden salir», con su motivo:
- **Drive no disponible:** «La carpeta de Encargos no está disponible en este ordenador. ¿Está abierto Google Drive?». Sin botón.
- **Límite diario alcanzado:** «Límite diario de encargos a Claude alcanzado (N): salen mañana». Lleva un botón a la pantalla «Claude», donde se sube el límite. Hace falta un destino nuevo, `CLAUDE`, en `RecommendationTarget.screen`.

### 5. Propuestas pedidas al momento
Al guardar una promoción que ya toca (nueva, activada o con fechas cambiadas), sus propuestas de la primera semana se piden en ese momento, sin esperar a la ronda de 10 minutos. Salen a Drive en el siguiente envío (30 s) y la franja dice «pedidas» enseguida.

## Fuera de esta fase
- Los detalles menores que Raúl no ha elegido (ver arriba).
- Las tareas programadas de Claude no se tocan.
- Ninguna librería nueva.

## Cómo se hace

Un solo bloque: rama `fase-11-arreglos` y una PR. Commit y push tras cada paso.

1. **Backend y contrato** (`programador`):
   - activación sola de las planificadas y propuestas de su primera semana (puntos 1 y 5);
   - validación propuesta a propuesta (punto 3);
   - motivos de Drive y del límite en la fila de retenidas y destino `CLAUDE` (punto 4);
   - mensaje de qué pasará al guardar.
   Con pruebas de cada caso, también el de las 20:40 → 9:40 del día siguiente.
2. **Frontal** (`programador`): desplazamiento a la tarjeta tras la carga (punto 2), destino «Claude» en la franja y mensajes al guardar, con pruebas.
3. **Comprobación funcional** (`probador-visual`): repetir el recorrido de hoy en la nube, con Playwright, en estos casos:
   - promoción planificada que empieza hoy y otra que empieza el lunes;
   - botones de carpetas y Publora;
   - resultado con propuestas buenas y malas mezcladas;
   - Drive oculto;
   - límite agotado.
4. **Revisión** (`revisor`) y **CI** en GitHub (`vigilante-ci`).
5. Tras el merge de Raúl: decisiones, manual, estado e instalador `.msi` 0.9.3.

## Cómo sabremos que está bien
- Una promoción nueva que se deja «Planificada» recibe sus propuestas sin tocar nada más, y el día que empieza aparece como «Activa».
- Al guardarla, el mensaje dice cuándo llegarán sus propuestas, y la franja lo dice en menos de un minuto.
- Los botones de la franja enseñan la tarjeta de carpetas o de Publora en pantalla.
- Un lote con una propuesta de ayer y tres buenas deja tres para revisar y lo explica.
- Con Drive cerrado o el límite agotado, la franja dice por qué no salen las propuestas.

## Pendiente
- Leer en el PC de Raúl `logs\socialhub.log`, `logs\pg_ctl.log` y el estado de «Test inicial», para confirmar qué le pasó de verdad. Raúl lo ha autorizado (solo lectura), pero el 11 nov su PC no respondía.
