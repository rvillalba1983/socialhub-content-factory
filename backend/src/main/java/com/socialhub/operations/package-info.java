/**
 * Registro de operaciones externas: guarda cada llamada observada por
 * {@link com.socialhub.platform.observability.ExternalCalls} y la expone en {@code /api/external-operations}.
 *
 * <ul>
 *   <li>Raíz: puertos primarios ({@code ExternalOperationsQueries}, {@code RecordExternalOperations}).</li>
 *   <li>{@code domain}: la operación y el puerto de salida {@code ExternalOperationRepository}.</li>
 *   <li>{@code application}: implementación de los puertos primarios.</li>
 *   <li>{@code adapter.in}: web, observación de llamadas y tareas periódicas. {@code adapter.out}: persistencia.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"platform", "platform::observability", "api"})
@org.jspecify.annotations.NullMarked
package com.socialhub.operations;
