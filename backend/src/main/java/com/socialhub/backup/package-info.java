/**
 * Copias de seguridad de la base de datos real: una al día al abrir SocialHub, a mano desde la pantalla «Copias»,
 * y restauración (con copia previa y parada de SocialHub después).
 *
 * <ul>
 *   <li>Raíz: puerto primario ({@code Backups}).</li>
 *   <li>{@code domain}: copias, nombres, rotación y puertos de salida (almacén, archivo de la base de datos, ajustes).</li>
 *   <li>{@code application}: implementación del puerto primario.</li>
 *   <li>{@code adapter.in}: web y reloj. {@code adapter.out}: ficheros y PostgreSQL.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"armory", "platform", "platform::postgres", "api"})
@org.jspecify.annotations.NullMarked
package com.socialhub.backup;
