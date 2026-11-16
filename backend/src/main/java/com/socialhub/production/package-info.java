/**
 * Producción: estaciones del anillo y tareas, con su máquina de estados.
 *
 * <ul>
 *   <li>Raíz: puertos primarios ({@code ProductionOrders}, {@code ProductionQueries}, {@code ProductionTasks},
 *       {@code ProductionAiTasks}, {@code TaskRounds}) y el puerto que implementan otros módulos
 *       ({@code TaskExecutor}).</li>
 *   <li>{@code domain}: agregados {@code Task} y {@code Station} y los puertos de salida (repositorios). Los eventos
 *       van en {@code domain.events} (interfaz con nombre {@code events}).</li>
 *   <li>{@code application}: implementación de los puertos primarios y motor de tareas.</li>
 *   <li>{@code adapter.in}: web (con la vista {@code ProductionView}, interfaz con nombre {@code web}) y relojes.
 *       {@code adapter.out}: persistencia JPA con auditoría de Hibernate Envers.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"platform", "platform::live", "api"})
@org.jspecify.annotations.NullMarked
package com.socialhub.production;
