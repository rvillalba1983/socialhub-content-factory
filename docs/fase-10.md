# Fase 10 · Primera semana real: guía y propuestas iniciales

Estado: **plan pendiente del OK de Raúl**.

## Qué se ha visto

Con «Test inicial», a Raúl le han faltado recomendaciones de qué hacer y mensajes sobre las propuestas iniciales. En el código:

- Al guardar una marca o una promoción, la app solo dice «guardada», sin paso siguiente.
- Las primeras propuestas se piden solas, pero nada lo dice. Tampoco se dice cuándo llegarán ni cuándo han llegado.
- Si la marca no tiene carpeta de «Encargos», el encargo queda retenido. El aviso sale en «Necesita atención», que solo enseña los 2 primeros.

## Qué se va a hacer

### 1. Bloque «Qué hacer ahora»

En la pantalla principal, encima de «Necesita atención». Muestra de 1 a 3 recomendaciones, las más importantes, cada una con un botón a la pantalla donde se resuelve. Cada recomendación desaparece sola cuando deja de hacer falta. No hay casillas ni lista fija. Se calcula en cada consulta, sin guardarla.

Por orden de prioridad:

| # | Cuándo sale | Texto (orientativo) | Botón |
|---|---|---|---|
| 1 | Las propuestas de una promoción están retenidas | «Las propuestas de {promoción} no pueden salir: {motivo}» | Carpetas de Drive de la marca |
| 2 | Hay propuestas listas para revisar | «Tienes {n} propuestas de {promoción} para revisar» | Propuestas |
| 3 | La marca no tiene carpeta base ni todas sus carpetas | «Crea las carpetas de {marca}» | Carpetas de Drive |
| 4 | Un encargo salió antes de una recogida ya pasada y sigue sin recoger (margen de 30 min) | «Claude no ha recogido los encargos de las {hora}. Revisa la tarea programada» | Ninguno, solo el texto |
| 5 | La marca no tiene cuenta de Publora | «Conecta Publora para {marca}» | Tarjeta de Publora |
| 6 | No hay carpeta de copias | «Elige dónde guardar las copias» | Copias |
| 7 | Una marca no tiene ninguna promoción activa | «Da de alta la primera promoción de {marca}» | Alta de promoción |
| 8 | Propuestas pedidas y en camino | «Propuestas de {promoción} pedidas. Llegarán sobre las {próxima recogida}» | Ninguno, solo el texto |

Si no hay nada que recomendar, el bloque no se muestra. Solo funciona en modo real, igual que los avisos de encargos retenidos.

Para no repetir, las propuestas retenidas (fila 1) salen solo en este bloque. Los demás encargos retenidos siguen en «Necesita atención».

### 2. Mensajes de propuestas iniciales

- Al guardar una promoción, el mensaje dice qué pasa después: «Promoción guardada. Propuestas pedidas: llegarán sobre las 9:40», o qué las retiene.
- Al guardar una marca, el mensaje dice cuál es el siguiente paso.
- Cuando lleguen las propuestas, sale la fila 2 del bloque, con el contador.
- La hora de llegada es la siguiente recogida de «Encargos de SocialHub»: 9:40, 14:40 y 20:40, hora de España. Son horas fijas en una constante del backend. **Las tareas programadas no se tocan.**

## Fuera de esta fase

- Lista de puesta en marcha con casillas (decidido el 8 nov).
- Ideas de plantilla sin Claude y recogidas más frecuentes (descartadas por Raúl el 9 nov).
- Avisos fuera de la app (correo o móvil).

## Cómo se hace

Un solo bloque: rama `fase-10-guia-propuestas` y una PR.

1. **Contrato y backend** (`programador`): lista `recommendations` en `/api/factory`, con pruebas de cada fila y de la prioridad.
2. **Frontal** (`programador`): bloque «Qué hacer ahora» y mensajes al guardar, con pruebas.
3. **Comprobación visual** (`probador-visual`): marca sin carpetas, propuestas retenidas, en camino y listas.
4. **Revisión** (`revisor`) y CI en GitHub (`vigilante-ci`), con commit y push tras cada paso.
5. Tras el merge de Raúl: manual, estado e instalador `.msi` 0.9.2.

No hace falta ninguna librería nueva.

## Cómo sabremos que está bien

- Marca nueva sin carpetas: se recomienda crearlas y el botón lleva a la pantalla correcta.
- Al crearlas, esa recomendación desaparece sin recargar y las propuestas pasan a «en camino», con la hora bien calculada (también la de después de las 20:40, que es a las 9:40 del día siguiente).
- Al llegar las propuestas, sale el contador. Al revisarlas, desaparece.
- En PC de Raúl, con el `.msi` nuevo y Litoques: la prueba real, que queda para él.
