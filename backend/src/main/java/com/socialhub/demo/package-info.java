/**
 * Datos de ejemplo del modo demo y su simulador.
 *
 * <ul>
 *   <li>Raíz: puertos primarios ({@code DemoSeeding}, {@code DemoSimulation}).</li>
 *   <li>{@code domain}: los datos de ejemplo y los puertos de salida (su origen y la base de datos del modo demo).</li>
 *   <li>{@code application}: implementación de los puertos primarios.</li>
 *   <li>{@code adapter.in}: arranque y reloj. {@code adapter.out}: fichero del jar y JDBC.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"production", "platform", "platform::postgres"})
@org.jspecify.annotations.NullMarked
package com.socialhub.demo;
