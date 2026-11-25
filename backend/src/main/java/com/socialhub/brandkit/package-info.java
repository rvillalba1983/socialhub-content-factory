/**
 * Kit de marca: la paleta y las plantillas SVG (cita, cierre del carrusel, historia) que Claude diseña una vez por marca
 * y que Raúl aprueba. Guarda cada versión del kit (pendiente, aprobada, sustituida o rechazada), la valida y
 * deja una sola aprobada por marca.
 *
 * <ul>
 *   <li>Raíz: puerto primario ({@code BrandKits}).</li>
 *   <li>{@code domain}: el kit, sus plantillas, el validador y los puertos de salida (repositorio y revisor de plantillas).</li>
 *   <li>{@code application}: casos de uso (el JSON sigue {@code contracts/brand-kit.v1.schema.json}).</li>
 *   <li>Implementa el puerto {@code ApprovedTemplates} del módulo {@code pieces}: le da las plantillas del kit aprobado.</li>
 *   <li>{@code adapter.out}: persistencia y lectura del JSON; la revisión de plantillas usa el motor del módulo {@code pieces} desde la aplicación.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"pieces", "ai", "ai::domain", "production", "promotion", "platform", "api"})
@org.jspecify.annotations.NullMarked
package com.socialhub.brandkit;
