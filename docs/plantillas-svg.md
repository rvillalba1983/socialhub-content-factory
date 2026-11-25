# Plantillas SVG de SocialHub

Formato de las plantillas que Claude diseña una vez por marca (encargo `socialhub.brand-kit`, bloque 2 de la fase 13) y que SocialHub rellena y dibuja en cada publicación. Este documento es el contrato del motor del bloque 1. Las decisiones están en `docs/decisiones.md` (fase 13).

## Idea

Una plantilla es un SVG con el **dibujo fijo** (fondos, formas, líneas, degradados) y unos **campos de texto con nombre**. En cada publicación Claude solo manda los textos (`campo → texto`) y SocialHub:

1. pinta con Batik el dibujo de la plantilla **sin los textos**;
2. coloca cada texto dentro de su caja con Java 2D y las fuentes incluidas, partiendo en líneas y bajando el tamaño si hace falta.

Los textos nunca pasan por el SVG. Por eso pueden llevar `&`, direcciones web, comillas o cualquier carácter, y por eso los textos van **siempre encima del dibujo**.

## Ejemplo mínimo

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1080 1350">
  <rect width="1080" height="1350" fill="#1F1A17"/>
  <rect x="60" y="60" width="960" height="1230" fill="none" stroke="#C9A25B" stroke-width="4"/>

  <rect id="caja-cita" x="120" y="260" width="840" height="620" fill="none"/>
  <text id="campo-cita" font-family="Crimson Text" font-style="italic" font-size="84"
        data-tamano-min="44" data-interlineado="1.25" data-vertical="centro"
        text-anchor="middle" fill="#F4EBDD">Aquí va la cita de ejemplo</text>

  <rect id="caja-autor" x="120" y="1000" width="840" height="80" fill="none"/>
  <text id="campo-autor" font-family="Fira Sans" font-weight="500" font-size="40"
        letter-spacing="4" text-anchor="middle" fill="#C9A25B">AUTOR</text>
</svg>
```

El texto que va dentro de cada `<text>` es el **ejemplo**: se usa en la vista previa de la ficha de la marca. En una publicación real, un campo al que no se le manda texto queda vacío (nunca se cuela el ejemplo).

## Reglas

**El SVG**
- Raíz `<svg xmlns="http://www.w3.org/2000/svg">` con `viewBox="0 0 ANCHO ALTO"` (obligatorio, de 100 a 4000 en cada lado). La plantilla se puede pintar a cualquier tamaño con **la misma proporción** que su `viewBox` (por ejemplo 1080×1350 para 4:5, 1080×1920 para 9:16). Con otra proporción (más del 1 % de diferencia) no se pinta. Con una diferencia menor, el dibujo se ajusta como `preserveAspectRatio="xMidYMid slice"` (se centra y recorta lo que sobre) y el texto sigue la misma transformación.
- El dibujo usa solo lo que ya permite el validador de `fondo_svg`: `g`, `defs`, `rect`, `circle`, `ellipse`, `path`, `polygon`, `polyline`, `line`, `linearGradient`, `radialGradient`, `stop`, `title`, `desc` y sus atributos de dibujo. Nada de `script`, `style`, `image`, `use`, `foreignObject`, animaciones ni referencias externas. Máximo 64 KB.
- **No puede haber `<text>` en el dibujo** salvo los campos (ver abajo). Un texto fijo (la marca, una dirección) es un campo con `data-fijo="true"`.

**Cada campo**
- `<text id="campo-NOMBRE">` hijo directo de `<svg>` (no dentro de `<g>`), con solo texto dentro (sin `<tspan>`). `NOMBRE`: minúsculas, cifras y `_`, empieza por letra, hasta 32 caracteres.
- Su caja: `<rect id="caja-NOMBRE" x y width height>` también hijo directo de `<svg>`, con números sin unidades. La caja no se dibuja (se quita); sirve solo para colocar el texto. Si quieres un recuadro visible, añade otro `<rect>` aparte.
- Cada campo tiene su caja y cada caja su campo. Los nombres no se repiten. Como máximo **40 campos** por plantilla.
- La caja tiene que quedar **dentro del lienzo** (`viewBox`, con 1 unidad de tolerancia) y ser lo bastante grande para el tamaño mínimo: alto al menos `data-interlineado × data-tamano-min` y ancho al menos `data-tamano-min`.
- El origen del `viewBox` (los dos primeros números) va de -4000 a 4000.
- La `x` e `y` del propio `<text>` se ignoran: manda la caja.

**Atributos del campo**

| Atributo | Obligatorio | Valores | Por defecto |
|---|---|---|---|
| `font-family` | sí | `Crimson Text`, `Fira Sans`, `Bebas Neue` | — |
| `font-size` | sí | tamaño **máximo** en unidades del `viewBox` (8 a 400) | — |
| `font-weight` | no | `normal`, `bold` o 400/500/600/700 (se usa el peso más cercano que exista) | `normal` |
| `font-style` | no | `normal`, `italic` | `normal` |
| `fill` | no | `#RGB` o `#RRGGBB` | `#000000` |
| `fill-opacity` | no | 0 a 1 | `1` |
| `letter-spacing` | no | número, de -5 a 40 | `0` |
| `text-anchor` | no | `start`, `middle`, `end` (alineación a la izquierda, centro o derecha de la caja) | `start` |
| `data-tamano-min` | no | tamaño mínimo (8 hasta el máximo) | la mitad del máximo, mínimo 12 |
| `data-lineas-max` | no | 1 a 30 (entero) | sin límite propio (las que quepan en el alto de la caja) |
| `data-interlineado` | no | 0,8 a 2,5 (veces el tamaño) | `1.2` |
| `data-vertical` | no | `arriba`, `centro`, `abajo` (dentro de la caja) | `arriba` |
| `data-fijo` | no | `true`: siempre se escribe el texto de la plantilla y se ignora el que mande Claude | `false` |

**Fuentes disponibles** (las únicas permitidas; otra fuente invalida la plantilla)

| Familia | Pesos y estilos |
|---|---|
| `Crimson Text` | 400, 400 cursiva, 600, 700 |
| `Fira Sans` | 400, 400 cursiva, 500, 700 |
| `Bebas Neue` | 400 (todo en mayúsculas) |

## Zona de la ilustración (plantillas del kit de marca)

Las plantillas del kit van **encima** de la ilustración de la publicación (decisión 53). Cada plantilla del kit declara en `image`
(fuera del SVG, en el JSON del kit) un rectángulo `{x, y, width, height}` en unidades de su `viewBox` con el sitio de la ilustración,
y deja esa zona libre o semitransparente.

- **Cierre del carrusel e historia:** `image` es **obligatorio** (un objeto). Sin él, el kit se rechaza.
- **Cita** (verso sobre fondo): `image` puede ser `null`.
- **Bloque 3 (decisión 60):** con kit aprobado, la ilustración va en la zona `image` y la plantilla se pinta encima con fondo transparente (`renderOver`); el kit debe dejar libre esa zona. Sin kit aprobado la plantilla se ignora.

## Cómo se ajusta un texto

1. Se separa en párrafos por los saltos de línea (`\n`); cada uno se parte por palabras. Los saltos de línea de los versos se respetan y las líneas en blanco cuentan.
2. Se prueba desde el tamaño máximo bajando de 1 en 1 hasta el mínimo. Vale el primer tamaño en el que todas las líneas caben en el ancho de la caja, no pasan de `data-lineas-max` y el bloque cabe en el alto.
3. Si ni el mínimo cabe, se parten las palabras demasiado largas (**con aviso**: «una palabra no cabía y se ha partido por letras»), se recorta el final con «…» y **se devuelve un aviso**. Nunca se recorta sin avisar.
4. El texto de un campo tiene un tope de **2.000 caracteres**: lo que pase se corta, con aviso.
5. Los caracteres que la fuente no tiene (emoji, por ejemplo) se quitan y se avisa.

## Avisos y errores

Los mensajes están en español porque los lee Raúl en la ficha de la marca.

- **Error** (la plantilla no sirve y no se pinta): XML no válido, falta `viewBox`, más de 40 campos, caja fuera del lienzo o demasiado pequeña, números no finitos, dibujo con elementos no permitidos, texto fuera de un campo, campo sin caja o caja sin campo, nombres repetidos, fuente no incluida, atributo con valor no válido, proporción distinta de la pedida.
- **Aviso** (se pinta igual): texto recortado, caracteres sin glifo, palabra partida por letras, texto cortado a 2.000 caracteres, y un texto de Claude para un campo que no existe en la plantilla (se citan como mucho 10 claves y se dice cuántas más hay).
