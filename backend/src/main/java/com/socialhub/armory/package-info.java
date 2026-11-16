/**
 * Armería: agentes, herramientas locales, servicios y dispositivos que usa la fábrica. Solo lectura por ahora.
 *
 * <ul>
 *   <li>Raíz: puertos primarios ({@code ArmoryQueries}, {@code ArmoryRegistry}).</li>
 *   <li>{@code domain}: el elemento de la armería y el puerto de salida {@code ArmoryRepository}.</li>
 *   <li>{@code application}: implementación de los puertos primarios.</li>
 *   <li>{@code adapter.in.web}: controlador y vista (interfaz con nombre {@code web}). {@code adapter.out}: persistencia.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"platform", "platform::live", "api"})
@org.jspecify.annotations.NullMarked
package com.socialhub.armory;
