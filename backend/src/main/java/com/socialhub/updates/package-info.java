/**
 * Aviso de versión nueva: SocialHub mira (como mucho una vez al día y al abrir) la última versión publicada en GitHub
 * Releases y la compara con la instalada. No envía datos del usuario, no instala nada y no falla sin red.
 *
 * <ul>
 *   <li>Raíz: puerto primario ({@code Updates}).</li>
 *   <li>{@code domain}: versiones, releases y puertos de salida (origen de releases, caché).</li>
 *   <li>{@code application}: implementación del puerto primario.</li>
 *   <li>{@code adapter.in}: web. {@code adapter.out}: GitHub y el archivo de caché.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"platform", "platform::observability", "api"})
@org.jspecify.annotations.NullMarked
package com.socialhub.updates;
