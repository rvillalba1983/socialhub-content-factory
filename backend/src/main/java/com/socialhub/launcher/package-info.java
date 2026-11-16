/**
 * SocialHub como programa de escritorio: se abre solo al iniciar sesión en Windows (en segundo plano, sin abrir el navegador),
 * con un icono en la bandeja del sistema (junto al reloj) para abrirlo, ver los registros o cerrarlo.
 *
 * <ul>
 *   <li>Raíz: puerto primario ({@code Launcher}).</li>
 *   <li>{@code domain}: el menú de la bandeja y los puertos de salida (entrada de arranque, preferencia, sistema).</li>
 *   <li>{@code application}: implementación del puerto primario.</li>
 *   <li>{@code adapter.in}: web, bandeja y arranque. {@code adapter.out}: registro de Windows, archivo y sistema operativo.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"platform", "platform::desktop", "platform::diagnostics", "api"})
@org.jspecify.annotations.NullMarked
package com.socialhub.launcher;
