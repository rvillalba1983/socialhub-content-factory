/**
 * Registro de herramientas locales: qué hay en este ordenador, en qué versión y si funciona. Los resultados se
 * guardan en la armería del modo real. Aporta al motor la tarea {@code CHECK_LOCAL_TOOLS}.
 *
 * <ul>
 *   <li>Raíz: puertos primarios ({@code LocalTools}, {@code Ffmpeg}).</li>
 *   <li>{@code domain}: {@code LocalTool}, {@code VideoProcessor} y el resultado de una comprobación.</li>
 *   <li>{@code application}: implementación de los puertos primarios.</li>
 *   <li>{@code adapter.in}: tarea del motor y comprobación al arrancar. {@code adapter.out}: FFmpeg y Java 2D.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"production", "armory", "platform", "platform::process"})
@org.jspecify.annotations.NullMarked
package com.socialhub.tools;
