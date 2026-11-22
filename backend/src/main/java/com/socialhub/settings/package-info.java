/**
 * Ajustes generales de SocialHub que leen varios módulos. Por ahora, el «Margen mínimo de publicación»: cuánto antes de
 * su hora tiene que quedar una publicación para poder programarla (nunca se publica al instante).
 *
 * <ul>
 *   <li>Raíz: puerto primario ({@code PublishSettings}).</li>
 *   <li>{@code domain}: el margen y el puerto de salida de los ajustes.</li>
 *   <li>{@code application}: implementación del puerto primario.</li>
 *   <li>{@code adapter.in.web}: la API. {@code adapter.out.persistence}: la tabla {@code app_setting}.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"platform", "platform::live", "platform::postgres", "api"})
@org.jspecify.annotations.NullMarked
package com.socialhub.settings;
