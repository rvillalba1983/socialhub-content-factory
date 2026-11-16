/**
 * Marcas, promociones y reglas comunes: alta y edición, carpetas de Drive, identidad y decisiones editoriales.
 *
 * <ul>
 *   <li>Raíz: puertos primarios ({@code PromotionQueries}, {@code PromotionCommands}, {@code PromotionActivation},
 *       {@code PromotionSaveNotes}) y el puerto que implementa el módulo de IA ({@code PromotionSavedHook}).</li>
 *   <li>{@code domain}: marcas, promociones, identidad y puertos de salida (repositorios, carpetas, memoria).</li>
 *   <li>{@code application}: implementación de los puertos primarios.</li>
 *   <li>{@code adapter.in.web}: controladores y la vista {@code PromotionView} (interfaz con nombre {@code web}).
 *       {@code adapter.out}: persistencia y carpetas del disco.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"platform", "platform::live", "platform::postgres", "api"})
@org.jspecify.annotations.NullMarked
package com.socialhub.promotion;
