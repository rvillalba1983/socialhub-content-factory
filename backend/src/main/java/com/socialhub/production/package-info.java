/**
 * Producción: estaciones del anillo y tareas, con su máquina de estados.
 *
 * <ul>
 *   <li>{@org.springframework.modulith.ApplicationModule(allowedDependencies = {"platform", "platform::live", "api"})
@code domain}: agregados {@code Task} y {@code Station} y los puertos de salida (repositorios).</li>
 *   <li>Raíz del módulo: casos de uso ({@link com.socialhub.production.ProductionService}) y eventos de dominio
 *       que escuchan otros módulos.</li>
 *   <li>{@code adapter.in.web}: controladores que implementan las interfaces generadas desde OpenAPI.</li>
 *   <li>{@code adapter.out.persistence}: JPA, con auditoría de Hibernate Envers.</li>
 * </ul>
 */
@org.jspecify.annotations.NullMarked
package com.socialhub.production;
