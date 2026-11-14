/**
 * Google Drive para ordenador (unidad G:): comprueba que las carpetas de las marcas y promociones están
 * accesibles y lo muestra en la armería. Si faltan, las tareas que las necesitan esperan bloqueadas.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"promotion", "armory", "platform"})
@org.jspecify.annotations.NullMarked
package com.socialhub.drive;
