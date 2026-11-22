/**
 * Vista completa del anillo ({@code GET /api/factory}): compone lo que exponen los demás módulos por sus puertos
 * primarios y por las vistas de su interfaz {@code web}, y reúne lo que necesita la atención de Raúl.
 *
 * <ul>
 *   <li>Raíz: puertos primarios ({@code FactoryAttention}, {@code ServicesCheck}).</li>
 *   <li>{@code domain}: avisos y recomendaciones («Qué hacer ahora»).</li>
 *   <li>{@code application}: avisos, recomendaciones y comprobación de servicios.</li>
 *   <li>{@code adapter.in.web}: el controlador, que traduce a los DTO del contrato. {@code adapter.in.scheduling}: el reloj.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"promotion", "promotion::web", "production", "production::web", "publishing", "publishing::web", "settings", "armory", "armory::web", "ai", "ai::domain", "backup", "pieces", "platform", "api"})
@org.jspecify.annotations.NullMarked
package com.socialhub.factory;
