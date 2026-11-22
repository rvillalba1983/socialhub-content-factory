/**
 * Piezas de Instagram a partir de un spec.json (lo que hacía promo.py, ahora en Java): carrusel e historia con
 * Java 2D; reel, clip y comprimir con FFmpeg. Solo lee y escribe en las carpetas de la promoción y nunca
 * sobrescribe. Aporta al motor la tarea {@code GENERATE_PIECES}.
 *
 * También incluye el motor de plantillas SVG (puerto {@code TemplateRenderer}): dibujo con Batik y texto ajustado con
 * Java 2D y las fuentes incluidas.
 *
 * <ul>
 *   <li>Raíz: puertos primarios ({@code PieceOrders}, {@code ContentGaps}).</li>
 *   <li>{@code domain}: el spec, el acceso seguro a los archivos y los puertos de salida (repositorio y lector de
 *       specs).</li>
 *   <li>{@code application}: casos de uso, el generador de piezas (Java 2D y FFmpeg), la tarea {@code GENERATE_PIECES} y
 *       el manejador del encargo a Claude.</li>
 *   <li>{@code adapter.in}: web y carpeta vigilada. {@code adapter.out}: persistencia y lectura del spec.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"promotion", "publishing", "production", "tools", "ai", "ai::domain", "platform", "api"})
@org.jspecify.annotations.NullMarked
package com.socialhub.pieces;
