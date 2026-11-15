/**
 * Copias de seguridad de la base de datos real: una al día al abrir SocialHub, a mano desde la pantalla «Copias»,
 * y restauración (con copia previa y parada de SocialHub después).
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"armory", "platform", "platform::postgres", "api"})
@org.jspecify.annotations.NullMarked
package com.socialhub.backup;
