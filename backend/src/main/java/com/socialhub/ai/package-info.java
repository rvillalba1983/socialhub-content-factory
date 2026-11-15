/**
 * Orquestador de IA. SocialHub no llama a la API de Claude: deja cada encargo
 * ({@org.springframework.modulith.ApplicationModule(allowedDependencies = {"promotion", "production", "platform", "platform::live", "api"})
@code contracts/task-request.v1.schema.json}) en {@code <Encargos de la marca>/para-claude} y recoge el resultado
 * ({@code task-result.v1}) de {@code <Encargos de la marca>/de-claude}. Una tarea programada de Claude hace el trabajo.
 * Controla el límite diario, el modelo de cada tipo de encargo (con subida de nivel en el tercer intento) y registra
 * cada envío. Tokens y coste no se conocen: Claude Pro no los expone.
 */
@org.jspecify.annotations.NullMarked
package com.socialhub.ai;
